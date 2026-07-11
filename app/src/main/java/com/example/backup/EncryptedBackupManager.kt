package com.example.backup

import android.util.Base64
import com.example.db.PhantomRepository
import com.example.db.UserSession
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object EncryptedBackupManager {
    private val random = SecureRandom()
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val ITERATIONS = 600000
    const val KEY_LENGTH = 256

    private fun sha256(data: ByteArray): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }

    fun deriveBackupKey(passphrase: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt
    }

    fun encryptBackup(data: ByteArray, passphrase: String): ByteArray {
        val salt = generateSalt()
        val keyBytes = deriveBackupKey(passphrase, salt)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val nonce = ByteArray(12)
        random.nextBytes(nonce)

        // Compute SHA-256 checksum of data and prefix it
        val checksum = sha256(data)
        val payload = ByteArray(checksum.size + data.size)
        System.arraycopy(checksum, 0, payload, 0, checksum.size)
        System.arraycopy(data, 0, payload, checksum.size, data.size)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(payload)

        val result = ByteArray(salt.size + nonce.size + ciphertext.size)
        System.arraycopy(salt, 0, result, 0, salt.size)
        System.arraycopy(nonce, 0, result, salt.size, nonce.size)
        System.arraycopy(ciphertext, 0, result, salt.size + nonce.size, ciphertext.size)

        return result
    }

    fun decryptBackup(encryptedData: ByteArray, passphrase: String): ByteArray {
        if (encryptedData.size < 28) throw IllegalArgumentException("Backup payload too short")
        val salt = ByteArray(16)
        val nonce = ByteArray(12)
        val ciphertext = ByteArray(encryptedData.size - 28)

        System.arraycopy(encryptedData, 0, salt, 0, 16)
        System.arraycopy(encryptedData, 16, nonce, 0, 12)
        System.arraycopy(encryptedData, 28, ciphertext, 0, ciphertext.size)

        val keyBytes = deriveBackupKey(passphrase, salt)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, nonce))
        val decryptedPayload = cipher.doFinal(ciphertext)

        if (decryptedPayload.size < 32) {
            throw SecurityException("Decrypted backup data is corrupt (too short for checksum).")
        }

        // Extract checksum and data
        val storedChecksum = ByteArray(32)
        val data = ByteArray(decryptedPayload.size - 32)
        System.arraycopy(decryptedPayload, 0, storedChecksum, 0, 32)
        System.arraycopy(decryptedPayload, 32, data, 0, data.size)

        // Verify checksum
        val computedChecksum = sha256(data)
        if (!java.security.MessageDigest.isEqual(storedChecksum, computedChecksum)) {
            throw SecurityException("Backup checksum validation failed. Password might be incorrect or payload has been tampered with.")
        }

        return data
    }

    suspend fun createBackup(repository: PhantomRepository): ByteArray {
        val session = repository.getSession()
        val backupJson = JSONObject().apply {
            if (session != null) {
                put("email", session.email)
                put("deviceId", session.deviceId)
                put("tokenFCM", session.tokenFCM)
                put("identityPublicKey", session.identityPublicKey)
                // Decrypt private key before backing up so that backup remains device-agnostic (portable)
                val decryptedPrivKey = com.example.CryptoUtils.decrypt(session.identityPrivateKey)
                put("identityPrivateKey", decryptedPrivKey)
                put("signedPreKey", session.signedPreKey)
                put("databaseKeyHex", session.databaseKeyHex)
                put("sessionToken", session.sessionToken)
            }
            put("backupTimestamp", System.currentTimeMillis())
        }
        return backupJson.toString().toByteArray(Charsets.UTF_8)
    }

    suspend fun restoreBackup(repository: PhantomRepository, data: ByteArray) {
        val jsonStr = String(data, Charsets.UTF_8)
        val obj = JSONObject(jsonStr)

        if (obj.has("email")) {
            val decryptedPrivKey = obj.getString("identityPrivateKey")
            // Encrypt private key with the new device's master keystore key before storing in DB
            val encryptedPrivKey = com.example.CryptoUtils.encrypt(decryptedPrivKey)

            val session = UserSession(
                id = 1,
                isLoggedIn = true,
                email = obj.getString("email"),
                deviceId = obj.getString("deviceId"),
                tokenFCM = obj.getString("tokenFCM"),
                identityPublicKey = obj.getString("identityPublicKey"),
                identityPrivateKey = encryptedPrivKey,
                signedPreKey = obj.getString("signedPreKey"),
                databaseKeyHex = obj.getString("databaseKeyHex"),
                sessionToken = obj.getString("sessionToken")
            )
            repository.insertSession(session)
        }
    }
}
