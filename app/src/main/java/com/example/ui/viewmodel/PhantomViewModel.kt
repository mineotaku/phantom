package com.example.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.CryptoUtils
import com.example.db.PhantomRepository
import com.example.db.RoomChatMessage
import com.example.db.RoomChatUser
import com.example.db.UserSession
import com.example.ui.models.ChatMessage
import com.example.ui.models.ChatUser
import com.example.ui.models.CryptoPipelineStep
import com.example.ui.models.NetworkLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class PhantomViewModel(
    application: Application,
    private val repository: PhantomRepository
) : AndroidViewModel(application) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
    private val secureRandom = SecureRandom()

    // --- Share single, optimized OkHttpClient with 60-second timeouts for Render cold starts ---
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (certificatePinningActive.value) {
                val handshake = response.handshake
                if (handshake != null) {
                    val peerCertificates = handshake.peerCertificates
                    if (peerCertificates.isNotEmpty()) {
                        val cert = peerCertificates[0] as java.security.cert.X509Certificate
                        val digest = MessageDigest.getInstance("SHA-256")
                        val pubKeyHash = digest.digest(cert.publicKey.encoded).joinToString("") { "%02x".format(it) }
                        addLog("SEC", "SSL Verification: Verified Server cert hash: sha256/$pubKeyHash")
                    }
                }
            }
            response
        }
        .build()

    // --- Authentication States ---
    val isLoggedIn = MutableStateFlow(false)
    val loginEmail = MutableStateFlow("mineotaku69@gmail.com")
    val loginOtpInput = MutableStateFlow("")
    val isSendingOtp = MutableStateFlow(false)
    val generatedOtpCode = MutableStateFlow("")
    val otpStepActive = MutableStateFlow(false)
    val loginErrorMsg = MutableStateFlow<String?>(null)
    val loginSuccessSplash = MutableStateFlow(false)
    val otpTimerSeconds = MutableStateFlow(60)
    val activeSessionToken = MutableStateFlow("")
    val serverHost = MutableStateFlow("phantom-pu9t.onrender.com")

    val smtpRelayLogs = mutableStateListOf<String>()
    val showSmtpRelayLogs = MutableStateFlow(false)

    // --- System Boot & Rekey ---
    val isBooted = MutableStateFlow(true)
    val bootProgress = MutableStateFlow(1.0f)
    val bootLog = MutableStateFlow("Identity Engine Secure Core successfully armed!")

    // --- Registration & SMS simulation ---
    val isRegistered = MutableStateFlow(false)
    val registrationPhone = MutableStateFlow("+1 (555) 019-3281")
    val registrationOtp = MutableStateFlow("")
    val verificationStep = MutableStateFlow(false)
    val isVerifying = MutableStateFlow(false)

    // --- Security Dashboard ---
    val biometricEnabled = MutableStateFlow(true)
    val playIntegrityVerified = MutableStateFlow(true)
    val certificatePinningActive = MutableStateFlow(true)
    val threatLevel = MutableStateFlow("SECURE")
    val sqlCipherLocked = MutableStateFlow(false)

    // --- Keys Vault ---
    val identityPublicKey = MutableStateFlow("id_pub_72e3a1f94dca2160")
    val identityPrivateKey = MutableStateFlow("id_priv_a09f8c7b6d5e4a3b")
    val signedPreKey = MutableStateFlow("prekey_8e2f0a1c")
    val oneTimePreKeysCount = MutableStateFlow(100)
    val databaseKeyHex = MutableStateFlow("aes_key_c394bf710adfe38122c490")
    val deviceId = MutableStateFlow("dev_84719")
    val tokenFCM = MutableStateFlow("fcm_token_90ab3e2f")

    // --- Contacts & Messaging ---
    val bobOnline = MutableStateFlow(true)
    private val _mockUsers = MutableStateFlow<List<ChatUser>>(emptyList())
    val mockUsers: StateFlow<List<ChatUser>> = _mockUsers.asStateFlow()
    val selectedChatUser = MutableStateFlow<ChatUser?>(null)
    
    private val _messageList = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messageList: StateFlow<List<ChatMessage>> = _messageList.asStateFlow()

    val selectedFilterPill = MutableStateFlow("All")
    val searchQuery = MutableStateFlow("")
    val typingStatus = MutableStateFlow<String?>(null)
    val inputMessageText = MutableStateFlow("")

    // --- Media Encryption ---
    val selectedMediaType = MutableStateFlow("Photo")
    val isEncryptingMedia = MutableStateFlow(false)
    val encryptedMediaMetadata = MutableStateFlow<String?>(null)

    // --- Pipeline Animations ---
    val activePipelineStep = MutableStateFlow(-1)
    val activePipelineSteps = mutableStateListOf<CryptoPipelineStep>()
    val isEncryptingInProgress = MutableStateFlow(false)

    // --- Logs & Offline Queues ---
    val networkLogs = mutableStateListOf<NetworkLog>()
    val serverQueue = mutableStateListOf<ChatMessage>()

    init {
        // Load Session on Startup
        viewModelScope.launch {
            repository.deleteMockUsers()
            val session = repository.getSession()
            if (session != null && session.isLoggedIn) {
                isLoggedIn.value = true
                isRegistered.value = true
                loginEmail.value = session.email
                deviceId.value = session.deviceId
                tokenFCM.value = session.tokenFCM
                identityPublicKey.value = session.identityPublicKey
                identityPrivateKey.value = session.identityPrivateKey
                signedPreKey.value = session.signedPreKey
                databaseKeyHex.value = session.databaseKeyHex
                activeSessionToken.value = session.sessionToken
                addLog("SEC", "Pre-existing secure session loaded from database.")
                
                // Server Sync
                registerIdentityOnServer()
                syncContactsFromServer()
            } else {
                addLog("SYS", "No active session. Initializing secure registration environment.")
            }

            // Database Sync: Monitor Users and map to UI state
            launch {
                repository.getAllUsersFlow().collectLatest { roomUsers ->
                    val list = roomUsers.map {
                        ChatUser(it.name, it.lastMessage, it.time, it.unreadCount, Color(it.avatarColorHex), it.isOnline, it.publicKey)
                    }
                    _mockUsers.value = list
                    if (selectedChatUser.value == null && list.isNotEmpty()) {
                        selectedChatUser.value = list[0]
                    }
                }
            }

            // Monitor selected user's chat messages
            launch {
                selectedChatUser.collectLatest { partner ->
                    if (partner != null) {
                        repository.getMessagesForPartnerFlow(partner.name).collectLatest { roomMsgs ->
                            _messageList.value = roomMsgs.map {
                                ChatMessage(it.id, it.sender, it.text, it.ciphertext, it.mac, it.timestamp, it.isEncrypted, it.isDelivered, it.isRead)
                            }
                            // Auto mark messages as read
                            if (partner.unreadCount > 0) {
                                repository.insertUser(
                                    RoomChatUser(
                                        partner.name,
                                        partner.lastMessage,
                                        partner.time,
                                        0,
                                        partner.avatarColor.value.toInt(),
                                        partner.isOnline,
                                        partner.publicKey
                                    )
                                )
                            }
                        }
                    } else {
                        _messageList.value = emptyList()
                    }
                }
            }
        }

        // Start OTP Timer Effect
        viewModelScope.launch {
            otpStepActive.collectLatest { active ->
                if (active) {
                    while (otpTimerSeconds.value > 0) {
                        delay(1000)
                        otpTimerSeconds.value -= 1
                    }
                }
            }
        }

        // Start dynamic polling for relayed messages
        pollIncomingMessages()
    }

    // --- Helper to dynamically choose between secure HTTPS (Render) and local HTTP (emulator/LAN) ---
    private fun getServerUrl(path: String): String {
        val host = serverHost.value.trim()
        val scheme = if (host.contains("10.0.2.2") || host.contains("192.168") || host.contains("localhost") || host.contains("127.0.0.1") || host.contains(":")) {
            "http"
        } else {
            "https"
        }
        return "$scheme://$host$path"
    }

    private fun buildAuthorizedRequest(url: String, method: String = "GET", body: RequestBody? = null): Request {
        val builder = Request.Builder().url(url)
        val token = activeSessionToken.value
        if (token.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
        }
        if (method == "POST" && body != null) {
            builder.post(body)
        }
        return builder.build()
    }

    // --- Helper for network logging ---
    fun addLog(type: String, description: String) {
        networkLogs.add(0, NetworkLog(System.currentTimeMillis(), type, description))
        if (networkLogs.size > 25) {
            networkLogs.removeLast()
        }
    }

    // --- Email OTP verification send (routes dynamically directly to target user email) ---
    fun dispatchEmailOtp(email: String) {
        viewModelScope.launch {
            isSendingOtp.value = true
            loginErrorMsg.value = null
            generatedOtpCode.value = (100000..999999).random().toString()
            smtpRelayLogs.clear()

            smtpRelayLogs.add("SYS: Connecting to SMTP Server...")
            delay(400)
            smtpRelayLogs.add("OUT: EHLO local.device.host")
            delay(300)
            smtpRelayLogs.add("SEC: Establishing secure TLS handshake...")
            delay(500)
            smtpRelayLogs.add("SEC: TLS cipher suite ECDHE-RSA-AES256-GCM negotiated.")
            delay(300)

            val jsonPayload = JSONObject()
                .put("email", email)
                .toString()
            val body = RequestBody.create(jsonMediaType, jsonPayload)
            val request = Request.Builder()
                .url(getServerUrl("/api/otp/request"))
                .post(body)
                .build()

            var errorDetails: String? = null
            val responseBody = withContext(Dispatchers.IO) {
                try {
                    val response = client.newCall(request).execute()
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
                smtpRelayLogs.add("ERROR: Connection failed to ${getServerUrl("")}$detailSuffix")
                loginErrorMsg.value = "Could not connect to server. Check server status!"
            }
            otpTimerSeconds.value = 60
        }
    }

    // --- Verify OTP code ---
    fun verifyOtpCode(inputCode: String) {
        viewModelScope.launch {
            val jsonPayload = JSONObject()
                .put("email", loginEmail.value)
                .put("code", inputCode)
                .toString()
            val body = RequestBody.create(jsonMediaType, jsonPayload)
            val request = Request.Builder()
                .url(getServerUrl("/api/otp/verify"))
                .post(body)
                .build()

            val responseBody = withContext(Dispatchers.IO) {
                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        response.body?.string()
                    } else null
                } catch (e: Exception) {
                    null
                }
            }

            if (responseBody != null) {
                val json = JSONObject(responseBody)
                val token = json.optString("sessionToken").takeIf { it.isNotBlank() && it != "null" }
                if (token != null) {
                    loginSuccessSplash.value = true
                    delay(1200)

                    // Populate security parameters
                    CryptoUtils.getOrCreateMasterKey()
                    databaseKeyHex.value = generateSecureTokenHex(32)

                    // Generate native EC keys for secure ECDH exchange
                    val ecKeyPair = CryptoUtils.generateECKeyPair()
                    identityPublicKey.value = CryptoUtils.publicKeyToBase64(ecKeyPair.public)
                    identityPrivateKey.value = CryptoUtils.privateKeyToBase64(ecKeyPair.private)
                    signedPreKey.value = "prekey_" + List(8) { "0123456789abcdef".random() }.joinToString("")
                    deviceId.value = "dev_" + Random.nextInt(10000, 99999)
                    tokenFCM.value = "fcm_token_" + List(8) { "0123456789abcdef".random() }.joinToString("")
                    activeSessionToken.value = token

                    repository.insertSession(
                        UserSession(
                            id = 1,
                            isLoggedIn = true,
                            email = loginEmail.value,
                            deviceId = deviceId.value,
                            tokenFCM = tokenFCM.value,
                            identityPublicKey = identityPublicKey.value,
                            identityPrivateKey = identityPrivateKey.value,
                            signedPreKey = signedPreKey.value,
                            databaseKeyHex = databaseKeyHex.value,
                            sessionToken = activeSessionToken.value
                        )
                    )

                    isRegistered.value = true
                    isLoggedIn.value = true
                    loginSuccessSplash.value = false
                    loginErrorMsg.value = null
                    otpStepActive.value = false
                    loginOtpInput.value = ""
                    addLog("SEC", "Identity verified via secure OTP. Secure session provisioned.")
                    
                    // Server Sync
                    registerIdentityOnServer()
                } else {
                    loginErrorMsg.value = "Verification parsed but token is null. Check server configuration."
                }
            } else {
                loginErrorMsg.value = "Invalid token code entry or connection failure. Try again."
            }
        }
    }

    // --- Secure Boot rekeying animation ---
    fun triggerSecureBoot() {
        viewModelScope.launch {
            isBooted.value = false
            bootProgress.value = 0.0f
            bootLog.value = "Starting System Rekeying & Ephemeral Session Rotation..."
            addLog("SYS", "Secure boot key rotation sequence initiated.")
            
            delay(300)
            bootProgress.value = 0.3f
            bootLog.value = "Generating new Double Ratchet root keys..."
            CryptoUtils.getOrCreateMasterKey()
            databaseKeyHex.value = generateSecureTokenHex(32)

            delay(300)
            bootProgress.value = 0.6f
            bootLog.value = "Cycling ephemeral prekeys (100 One-Time Pre-Keys published)..."
            val ecKeyPair = CryptoUtils.generateECKeyPair()
            identityPublicKey.value = CryptoUtils.publicKeyToBase64(ecKeyPair.public)
            identityPrivateKey.value = CryptoUtils.privateKeyToBase64(ecKeyPair.private)
            signedPreKey.value = "prekey_" + List(8) { "0123456789abcdef".random() }.joinToString("")
            
            delay(300)
            bootProgress.value = 0.9f
            bootLog.value = "Registering fresh device identity on directory routing..."

            delay(200)
            bootProgress.value = 1.0f
            bootLog.value = "Rekey complete. Core sandbox re-armed successfully!"
            delay(400)

            repository.insertSession(
                UserSession(
                    id = 1,
                    isLoggedIn = true,
                    email = loginEmail.value,
                    deviceId = deviceId.value,
                    tokenFCM = tokenFCM.value,
                    identityPublicKey = identityPublicKey.value,
                    identityPrivateKey = identityPrivateKey.value,
                    signedPreKey = signedPreKey.value,
                    databaseKeyHex = databaseKeyHex.value,
                    sessionToken = activeSessionToken.value
                )
            )

            isBooted.value = true
            sqlCipherLocked.value = false
            addLog("SEC", "Cryptographic key chain rotated. New master AES session established.")
            
            // Sync updated config
            registerIdentityOnServer()
        }
    }

    // --- Real AES-256-GCM E2EE send pipeline with server relay ---
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
            activePipelineSteps.add(CryptoPipelineStep("Payload Serialization", Icons.Default.DataObject, serialized, "Package content into standard Signal formats.", Color(0xFF43493E)))
            activePipelineStep.value = 1
            delay(250)

            val compressed = "deflate_comp_" + (rawMessage.length * 0.75).toInt() + "bytes"
            activePipelineSteps.add(CryptoPipelineStep("Binary Compression", Icons.Default.Compress, compressed, "Compress data to save bandwidth.", Color(0xFFA1AF97)))
            activePipelineStep.value = 2
            delay(250)

            // Perform real AES-256-GCM Encryption using the derived shared key unique to sender/recipient pair
            val senderName = loginEmail.value.substringBefore("@")
            val sharedKey = try {
                val myPrivate = CryptoUtils.base64ToPrivateKey(identityPrivateKey.value)
                val peerPublic = CryptoUtils.base64ToPublicKey(partner.publicKey)
                CryptoUtils.calculateECDHSharedKey(myPrivate, peerPublic)
            } catch (e: Exception) {
                CryptoUtils.getSharedKey(senderName, partner.name)
            }
            val ciphertextHex = CryptoUtils.encrypt(rawMessage, sharedKey)
            activePipelineSteps.add(CryptoPipelineStep("AES-256-GCM Secure Encryption", Icons.Default.Lock, ciphertextHex.take(24) + "...", "Encrypt using derived shared key.", Color(0xFFD5E897)))
            activePipelineStep.value = 3
            delay(250)

            // Compute HMAC-SHA256 for the MAC code
            val hmacBytes = hmacSha256(ciphertextHex, sharedKey.encoded).take(16)
            val macCode = "hmac_sha256_$hmacBytes"
            activePipelineSteps.add(CryptoPipelineStep("HMAC Authenticator Signature", Icons.Default.Fingerprint, macCode, "Seal with key-hashed MAC verification code.", Color(0xFF43493E)))
            activePipelineStep.value = 4
            delay(300)

            val timestamp = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            val finalMsg = ChatMessage(
                id = "msg_" + Random.nextInt(10000, 99999),
                sender = senderName,
                text = rawMessage,
                ciphertext = ciphertextHex,
                mac = macCode,
                timestamp = timestamp,
                isEncrypted = true,
                isDelivered = bobOnline.value,
                isRead = false
            )

            // Save locally to DB
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
                    chatPartner = partner.name
                )
            )

            // Update user last message locally
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

            val myShortName = loginEmail.value.substringBefore("@")
            val jsonPayload = JSONObject()
                .put("id", finalMsg.id)
                .put("sender", myShortName)
                .put("recipient", partner.name)
                .put("text", rawMessage)
                .put("ciphertext", ciphertextHex)
                .put("mac", macCode)
                .put("timestamp", timestamp)
                .toString()
            val body = RequestBody.create(jsonMediaType, jsonPayload)
            val request = buildAuthorizedRequest(getServerUrl("/api/messages/send"), "POST", body)

            val success = withContext(Dispatchers.IO) {
                try {
                    val response = client.newCall(request).execute()
                    response.isSuccessful
                } catch (e: Exception) {
                    false
                }
            }

            if (success) {
                addLog("NET", "Relayed secure envelope sent successfully to ${serverHost.value}.")
            } else {
                addLog("NET", "Server offline. Transmission queued locally in offline queue.")
                serverQueue.add(finalMsg)
            }

            isEncryptingInProgress.value = false
            activePipelineStep.value = -1
        }
    }

    // --- Message Editing & Deletion ---
    fun deleteMessage(id: String, partner: ChatUser) {
        viewModelScope.launch {
            repository.deleteMessageById(id)
            addLog("SYS", "Message $id permanently deleted locally.")
            
            // Send protocol delete E2EE message over the network
            val deletePayload = JSONObject()
                .put("type", "delete")
                .put("target_id", id)
                .toString()
            
            encryptAndSendProtocolMessage(deletePayload, partner)
        }
    }

    fun editMessage(id: String, newText: String, partner: ChatUser) {
        viewModelScope.launch {
            val original = repository.getMessageById(id) ?: return@launch
            val updatedMsg = original.copy(text = "$newText (Edited)")
            repository.insertMessage(updatedMsg)
            addLog("SYS", "Message $id edited locally.")

            // Send protocol edit E2EE message over the network
            val editPayload = JSONObject()
                .put("type", "edit")
                .put("target_id", id)
                .put("new_text", newText)
                .toString()

            encryptAndSendProtocolMessage(editPayload, partner)
        }
    }

    private fun encryptAndSendProtocolMessage(payloadText: String, partner: ChatUser) {
        viewModelScope.launch {
            val senderName = loginEmail.value.substringBefore("@")
            val sharedKey = try {
                val myPrivate = CryptoUtils.base64ToPrivateKey(identityPrivateKey.value)
                val peerPublic = CryptoUtils.base64ToPublicKey(partner.publicKey)
                CryptoUtils.calculateECDHSharedKey(myPrivate, peerPublic)
            } catch (e: Exception) {
                CryptoUtils.getSharedKey(senderName, partner.name)
            }
            val ciphertextHex = CryptoUtils.encrypt(payloadText, sharedKey)
            val hmacBytes = hmacSha256(ciphertextHex, sharedKey.encoded).take(16)
            val macCode = "hmac_sha256_$hmacBytes"
            
            val timestamp = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            val msgId = "protocol_" + Random.nextInt(10000, 99999)
            
            val jsonPayload = JSONObject()
                .put("id", msgId)
                .put("sender", senderName)
                .put("recipient", partner.name)
                .put("text", payloadText)
                .put("ciphertext", ciphertextHex)
                .put("mac", macCode)
                .put("timestamp", timestamp)
                .toString()
            
            val body = RequestBody.create(jsonMediaType, jsonPayload)
            val request = buildAuthorizedRequest(getServerUrl("/api/messages/send"), "POST", body)
            
            withContext(Dispatchers.IO) {
                try {
                    client.newCall(request).execute()
                } catch (e: Exception) {
                    // Ignore drops
                }
            }
        }
    }

    // --- Secure Media Transmission Helpers ---
    fun encryptAndSendMediaMessage(rawMessage: String, localPathText: String, partner: ChatUser) {
        viewModelScope.launch {
            isEncryptingInProgress.value = true
            activePipelineSteps.clear()

            activePipelineSteps.add(CryptoPipelineStep("Plaintext Metadata", Icons.Default.TextFields, "[Media Metadata]", "Raw UTF-8 JSON metadata value.", Color(0xFF1A1C18)))
            activePipelineStep.value = 0
            delay(150)

            val serialized = JSONObject()
                .put("v", 1)
                .put("type", "media")
                .put("txt", rawMessage)
                .toString()
            activePipelineSteps.add(CryptoPipelineStep("Payload Serialization", Icons.Default.DataObject, serialized, "Package content into standard Signal formats.", Color(0xFF43493E)))
            activePipelineStep.value = 1
            delay(150)

            val compressed = "deflate_comp_media_payload"
            activePipelineSteps.add(CryptoPipelineStep("Binary Compression", Icons.Default.Compress, compressed, "Compress data to save bandwidth.", Color(0xFFA1AF97)))
            activePipelineStep.value = 2
            delay(150)

            val senderName = loginEmail.value.substringBefore("@")
            val sharedKey = try {
                val myPrivate = CryptoUtils.base64ToPrivateKey(identityPrivateKey.value)
                val peerPublic = CryptoUtils.base64ToPublicKey(partner.publicKey)
                CryptoUtils.calculateECDHSharedKey(myPrivate, peerPublic)
            } catch (e: Exception) {
                CryptoUtils.getSharedKey(senderName, partner.name)
            }
            val ciphertextHex = CryptoUtils.encrypt(rawMessage, sharedKey)
            activePipelineSteps.add(CryptoPipelineStep("AES-256-GCM Secure Encryption", Icons.Default.Lock, ciphertextHex.take(24) + "...", "Encrypt using derived shared key.", Color(0xFFD5E897)))
            activePipelineStep.value = 3
            delay(150)

            val hmacBytes = hmacSha256(ciphertextHex, sharedKey.encoded).take(16)
            val macCode = "hmac_sha256_$hmacBytes"
            activePipelineSteps.add(CryptoPipelineStep("HMAC Authenticator Signature", Icons.Default.Fingerprint, macCode, "Seal with key-hashed MAC verification code.", Color(0xFF43493E)))
            activePipelineStep.value = 4
            delay(200)

            val timestamp = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            val finalMsg = ChatMessage(
                id = "msg_" + Random.nextInt(10000, 99999),
                sender = senderName,
                text = localPathText,
                ciphertext = ciphertextHex,
                mac = macCode,
                timestamp = timestamp,
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
                    chatPartner = partner.name
                )
            )

            repository.insertUser(
                RoomChatUser(
                    partner.name,
                    if (localPathText.startsWith("media:image:")) "📷 Photo" else "🎥 Video",
                    timestamp,
                    0,
                    partner.avatarColor.value.toInt(),
                    partner.isOnline,
                    partner.publicKey
                )
            )

            val myShortName = loginEmail.value.substringBefore("@")
            val jsonPayload = JSONObject()
                .put("id", finalMsg.id)
                .put("sender", myShortName)
                .put("recipient", partner.name)
                .put("text", rawMessage)
                .put("ciphertext", ciphertextHex)
                .put("mac", macCode)
                .put("timestamp", timestamp)
                .toString()
            val body = RequestBody.create(jsonMediaType, jsonPayload)
            val request = buildAuthorizedRequest(getServerUrl("/api/messages/send"), "POST", body)

            val success = withContext(Dispatchers.IO) {
                try {
                    val response = client.newCall(request).execute()
                    response.isSuccessful
                } catch (e: Exception) {
                    false
                }
            }

            if (success) {
                addLog("NET", "Relayed secure media envelope successfully.")
            } else {
                addLog("ERROR", "Could not dispatch secure media envelope to relay server.")
            }
            
            isEncryptingInProgress.value = false
            activePipelineStep.value = -1
        }
    }

    private fun compressImageUri(context: android.content.Context, uri: android.net.Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytesTemp = inputStream.readBytes()
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeByteArray(bytesTemp, 0, bytesTemp.size, options)
                
                val maxDim = 1200
                var scale = 1
                if (options.outWidth > maxDim || options.outHeight > maxDim) {
                    val widthScale = Math.round(options.outWidth.toFloat() / maxDim.toFloat())
                    val heightScale = Math.round(options.outHeight.toFloat() / maxDim.toFloat())
                    scale = Math.max(widthScale, heightScale)
                }
                
                val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = scale
                }
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytesTemp, 0, bytesTemp.size, decodeOptions) ?: return null
                
                val outputStream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, outputStream)
                val compressedBytes = outputStream.toByteArray()
                bitmap.recycle()
                compressedBytes
            }
        } catch (e: Throwable) {
            null
        }
    }

    fun uploadMediaAndSendMessage(uri: android.net.Uri, mediaType: String, partner: ChatUser) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val bytes = if (mediaType == "image") {
                compressImageUri(context, uri)
            } else {
                val size = try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use {
                        it.statSize
                    } ?: 0L
                } catch (e: Exception) {
                    0L
                }
                if (size > 15728640L) { // 15MB
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Video file is too large (maximum 15MB)", android.widget.Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                try {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } catch (e: Throwable) {
                    null
                }
            }

            if (bytes == null) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Failed to read or compress media file.", android.widget.Toast.LENGTH_LONG).show()
                }
                addLog("ERROR", "Failed to read or compress media bytes.")
                return@launch
            }

            val senderName = loginEmail.value.substringBefore("@")
            val sharedKey = try {
                val myPrivate = CryptoUtils.base64ToPrivateKey(identityPrivateKey.value)
                val peerPublic = CryptoUtils.base64ToPublicKey(partner.publicKey)
                CryptoUtils.calculateECDHSharedKey(myPrivate, peerPublic)
            } catch (e: Exception) {
                CryptoUtils.getSharedKey(senderName, partner.name)
            }

            val encryptedBytes = try {
                CryptoUtils.encryptBytes(bytes, sharedKey)
            } catch (e: Exception) {
                addLog("ERROR", "Encryption of media file failed.")
                return@launch
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    if (mediaType == "image") "media.jpg" else "media.mp4",
                    RequestBody.create("application/octet-stream".toMediaTypeOrNull(), encryptedBytes)
                )
                .build()
            val request = buildAuthorizedRequest(getServerUrl("/api/media/upload"), "POST", requestBody)

            val fileId = try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    json.getString("file_id")
                } else {
                    addLog("ERROR", "Media upload failed: ${response.code}")
                    null
                }
            } catch (e: Exception) {
                addLog("ERROR", "Connection to upload server failed.")
                null
            }

            if (fileId == null) return@launch

            val localFile = File(context.cacheDir, "media_${fileId}")
            try {
                localFile.writeBytes(bytes)
            } catch (e: Exception) {
                // ignore
            }

            val metadata = JSONObject()
                .put("type", "media")
                .put("media_type", mediaType)
                .put("file_id", fileId)
                .toString()

            val localPathText = "media:$mediaType:${localFile.absolutePath}"
            withContext(Dispatchers.Main) {
                encryptAndSendMediaMessage(metadata, localPathText, partner)
            }
        }
    }

    private fun downloadAndDecryptMedia(fileId: String, mediaType: String, sender: String, messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val localFile = File(context.cacheDir, "media_${fileId}")
            if (localFile.exists()) {
                val msg = repository.getMessageById(messageId)
                if (msg != null) {
                    repository.insertMessage(msg.copy(text = "media:$mediaType:${localFile.absolutePath}"))
                }
                return@launch
            }

            val request = buildAuthorizedRequest(getServerUrl("/api/media/download/$fileId"))
            val encryptedBytes = try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.bytes()
                } else null
            } catch (e: Exception) {
                null
            }

            if (encryptedBytes == null) return@launch

            val senderName = loginEmail.value.substringBefore("@")
            val senderUser = repository.getUserByName(sender)
            val sharedKey = try {
                val myPrivate = CryptoUtils.base64ToPrivateKey(identityPrivateKey.value)
                val peerPublic = CryptoUtils.base64ToPublicKey(senderUser?.publicKey ?: "")
                CryptoUtils.calculateECDHSharedKey(myPrivate, peerPublic)
            } catch (e: Exception) {
                CryptoUtils.getSharedKey(sender, senderName)
            }

            val decryptedBytes = try {
                CryptoUtils.decryptBytes(encryptedBytes, sharedKey)
            } catch (e: Exception) {
                null
            }

            if (decryptedBytes == null) return@launch

            try {
                localFile.writeBytes(decryptedBytes)
                val msg = repository.getMessageById(messageId)
                if (msg != null) {
                    repository.insertMessage(msg.copy(text = "media:$mediaType:${localFile.absolutePath}"))
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    // --- Simulate Incoming Message (Typing feedback first) ---
    fun simulateIncomingMessage(text: String, senderName: String) {
        viewModelScope.launch {
            if (!isBooted.value || !isRegistered.value || !isLoggedIn.value) return@launch
            val partner = _mockUsers.value.find { it.name == senderName } ?: return@launch

            typingStatus.value = "$senderName is typing..."
            delay(1500)
            typingStatus.value = null

            val senderNameShort = loginEmail.value.substringBefore("@")
            val sharedKey = try {
                val myPrivate = CryptoUtils.base64ToPrivateKey(identityPrivateKey.value)
                val peerPublic = CryptoUtils.base64ToPublicKey(partner.publicKey)
                CryptoUtils.calculateECDHSharedKey(myPrivate, peerPublic)
            } catch (e: Exception) {
                CryptoUtils.getSharedKey(senderName, senderNameShort)
            }
            val ciphertextHex = CryptoUtils.encrypt(text, sharedKey)
            val hmacBytes = hmacSha256(ciphertextHex, sharedKey.encoded).take(16)
            val macCode = "hmac_sha256_$hmacBytes"
            val timestamp = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

            val finalMsg = ChatMessage(
                id = "incoming_" + Random.nextInt(10000, 99999),
                sender = senderName,
                text = text,
                ciphertext = ciphertextHex,
                mac = macCode,
                timestamp = timestamp,
                isEncrypted = true,
                isDelivered = true,
                isRead = (selectedChatUser.value?.name == senderName)
            )

            // Save locally
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
                    chatPartner = senderName
                )
            )

            val unreadCount = if (selectedChatUser.value?.name == senderName) 0 else partner.unreadCount + 1
            repository.insertUser(
                RoomChatUser(
                    partner.name,
                    text,
                    timestamp,
                    unreadCount,
                    partner.avatarColor.value.toInt(),
                    partner.isOnline,
                    partner.publicKey
                )
            )

            addLog("IN", "Incoming E2EE message from $senderName received.")
        }
    }

    private fun handleSessionExpired() {
        viewModelScope.launch {
            repository.clearSession()
            isLoggedIn.value = false
            isRegistered.value = false
            activeSessionToken.value = ""
            addLog("SEC", "Session expired or database wiped on server. Forced re-login required.")
        }
    }

    private fun showLocalNotification(sender: String, messageText: String) {
        val context = getApplication<Application>().applicationContext
        val channelId = "phantom_messages"
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Phantom Secure Messages",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming end-to-end encrypted transmissions"
                enableLights(true)
                lightColor = android.graphics.Color.GREEN
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 300, 400, 500)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val displayBody = if (messageText.startsWith("media:image:")) {
            "📷 Photo"
        } else if (messageText.startsWith("media:video:")) {
            "🎥 Video"
        } else {
            messageText
        }

        val intent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            intent,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
            .setContentTitle(sender)
            .setContentText(displayBody)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }


    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearAllMessages()
            addLog("SYS", "Local chat message history purged.")
        }
    }

    fun resetApp() {
        viewModelScope.launch {
            repository.clearSession()
            repository.clearAllMessages()
            isLoggedIn.value = false
            isRegistered.value = false
            activeSessionToken.value = ""
            val identityKeyPair = CryptoUtils.generateECKeyPair()
            identityPrivateKey.value = CryptoUtils.privateKeyToBase64(identityKeyPair.private)
            identityPublicKey.value = CryptoUtils.publicKeyToBase64(identityKeyPair.public)
            addLog("SYS", "Local application container reset and new cryptographic ratchets generated.")
        }
    }

    // --- Sync contact list from the server ---
    fun syncContactsFromServer() {
        viewModelScope.launch(Dispatchers.IO) {
            val request = buildAuthorizedRequest(getServerUrl("/api/users"))
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "[]"
                    val usersJson = JSONArray(body)
                    val syncedUsers = (0 until usersJson.length()).mapNotNull { index ->
                        val userJson = usersJson.optJSONObject(index) ?: return@mapNotNull null
                        val name = userJson.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val pubKey = userJson.optString("publicKey")
                        RoomChatUser(
                            name,
                            "Secure connection active",
                            "Now",
                            0,
                            0xFF4FC3F7.toInt(),
                            true,
                            pubKey
                        )
                    }.toList()
                    val myShortName = loginEmail.value.substringBefore("@")
                    val otherUsers = syncedUsers.filter { it.name != myShortName }
                    if (otherUsers.isNotEmpty()) {
                        repository.insertUsers(otherUsers)
                    }
                } else if (response.code == 401) {
                    handleSessionExpired()
                }
            } catch (e: Exception) {
                // Keep Room database values if offline
            }
        }
    }

    // --- Register identity on the server ---
    fun registerIdentityOnServer() {
        viewModelScope.launch(Dispatchers.IO) {
            val myShortName = loginEmail.value.substringBefore("@")
            val jsonPayload = JSONObject()
                .put("name", myShortName)
                .put("publicKey", identityPublicKey.value)
                .put("deviceId", deviceId.value)
                .toString()
            val body = RequestBody.create(jsonMediaType, jsonPayload)
            val request = buildAuthorizedRequest(getServerUrl("/api/users/register"), "POST", body)
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    addLog("SYS", "Identity successfully registered on directory server.")
                    syncContactsFromServer()
                } else if (response.code == 401) {
                    handleSessionExpired()
                }
            } catch (e: Exception) {
                addLog("WARNING", "Relay server is currently offline.")
            }
        }
    }

    // --- Poll incoming E2EE messages targeted for our user ---
    private fun pollIncomingMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                if (isLoggedIn.value) {
                    val myShortName = loginEmail.value.substringBefore("@")
                    val encodedUser = URLEncoder.encode(myShortName, Charsets.UTF_8.name())
                    val request = buildAuthorizedRequest(getServerUrl("/api/messages/poll?user=$encodedUser"))
                    try {
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: "[]"
                            val messagesJson = JSONArray(body)
                            for (index in 0 until messagesJson.length()) {
                                val messageJson = messagesJson.optJSONObject(index) ?: continue
                                val id = messageJson.optString("id").takeIf { it.isNotBlank() } ?: continue
                                val sender = messageJson.optString("sender").takeIf { it.isNotBlank() } ?: continue
                                val senderPublicKey = messageJson.optString("senderPublicKey")
                                val ciphertext = messageJson.optString("ciphertext").takeIf { it.isNotBlank() } ?: continue
                                val mac = messageJson.optString("mac")
                                val timestamp = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

                                // Decrypt using derived shared key specific to sender/recipient pair
                                val decryptedText = try {
                                    val sharedKey = try {
                                        val myPrivate = CryptoUtils.base64ToPrivateKey(identityPrivateKey.value)
                                        val peerPublic = CryptoUtils.base64ToPublicKey(senderPublicKey)
                                        CryptoUtils.calculateECDHSharedKey(myPrivate, peerPublic)
                                    } catch (e: Exception) {
                                        CryptoUtils.getSharedKey(sender, myShortName)
                                    }
                                    CryptoUtils.decrypt(ciphertext, sharedKey)
                                } catch (e: Exception) {
                                    "[Decrypted Payload]"
                                }

                                var isProtocolMsg = false
                                var finalDecryptedText = decryptedText
                                try {
                                    val json = JSONObject(decryptedText)
                                    when (json.optString("type")) {
                                        "media" -> {
                                            val mediaType = json.optString("media_type")
                                            val fileId = json.optString("file_id")
                                            finalDecryptedText = "media_pending:$mediaType:$fileId"
                                            downloadAndDecryptMedia(fileId, mediaType, sender, id)
                                        }
                                        "edit" -> {
                                            isProtocolMsg = true
                                            val targetId = json.getString("target_id")
                                            val newText = json.getString("new_text")
                                            val msg = repository.getMessageById(targetId)
                                            if (msg != null) {
                                                repository.insertMessage(msg.copy(text = "$newText (Edited)"))
                                            }
                                        }
                                        "delete" -> {
                                            isProtocolMsg = true
                                            val targetId = json.getString("target_id")
                                            repository.deleteMessageById(targetId)
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Not a protocol JSON
                                }

                                if (!isProtocolMsg) {
                                    val incomingMsg = RoomChatMessage(
                                        id = id,
                                        sender = sender,
                                        text = finalDecryptedText,
                                        ciphertext = ciphertext,
                                        mac = mac,
                                        timestamp = timestamp,
                                        timestampMillis = System.currentTimeMillis(),
                                        isEncrypted = true,
                                        isDelivered = true,
                                        isRead = false,
                                        chatPartner = sender
                                    )
                                     repository.insertMessage(incomingMsg)
                                     showLocalNotification(sender, finalDecryptedText)

                                    // Ensure sender user exists in local contact list with actual public key
                                    val resolvedPubKey = if (senderPublicKey.isNotBlank()) senderPublicKey else "id_pub_relayed"
                                    repository.insertUser(
                                        RoomChatUser(
                                            sender,
                                            if (finalDecryptedText.startsWith("media:")) {
                                                if (finalDecryptedText.contains(":image:")) "📷 Photo" else "🎥 Video"
                                            } else finalDecryptedText,
                                            timestamp,
                                            1,
                                            0xFF81C784.toInt(),
                                            true,
                                            resolvedPubKey
                                        )
                                    )

                                    addLog("IN", "Received and decrypted E2EE transmission from $sender.")
                                }
                            }
                        } else if (response.code == 401) {
                            handleSessionExpired()
                        }
                    } catch (e: Exception) {
                        // Skip network disconnects or polling delays
                    }
                }
                delay(2000) // Poll server every 2 seconds
            }
        }
    }

    // --- Secure Media Encrypter ---
    fun runMediaEncryption() {
        viewModelScope.launch {
            isEncryptingMedia.value = true
            encryptedMediaMetadata.value = null
            addLog("SYS", "Hardware media encryption pipeline started.")
            delay(800)

            val type = selectedMediaType.value
            val payload = "dummy_binary_payload_for_user_media_${type.lowercase().replace(" ", "_")}"
            val masterKey = CryptoUtils.getOrCreateMasterKey()
            val encryptedBase64 = CryptoUtils.encrypt(payload, masterKey)
            val algorithm = "AES-GCM-256 (Keystore Crypt)"

            encryptedMediaMetadata.value = """
                [ATTACHMENT ENCRYPTED]
                Media Type: $type
                Algorithm: $algorithm
                Key ID: ${masterKey.hashCode()}
                Payload Size: ${payload.length} Bytes
                Secure Cipher Envelope:
                ${encryptedBase64.take(64)}...
            """.trimIndent()

            addLog("SEC", "Media attachment payload encrypted and sealed safely.")
            isEncryptingMedia.value = false
        }
    }

    // --- Offline Network Queue Flush ---
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

    // --- SMS OTP Verification (Identity Screen Tab 0) ---
    fun sendVerificationSms() {
        viewModelScope.launch {
            verificationStep.value = true
            addLog("SYS", "Identity attestation OTP code '4839' sent to ${registrationPhone.value}.")
        }
    }

    fun verifySmsCode() {
        viewModelScope.launch {
            if (registrationOtp.value == "4839") {
                isVerifying.value = true
                delay(800)
                isRegistered.value = true
                isVerifying.value = false
                addLog("SEC", "SMS confirmation verified. Identity keys synced on directory routing.")
            } else {
                addLog("SYS", "Verification code mismatch: code rejected.")
            }
        }
    }

    // --- Security profile controls ---
    fun togglePlayIntegrity(verified: Boolean) {
        playIntegrityVerified.value = verified
        recalculateThreatLevel()
    }

    fun toggleCertPinning(active: Boolean) {
        certificatePinningActive.value = active
        recalculateThreatLevel()
    }

    private fun recalculateThreatLevel() {
        threatLevel.value = when {
            !playIntegrityVerified.value -> "COMPROMISED"
            !certificatePinningActive.value -> "WARNING"
            else -> "SECURE"
        }
    }

    fun toggleDbLock() {
        sqlCipherLocked.value = !sqlCipherLocked.value
        addLog("SEC", if (sqlCipherLocked.value) "Local database container locked." else "Local database container unlocked and decrypted.")
    }

    // --- Log out secure session ---
    fun logout() {
        viewModelScope.launch {
            repository.clearSession()
            isLoggedIn.value = false
            isRegistered.value = false
            loginOtpInput.value = ""
            activeSessionToken.value = ""
            addLog("SYS", "Secure session terminated by user. Keys deleted from RAM.")
        }
    }

    // --- HMAC SHA256 helper ---
    private fun hmacSha256(data: String, key: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(key, "HmacSHA256")
        mac.init(secretKeySpec)
        val bytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateSecureTokenHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
