package com.example.sync

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.PhantomApplication
import com.example.db.UserSession
import com.example.crypto.PreKeyStore
import com.example.CryptoUtils
import com.example.network.NetworkConfig
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class PreKeyRotationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "PreKeyRotationWorker"
    private val client = OkHttpClient()

    override suspend fun doWork(): Result {
        Log.i(TAG, "Automatic signed prekey background rotation initiated.")
        val repository = PhantomApplication.instance.repository
        val session = repository.getSession() ?: return Result.failure()

        try {
            val localPrivBytes = Base64.decode(CryptoUtils.decrypt(session.identityPrivateKey), Base64.DEFAULT)
            val preKeyStore = PreKeyStore(repository)
            
            val latest = repository.getLatestSignedPreKey()
            val nextKeyId = (latest?.keyId ?: 0) + 1
            
            val preKeyEntity = preKeyStore.generateAndStoreSignedPreKey(localPrivBytes, nextKeyId)
            
            val bundlePayload = JSONObject().apply {
                put("identityKey", session.identityPublicKey)
                put("signedPreKey", Base64.encodeToString(preKeyEntity.publicKey, Base64.NO_WRAP))
                put("signedPreKeySignature", Base64.encodeToString(preKeyEntity.signature, Base64.NO_WRAP))
                put("signedPreKeyId", preKeyEntity.keyId)
            }
            
            val body = bundlePayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(NetworkConfig.getServerUrl("/api/keys/prekey-bundle"))
                .addHeader("Authorization", "Bearer ${session.sessionToken}")
                .post(body)
                .build()
                
            val success = client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
            
            if (success) {
                val updatedSession = session.copy(signedPreKey = "prekey_${preKeyEntity.keyId}")
                repository.insertSession(updatedSession)
                Log.i(TAG, "Successfully rotated signed prekey to keyId: $nextKeyId")
                return Result.success()
            } else {
                Log.e(TAG, "Server rejected prekey bundle upload.")
                return Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating signed prekeys in background", e)
            return Result.retry()
        }
    }
}
