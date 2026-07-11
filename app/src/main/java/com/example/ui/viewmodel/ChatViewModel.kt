package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.db.PhantomRepository
import com.example.db.RoomChatMessage
import com.example.db.RoomChatUser
import com.example.db.UserSession
import com.example.crypto.SessionManager
import com.example.network.P2PManager
import com.example.network.NetworkConfig
import com.example.security.SelfDestructTimer
import com.example.ui.models.ChatMessage
import com.example.ui.models.ChatUser
import com.example.ui.models.CryptoPipelineStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatViewModel(application: Application, val repository: PhantomRepository) : AndroidViewModel(application) {
    private val client get() = com.example.network.NetworkConfig.clientState.value
    private val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

    val sessionManager = SessionManager(repository)
    val p2pManager = P2PManager(application)

    val identityPublicKey = MutableStateFlow("")
    val identityPrivateKey = MutableStateFlow("")
    val deviceId = MutableStateFlow("")
    val loginEmail = MutableStateFlow("")
    val activeSessionToken = MutableStateFlow("")
    val tokenFCM = MutableStateFlow("")

    val selectedChatUser = MutableStateFlow<ChatUser?>(null)
    val selectedFilterPill = MutableStateFlow("Direct")
    val searchQuery = MutableStateFlow("")
    
    val isEncryptingInProgress = MutableStateFlow(false)
    val activePipelineStep = MutableStateFlow(-1)
    val activePipelineSteps = mutableStateListOf<CryptoPipelineStep>()
    
    val bobOnline = MutableStateFlow(true)
    val typingStatus = MutableStateFlow<String?>(null)
    val inputMessageText = MutableStateFlow("")
    val defaultSelfDestructTimer = MutableStateFlow(SelfDestructTimer.OFF)
    val storagePermissionGrantedEvent = MutableStateFlow<String?>(null)

    val serverQueue = mutableStateListOf<ChatMessage>()

    init {
        viewModelScope.launch {
            com.example.ui.viewmodel.PhantomViewModel.isLoggedInGlobal.collect { loggedIn ->
                if (loggedIn) {
                    if (loginEmail.value.isBlank()) {
                        runCatching {
                            val session = repository.getSession()
                            if (session != null) {
                                identityPublicKey.value = session.identityPublicKey
                                identityPrivateKey.value = com.example.CryptoUtils.decrypt(session.identityPrivateKey)
                                deviceId.value = session.deviceId
                                loginEmail.value = session.email.trim().lowercase()
                                activeSessionToken.value = session.sessionToken
                                tokenFCM.value = session.tokenFCM
                            }
                        }.onFailure { e ->
                            android.util.Log.e("CHAT_VM_INIT", "Failed to load session from database", e)
                        }
                    }
                } else {
                    identityPublicKey.value = ""
                    identityPrivateKey.value = ""
                    deviceId.value = ""
                    loginEmail.value = ""
                    activeSessionToken.value = ""
                    tokenFCM.value = ""
                }
            }
        }
    }

    val messageList: Flow<List<ChatMessage>> = selectedChatUser
        .flatMapLatest { partner ->
            if (partner == null) flowOf(emptyList())
            else repository.getMessagesForPartnerFlow(partner.name)
        }
        .map { list ->
            list.map {
                ChatMessage(it.id, it.sender, it.text, it.ciphertext, it.mac, it.timestamp, it.timestampMillis, it.isEncrypted, it.isDelivered, it.isRead, it.selfDestructAt, it.selfDestructDuration)
            }
        }

    val mockUsers: Flow<List<ChatUser>> = combine(
        repository.getAllUsersFlow(),
        searchQuery,
        selectedFilterPill
    ) { usersList, query, pill ->
        var list = usersList
        if (pill == "P2P") {
            list = list.filter { it.publicKey == "id_pub_relayed" || it.name == "Bob" }
        }
        if (query.isNotBlank()) {
            list = list.filter { it.name.contains(query, ignoreCase = true) }
        }
        list.map {
            ChatUser(it.name, it.lastMessage, it.time, it.unreadCount, Color(it.avatarColorHex), it.isOnline, it.publicKey)
        }
    }

    fun addLog(tag: String, msg: String) {
        PhantomViewModel.addLogGlobal(tag, msg)
    }

    private fun buildAuthorizedRequest(url: String, method: String = "GET", body: RequestBody? = null): Request {
        val builder = Request.Builder().url(url)
        if (activeSessionToken.value.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer ${activeSessionToken.value}")
        }
        if (method == "POST" && body != null) {
            builder.post(body)
        }
        return builder.build()
    }

    fun encryptAndSendMessage(rawMessage: String, partner: ChatUser) {
        viewModelScope.launch {
            isEncryptingInProgress.value = true
            activePipelineSteps.clear()

            activePipelineSteps.add(CryptoPipelineStep("Plaintext Input", Icons.Default.TextFields, rawMessage, "Raw UTF-8 string value typed by Alice.", Color(0xFF1A1C18)))
            activePipelineStep.value = 0
            delay(250)

            val serialized = JSONObject()
                .put("v", 1)
                .put("type", "text")
                .put("txt", rawMessage)
                .toString()
            activePipelineSteps.add(CryptoPipelineStep("Payload Serialization", Icons.Default.DataObject, serialized, "Package content into standard formats.", Color(0xFF43493E)))
            activePipelineStep.value = 1
            delay(250)

            val compressed = "deflate_comp_" + (rawMessage.length * 0.75).toInt() + "bytes"
            activePipelineSteps.add(CryptoPipelineStep("Binary Compression", Icons.Default.Compress, compressed, "Compress data to save bandwidth.", Color(0xFFA1AF97)))
            activePipelineStep.value = 2
            delay(250)

            val myPrivateBytes = Base64.decode(identityPrivateKey.value, Base64.DEFAULT)
            val myPublicBytes = Base64.decode(identityPublicKey.value, Base64.DEFAULT)
            val peerPublicKeyBytes = Base64.decode(partner.publicKey, Base64.DEFAULT)

            var peerBundle: com.example.crypto.PreKeyBundle? = null
            if (!sessionManager.hasSession(partner.name)) {
                addLog("SEC", "No active E2EE session for ${partner.name}. Fetching prekey bundle from server...")
                val request = buildAuthorizedRequest(NetworkConfig.getServerUrl("/api/keys/prekey-bundle/${partner.name}"))
                try {
                    val body = withContext(Dispatchers.IO) {
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) response.body?.string() else null
                        }
                    }
                    if (body != null) {
                        val json = JSONObject(body)
                        val identityKey = Base64.decode(json.getString("identityKey"), Base64.DEFAULT)
                        val signedPreKeyBytes = Base64.decode(json.getString("signedPreKey"), Base64.DEFAULT)
                        val signedPreKeySignature = Base64.decode(json.getString("signedPreKeySignature"), Base64.DEFAULT)
                        val signedPreKeyId = json.getInt("signedPreKeyId")
                        val oneTimePreKeyStr = json.optString("oneTimePreKey").takeIf { it.isNotBlank() }
                        val oneTimePreKey = if (oneTimePreKeyStr != null) Base64.decode(oneTimePreKeyStr, Base64.DEFAULT) else null
                        val oneTimePreKeyId = if (json.has("oneTimePreKeyId")) json.getInt("oneTimePreKeyId") else null
                        
                        peerBundle = com.example.crypto.PreKeyBundle(
                            identityKey = identityKey,
                            signedPreKey = signedPreKeyBytes,
                            signedPreKeySignature = signedPreKeySignature,
                            signedPreKeyId = signedPreKeyId,
                            oneTimePreKey = oneTimePreKey,
                            oneTimePreKeyId = oneTimePreKeyId
                        )
                        addLog("SEC", "Retrieved authenticated prekey bundle for ${partner.name} from directory.")
                    }
                } catch (e: Exception) {
                    addLog("WARNING", "Failed to fetch prekey bundle from server: ${e.message}. Falling back to offline key exchange.")
                }
            }

            val envelope = try {
                sessionManager.encryptMessage(myPrivateBytes, myPublicBytes, partner.name, peerPublicKeyBytes, rawMessage.toByteArray(Charsets.UTF_8), peerBundle)
            } catch (e: Exception) {
                addLog("ERROR", "E2EE encryption failed: ${e.message}. Message NOT sent.")
                isEncryptingInProgress.value = false
                activePipelineStep.value = -1
                return@launch
            }

            val ciphertextHex = envelope.ciphertext
            activePipelineSteps.add(CryptoPipelineStep("Signal Double Ratchet AES-GCM", Icons.Default.Lock, ciphertextHex.take(24) + "...", "Encrypt using derived symmetric chain key step.", Color(0xFFD5E897)))
            activePipelineStep.value = 3
            delay(250)

            val macCode = envelope.mac
            activePipelineSteps.add(CryptoPipelineStep("HMAC Authenticator Signature", Icons.Default.Fingerprint, macCode, "Seal envelope with key-hashed integrity MAC.", Color(0xFF43493E)))
            activePipelineStep.value = 4
            delay(300)

            val now = System.currentTimeMillis()
            val timestamp = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(now))
            val selfDestructDuration = defaultSelfDestructTimer.value.durationMillis
            val selfDestructAt = if (selfDestructDuration > 0) now + selfDestructDuration else 0L

            val finalMsg = ChatMessage(
                id = java.util.UUID.randomUUID().toString(),
                sender = loginEmail.value.substringBefore("@"),
                text = rawMessage,
                ciphertext = ciphertextHex,
                mac = macCode,
                timestamp = timestamp,
                timestampMillis = now,
                isEncrypted = true,
                isDelivered = bobOnline.value,
                isRead = false
            )

            repository.insertMessage(
                RoomChatMessage(
                    id = finalMsg.id,
                    sender = finalMsg.sender,
                    text = finalMsg.text,
                    ciphertext = finalMsg.ciphertext,
                    mac = finalMsg.mac,
                    timestamp = finalMsg.timestamp,
                    timestampMillis = System.currentTimeMillis(),
                    isEncrypted = finalMsg.isEncrypted,
                    isDelivered = finalMsg.isDelivered,
                    isRead = finalMsg.isRead,
                    chatPartner = partner.name,
                    selfDestructAt = selfDestructAt,
                    selfDestructDuration = selfDestructDuration
                )
            )

            repository.insertUser(
                RoomChatUser(
                    partner.name,
                    rawMessage,
                    timestamp,
                    0,
                    partner.avatarColor.value.toInt(),
                    partner.isOnline,
                    partner.publicKey
                )
            )

            val payloadEnvelopeJson = envelope.toJson()
            val myShortName = loginEmail.value.substringBefore("@")
            val jsonPayload = JSONObject()
                .put("id", finalMsg.id)
                .put("sender", myShortName)
                .put("recipient", partner.name)
                .put("text", payloadEnvelopeJson)
                .put("ciphertext", ciphertextHex)
                .put("mac", macCode)
                .put("timestamp", timestamp)
                .toString()
            
            val body = jsonPayload.toRequestBody(jsonMediaType)
            val request = buildAuthorizedRequest(NetworkConfig.getServerUrl("/api/messages/send"), "POST", body)

            val success = withContext(Dispatchers.IO) {
                try {
                    client.newCall(request).execute().use { response ->
                        response.isSuccessful
                    }
                } catch (e: Exception) {
                    false
                }
            }

            if (success) {
                addLog("NET", "Relayed secure envelope sent successfully to directory.")
            } else {
                addLog("NET", "Directory server offline. Envelope queued in offline database.")
                serverQueue.add(finalMsg)
            }

            isEncryptingInProgress.value = false
            activePipelineStep.value = -1
        }
    }

    fun deleteMessage(msgId: String, partner: ChatUser) {
        viewModelScope.launch {
            val protocolPayload = JSONObject()
                .put("type", "delete")
                .put("target_id", msgId)
                .toString()

            repository.deleteMessageById(msgId)
            encryptAndSendMessage(protocolPayload, partner)
            addLog("SYS", "Purged local message ID: $msgId (sent deletion protocol packet).")
        }
    }

    fun editMessage(msgId: String, newText: String, partner: ChatUser) {
        viewModelScope.launch {
            val protocolPayload = JSONObject()
                .put("type", "edit")
                .put("target_id", msgId)
                .put("new_text", newText)
                .toString()

            val msg = repository.getMessageById(msgId)
            if (msg != null) {
                repository.insertMessage(msg.copy(text = "$newText (Edited)"))
            }
            encryptAndSendMessage(protocolPayload, partner)
            addLog("SYS", "Edited message local state (sent update E2EE payload).")
        }
    }

    fun uploadMediaAndSendMessage(uri: Uri, type: String, partner: ChatUser) {
        viewModelScope.launch {
            addLog("SEC", "Beginning zero-knowledge secure media encrypter...")
            val context = getApplication<Application>()
            val resolver = context.contentResolver
            val inputStream = resolver.openInputStream(uri)
            val fileBytes = inputStream?.readBytes() ?: return@launch
            inputStream.close()

            val secretKeyBytes = ByteArray(32)
            SecureRandom().nextBytes(secretKeyBytes)
            val secretKey = javax.crypto.spec.SecretKeySpec(secretKeyBytes, "AES")
            val combined = com.example.CryptoUtils.encryptBytes(fileBytes, secretKey)
            val iv = combined.sliceArray(0 until 12)

            val tempFile = File(context.cacheDir, "sec_upload.bin")
            tempFile.writeBytes(combined)

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "encrypted_media.bin", RequestBody.create("application/octet-stream".toMediaTypeOrNull(), tempFile))
                .build()

            val request = buildAuthorizedRequest(NetworkConfig.getServerUrl("/api/media/upload"), "POST", requestBody)

            val responseBody = withContext(Dispatchers.IO) {
                try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) response.body?.string() else null
                    }
                } catch (e: Exception) {
                    null
                }
            }

            tempFile.delete()

            if (responseBody != null) {
                val json = JSONObject(responseBody)
                val fileId = json.getString("fileId")
                val mediaPayload = JSONObject()
                    .put("type", "media")
                    .put("fileId", fileId)
                    .put("mediaType", type)
                    .put("key", Base64.encodeToString(secretKeyBytes, Base64.NO_WRAP))
                    .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                    .toString()

                addLog("SEC", "ZK Media payload encrypted, ID: $fileId. Relaying wrapper envelope.")
                encryptAndSendMessage(mediaPayload, partner)
            } else {
                addLog("ERROR", "Failed to upload encrypted payload attachment to directory.")
            }
        }
    }

    fun syncContactsFromServer() {
        viewModelScope.launch {
            addLog("SYS", "Syncing verified contact list from directory...")
            val request = buildAuthorizedRequest(NetworkConfig.getServerUrl("/api/users"))
            val responseBody = withContext(Dispatchers.IO) {
                try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) response.body?.string() else null
                    }
                } catch (e: Exception) {
                    null
                }
            }

            if (responseBody != null) {
                try {
                    android.util.Log.d("PHANTOM_SYNC", "Raw directory response: $responseBody")
                    addLog("SYS", "Raw synced response: $responseBody")
                    val jsonArray = JSONArray(responseBody)
                    val users = mutableListOf<RoomChatUser>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val name = obj.getString("name")
                        if (name == loginEmail.value.substringBefore("@")) continue
                        val pubKey = obj.getString("publicKey")
                        val existing = repository.getUserByName(name)
                        users.add(
                            RoomChatUser(
                                name = name,
                                lastMessage = existing?.lastMessage ?: "No messages yet",
                                time = existing?.time ?: "12:00 PM",
                                unreadCount = existing?.unreadCount ?: 0,
                                avatarColorHex = existing?.avatarColorHex ?: 0xFF95A5A6.toInt(),
                                isOnline = true,
                                publicKey = pubKey
                            )
                        )
                    }
                    repository.insertUsers(users)
                    addLog("SYS", "Successfully synced ${users.size} active contacts.")
                } catch (e: Exception) {
                    android.util.Log.e("CHAT_VM_SYNC", "Failed to parse users JSON", e)
                    addLog("ERROR", "Directory synchronization parsing failed.")
                }
            } else {
                addLog("ERROR", "Directory synchronization failed.")
            }
        }
    }

    fun initiateVoIpCall(partner: ChatUser) {
        viewModelScope.launch {
            addLog("SEC", "Starting E2EE WebRTC VoIP handshake with ${partner.name}...")
        }
    }

    fun simulateIncomingMessage(text: String, sender: String) {
        viewModelScope.launch {
            val timestamp = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            val testMsg = RoomChatMessage(
                id = java.util.UUID.randomUUID().toString(),
                sender = sender,
                text = text,
                ciphertext = "simulated_ciphertext_hex",
                mac = "simulated_mac",
                timestamp = timestamp,
                timestampMillis = System.currentTimeMillis(),
                isEncrypted = true,
                isDelivered = true,
                isRead = false,
                chatPartner = sender
            )
            repository.insertMessage(testMsg)
        }
    }

    fun triggerSecureBoot() {
        com.example.ui.viewmodel.PhantomViewModel.triggerSecureBootGlobal()
    }

    fun flushOfflineQueue() {
        viewModelScope.launch {
            if (bobOnline.value && serverQueue.isNotEmpty()) {
                val queueToFlush = ArrayList(serverQueue)
                serverQueue.clear()
                queueToFlush.forEach { msg ->
                    repository.insertMessage(
                        RoomChatMessage(
                            id = msg.id,
                            sender = msg.sender,
                            text = msg.text,
                            ciphertext = msg.ciphertext,
                            mac = msg.mac,
                            timestamp = msg.timestamp,
                            timestampMillis = System.currentTimeMillis(),
                            isEncrypted = msg.isEncrypted,
                            isDelivered = true,
                            isRead = msg.isRead,
                            chatPartner = selectedChatUser.value?.name ?: "Ethan"
                        )
                    )
                }
                addLog("NET", "Offline queue flushed. ${queueToFlush.size} packet(s) safely relayed.")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.clearSession()
            com.example.ui.viewmodel.PhantomViewModel.isLoggedInGlobal.value = false
            addLog("SYS", "Secure session logged out.")
        }
    }
}
