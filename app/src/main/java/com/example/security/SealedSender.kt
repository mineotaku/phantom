package com.example.security

import android.util.Base64
import com.example.crypto.X3DHProtocol
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class SenderCertificate(
    val senderUserId: String,
    val senderIdentityKey: ByteArray, // Base64 encoded or raw bytes
    val expiration: Long,
    val serverSignature: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SenderCertificate) return false
        return senderUserId == other.senderUserId &&
                senderIdentityKey.contentEquals(other.senderIdentityKey) &&
                expiration == other.expiration &&
                serverSignature.contentEquals(other.serverSignature)
    }

    override fun hashCode(): Int {
        var result = senderUserId.hashCode()
        result = 31 * result + senderIdentityKey.contentHashCode()
        result = 31 * result + expiration.hashCode()
        result = 31 * result + serverSignature.contentHashCode()
        return result
    }
}

data class SealedSenderEnvelope(
    val recipientUserId: String,
    val encryptedContent: ByteArray // formatted as: [4-byte pubKey len][ephemeral pubKey][nonce][ciphertext]
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SealedSenderEnvelope) return false
        return recipientUserId == other.recipientUserId &&
                encryptedContent.contentEquals(other.encryptedContent)
    }

    override fun hashCode(): Int {
        var result = recipientUserId.hashCode()
        result = 31 * result + encryptedContent.contentHashCode()
        return result
    }
}

data class SealedSenderContent(
    val senderCertificate: SenderCertificate,
    val messageContent: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SealedSenderContent) return false
        return senderCertificate == other.senderCertificate &&
                messageContent.contentEquals(other.messageContent)
    }

    override fun hashCode(): Int {
        var result = senderCertificate.hashCode()
        result = 31 * result + messageContent.contentHashCode()
        return result
    }
}

object SealedSender {
    private const val KEY_ALGORITHM = "EC"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val HKDF_INFO = "PhantomSealedSender"

    fun sealMessage(
        recipientUserId: String,
        senderCertificate: SenderCertificate,
        recipientIdentityKey: ByteArray,
        messageContent: ByteArray
    ): SealedSenderEnvelope {
        // Generate ephemeral EC keypair
        val keyGen = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        keyGen.initialize(256)
        val ephemeralKeyPair = keyGen.generateKeyPair()

        // Derive ECDH shared secret
        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
        val recipientPub = keyFactory.generatePublic(X509EncodedKeySpec(recipientIdentityKey))
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(ephemeralKeyPair.private)
        keyAgreement.doPhase(recipientPub, true)
        val sharedSecret = keyAgreement.generateSecret()

        // Derive AES key using HKDF
        val aesKeyBytes = X3DHProtocol.hkdf(sharedSecret, ByteArray(32), HKDF_INFO.toByteArray(Charsets.UTF_8), 32)
        val aesKey = SecretKeySpec(aesKeyBytes, "AES")

        // Serialize SealedSenderContent
        val contentJson = JSONObject().apply {
            put("certificate", serializeCertificate(senderCertificate))
            put("message", Base64.encodeToString(messageContent, Base64.NO_WRAP))
        }.toString().toByteArray(Charsets.UTF_8)

        // Encrypt with AES-GCM
        val nonce = ByteArray(12)
        java.security.SecureRandom().nextBytes(nonce)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(contentJson)

        // Package encrypted content: [4-byte pubKey len][pubKey][nonce][ciphertext]
        val ephemeralPubBytes = ephemeralKeyPair.public.encoded
        val buffer = ByteBuffer.allocate(4 + ephemeralPubBytes.size + nonce.size + ciphertext.size)
        buffer.putInt(ephemeralPubBytes.size)
        buffer.put(ephemeralPubBytes)
        buffer.put(nonce)
        buffer.put(ciphertext)

        return SealedSenderEnvelope(
            recipientUserId = recipientUserId,
            encryptedContent = buffer.array()
        )
    }

    fun unsealMessage(
        recipientIdentityPrivate: ByteArray,
        envelope: SealedSenderEnvelope
    ): SealedSenderContent {
        val buffer = ByteBuffer.wrap(envelope.encryptedContent)
        val pubKeyLen = buffer.int
        if (pubKeyLen <= 0 || pubKeyLen > 1024) {
            throw IllegalArgumentException("Invalid public key length in sealed sender envelope: $pubKeyLen")
        }
        if (pubKeyLen > buffer.remaining()) {
            throw IllegalArgumentException("Public key length exceeds available envelope buffer remaining bytes")
        }
        val ephemeralPubBytes = ByteArray(pubKeyLen)
        buffer.get(ephemeralPubBytes)
        val nonce = ByteArray(12)
        buffer.get(nonce)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        // Derive ECDH shared secret
        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
        val recipientPriv = keyFactory.generatePrivate(PKCS8EncodedKeySpec(recipientIdentityPrivate))
        val ephemeralPub = keyFactory.generatePublic(X509EncodedKeySpec(ephemeralPubBytes))

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(recipientPriv)
        keyAgreement.doPhase(ephemeralPub, true)
        val sharedSecret = keyAgreement.generateSecret()

        // Derive AES key
        val aesKeyBytes = X3DHProtocol.hkdf(sharedSecret, ByteArray(32), HKDF_INFO.toByteArray(Charsets.UTF_8), 32)
        val aesKey = SecretKeySpec(aesKeyBytes, "AES")

        // Decrypt
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, nonce))
        val decryptedJsonBytes = cipher.doFinal(ciphertext)
        val decryptedJson = JSONObject(String(decryptedJsonBytes, Charsets.UTF_8))

        val certStr = decryptedJson.getString("certificate")
        val messageStr = decryptedJson.getString("message")

        return SealedSenderContent(
            senderCertificate = deserializeCertificate(certStr),
            messageContent = Base64.decode(messageStr, Base64.DEFAULT)
        )
    }

    fun verifySenderCertificate(certificate: SenderCertificate, serverPublicKey: ByteArray): Boolean {
        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
        val serverPub = keyFactory.generatePublic(X509EncodedKeySpec(serverPublicKey))

        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initVerify(serverPub)
        sig.update(certificate.senderUserId.toByteArray(Charsets.UTF_8))
        sig.update(certificate.senderIdentityKey)
        sig.update(ByteBuffer.allocate(8).putLong(certificate.expiration).array())

        return sig.verify(certificate.serverSignature)
    }

    fun serializeCertificate(cert: SenderCertificate): String {
        return JSONObject().apply {
            put("userId", cert.senderUserId)
            put("identityKey", Base64.encodeToString(cert.senderIdentityKey, Base64.NO_WRAP))
            put("expiration", cert.expiration)
            put("signature", Base64.encodeToString(cert.serverSignature, Base64.NO_WRAP))
        }.toString()
    }

    fun deserializeCertificate(data: String): SenderCertificate {
        val obj = JSONObject(data)
        return SenderCertificate(
            senderUserId = obj.getString("userId"),
            senderIdentityKey = Base64.decode(obj.getString("identityKey"), Base64.DEFAULT),
            expiration = obj.getLong("expiration"),
            serverSignature = Base64.decode(obj.getString("signature"), Base64.DEFAULT)
        )
    }
}
