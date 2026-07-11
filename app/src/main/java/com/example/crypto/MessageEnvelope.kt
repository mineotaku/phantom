package com.example.crypto

import android.util.Base64
import org.json.JSONObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class MessageEnvelope(
    val protocolVersion: Int,
    val senderIdentityKey: String,     // Base64
    val dhRatchetKey: String,          // Base64
    val previousChainLength: Int,
    val messageNumber: Int,
    val timestamp: Long,
    val nonce: String,                 // Base64
    val ciphertext: String,            // Base64
    val mac: String,                   // Hex HMAC signature
    val x3dhEphemeralKey: String? = null // Base64 (only present in first message of a session)
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("protocolVersion", protocolVersion)
            put("senderIdentityKey", senderIdentityKey)
            put("dhRatchetKey", dhRatchetKey)
            put("previousChainLength", previousChainLength)
            put("messageNumber", messageNumber)
            put("timestamp", timestamp)
            put("nonce", nonce)
            put("ciphertext", ciphertext)
            put("mac", mac)
            if (x3dhEphemeralKey != null) {
                put("x3dhEphemeralKey", x3dhEphemeralKey)
            }
        }.toString()
    }

    fun verifyMac(macKey: ByteArray): Boolean {
        val expectedMac = computeMac(macKey)
        return java.security.MessageDigest.isEqual(
            expectedMac.toByteArray(Charsets.UTF_8),
            mac.toByteArray(Charsets.UTF_8)
        )
    }

    fun computeMac(macKey: ByteArray): String {
        val payload = "$protocolVersion|$senderIdentityKey|$dhRatchetKey|$previousChainLength|$messageNumber|$timestamp|$nonce|$ciphertext|${x3dhEphemeralKey ?: ""}"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(macKey, "HmacSHA256"))
        val macBytes = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return macBytes.joinToString("") { "%02x".format(it) }
    }

    fun isExpired(): Boolean {
        val current = System.currentTimeMillis()
        return (current - timestamp) > CryptoConstants.MAX_MESSAGE_AGE_MILLIS
    }

    companion object {
        fun create(
            senderIdentityKey: ByteArray,
            header: MessageHeader,
            timestamp: Long,
            nonce: ByteArray,
            ciphertext: ByteArray,
            macKey: ByteArray,
            x3dhEphemeralKey: ByteArray? = null
        ): MessageEnvelope {
            val senderKeyStr = Base64.encodeToString(senderIdentityKey, Base64.NO_WRAP)
            val dhKeyStr = Base64.encodeToString(header.dhRatchetPublicKey, Base64.NO_WRAP)
            val nonceStr = Base64.encodeToString(nonce, Base64.NO_WRAP)
            val ciphertextStr = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            val ephemeralKeyStr = x3dhEphemeralKey?.let { Base64.encodeToString(it, Base64.NO_WRAP) }

            val tempEnvelope = MessageEnvelope(
                protocolVersion = CryptoConstants.PROTOCOL_VERSION,
                senderIdentityKey = senderKeyStr,
                dhRatchetKey = dhKeyStr,
                previousChainLength = header.previousChainLength,
                messageNumber = header.messageNumber,
                timestamp = timestamp,
                nonce = nonceStr,
                ciphertext = ciphertextStr,
                mac = "",
                x3dhEphemeralKey = ephemeralKeyStr
            )

            val signature = tempEnvelope.computeMac(macKey)
            return tempEnvelope.copy(mac = signature)
        }

        fun fromJson(json: String): MessageEnvelope {
            val obj = JSONObject(json)
            return MessageEnvelope(
                protocolVersion = obj.getInt("protocolVersion"),
                senderIdentityKey = obj.getString("senderIdentityKey"),
                dhRatchetKey = obj.getString("dhRatchetKey"),
                previousChainLength = obj.getInt("previousChainLength"),
                messageNumber = obj.getInt("messageNumber"),
                timestamp = obj.getLong("timestamp"),
                nonce = obj.getString("nonce"),
                ciphertext = obj.getString("ciphertext"),
                mac = obj.getString("mac"),
                x3dhEphemeralKey = if (obj.has("x3dhEphemeralKey")) obj.getString("x3dhEphemeralKey") else null
            )
        }
    }
}
