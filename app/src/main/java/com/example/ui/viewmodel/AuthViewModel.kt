package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.db.PhantomRepository
import com.example.db.UserSession
import com.example.network.NetworkConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import android.util.Base64
import kotlinx.coroutines.delay

class AuthViewModel(private val repository: PhantomRepository) : ViewModel() {
    private val client get() = com.example.network.NetworkConfig.clientState.value
    private val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

    val loginEmail = MutableStateFlow("")
    val loginOtpInput = MutableStateFlow("")
    val isSendingOtp = MutableStateFlow(false)
    val otpStepActive = MutableStateFlow(false)
    val loginErrorMsg = MutableStateFlow<String?>(null)
    val loginSuccessSplash = MutableStateFlow(false)
    val otpTimerSeconds = MutableStateFlow(0)

    val generatedOtpCode = MutableStateFlow("")
    val smtpRelayLogs = mutableListOf<String>()

    fun dispatchEmailOtp(email: String) {
        viewModelScope.launch {
            isSendingOtp.value = true
            loginErrorMsg.value = null
            
            smtpRelayLogs.clear()
            smtpRelayLogs.add("SYS: Connecting to Phantom Relay Server...")

            val jsonPayload = JSONObject()
                .put("email", email)
                .toString()
            val body = jsonPayload.toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(NetworkConfig.getServerUrl("/api/otp/request"))
                .post(body)
                .build()

            var errorDetails: String? = null
            val responseBody = withContext(Dispatchers.IO) {
                try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body?.string()
                        } else {
                            val errBody = response.body?.string()
                            val parsedDetail = try {
                                JSONObject(errBody ?: "").optString("detail")
                            } catch (e: Exception) {
                                null
                            }
                            errorDetails = if (!parsedDetail.isNullOrBlank()) parsedDetail else "Server HTTP code: ${response.code}"
                            null
                        }
                    }
                } catch (e: Exception) {
                    errorDetails = e.message ?: e.toString()
                    null
                }
            }

            isSendingOtp.value = false
            if (responseBody != null) {
                val responseJson = JSONObject(responseBody)
                val isSuccess = responseJson.optString("status") == "success"
                val serverOtp = responseJson.optString("otp").takeIf { it.isNotBlank() && it != "null" }
                val serverError = responseJson.optString("error").takeIf { it.isNotBlank() }

                if (isSuccess) {
                    if (serverOtp != null) {
                        generatedOtpCode.value = serverOtp
                        smtpRelayLogs.add("SUCCESS: Development OTP fallback enabled by server.")
                        loginErrorMsg.value = "Development Code: $serverOtp"
                    } else {
                        smtpRelayLogs.add("SUCCESS: OTP successfully routed via SMTP server.")
                        loginErrorMsg.value = "Verification token dispatched to $email!"
                    }
                    otpStepActive.value = true
                } else {
                    smtpRelayLogs.add("ERROR: Server rejected request: $serverError")
                    loginErrorMsg.value = "Dispatch failed: $serverError"
                }
            } else {
                val detailSuffix = if (errorDetails != null) " ($errorDetails)" else ""
                smtpRelayLogs.add("ERROR: Connection failed to ${NetworkConfig.getServerUrl("")}$detailSuffix")
                loginErrorMsg.value = "Could not connect to server. Check server status!"
            }
            otpTimerSeconds.value = 60
            startOtpTimer()
        }
    }

    private fun startOtpTimer() {
        viewModelScope.launch {
            while (otpTimerSeconds.value > 0) {
                delay(1000)
                otpTimerSeconds.value -= 1
            }
        }
    }

    fun verifyOtpCode(inputCode: String) {
        viewModelScope.launch {
            val jsonPayload = JSONObject()
                .put("email", loginEmail.value)
                .put("code", inputCode)
                .toString()
            val body = jsonPayload.toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(NetworkConfig.getServerUrl("/api/otp/verify"))
                .post(body)
                .build()

            val responseBody = withContext(Dispatchers.IO) {
                try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body?.string()
                        } else null
                    }
                } catch (e: Exception) {
                    null
                }
            }

            if (responseBody != null) {
                val json = JSONObject(responseBody)
                val token = json.optString("sessionToken").takeIf { it.isNotBlank() && it != "null" }
                if (token != null) {
                    loginSuccessSplash.value = true
                    delay(800)
                    
                    val dbKeyHex = generateSecureTokenHex(32)
                    val identityKeyPair = com.example.crypto.X3DHProtocol.generateIdentityKeyPair()
                    val identityPublicKeyHex = Base64.encodeToString(identityKeyPair.publicKey, Base64.NO_WRAP)
                    val identityPrivateKeyHex = Base64.encodeToString(identityKeyPair.privateKey, Base64.NO_WRAP)
                    val signedPreKeyHex = "prekey_" + List(8) { "0123456789abcdef".random() }.joinToString("")
                    val deviceIdStr = "dev_" + kotlin.random.Random.nextInt(10000, 99999)
                    val tokenFCMStr = "fcm_token_" + List(8) { "0123456789abcdef".random() }.joinToString("")

                    repository.insertSession(
                        UserSession(
                            id = 1,
                            isLoggedIn = true,
                            email = loginEmail.value.trim().lowercase(),
                            deviceId = deviceIdStr,
                            tokenFCM = tokenFCMStr,
                            identityPublicKey = identityPublicKeyHex,
                            identityPrivateKey = com.example.CryptoUtils.encrypt(identityPrivateKeyHex),
                            signedPreKey = signedPreKeyHex,
                            databaseKeyHex = dbKeyHex,
                            sessionToken = token
                        )
                    )
                    
                    val privBytes = identityKeyPair.privateKey
                    val preKeyStore = com.example.crypto.PreKeyStore(repository)
                    preKeyStore.generateAndStoreSignedPreKey(privBytes, 1)
                    preKeyStore.generateAndStoreOneTimePreKeys(1, 100)

                    PhantomViewModel.isLoggedInGlobal.value = true
                } else {
                    loginErrorMsg.value = "Authentication token parsing error."
                }
            } else {
                loginErrorMsg.value = "Incorrect OTP code. Access denied."
            }
        }
    }

    private fun generateSecureTokenHex(size: Int): String {
        val bytes = ByteArray(size)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
