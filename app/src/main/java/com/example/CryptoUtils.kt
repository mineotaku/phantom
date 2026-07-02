package com.example

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "phantom_database_master_key"
    private const val AES_GCM_NOPADDING = "AES/GCM/NoPadding"

    fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(ALIAS, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    // Derive a shared symmetric key dynamically for two users based on sorted identities
    fun getSharedKey(user1: String, user2: String): SecretKey {
        val sortedString = listOf(user1, user2).sorted().joinToString("_")
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest(sortedString.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plaintext: String, secretKey: SecretKey = getOrCreateMasterKey()): String {
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encryptedBase64: String, secretKey: SecretKey = getOrCreateMasterKey()): String {
        val combined = Base64.decode(encryptedBase64, Base64.DEFAULT)
        if (combined.size < 12) return "decryption_error: payload_too_short"
        val iv = ByteArray(12)
        val ciphertext = ByteArray(combined.size - 12)
        System.arraycopy(combined, 0, iv, 0, 12)
        System.arraycopy(combined, 12, ciphertext, 0, ciphertext.size)

        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val decrypted = cipher.doFinal(ciphertext)
        return String(decrypted, Charsets.UTF_8)
    }
}
