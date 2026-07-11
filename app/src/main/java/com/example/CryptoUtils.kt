package com.example

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
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

    // Generate EC KeyPair (P-256) for secure ECDH key exchanges
    fun generateECKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(256)
        return keyPairGenerator.generateKeyPair()
    }

    // Serialization: Convert PublicKey to Base64 String
    fun publicKeyToBase64(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    // Serialization: Convert PrivateKey to Base64 String
    fun privateKeyToBase64(privateKey: PrivateKey): String {
        return Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
    }

    // Deserialization: Parse Base64 String to PublicKey
    fun base64ToPublicKey(base64Str: String): PublicKey {
        val keyBytes = Base64.decode(base64Str, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(spec)
    }

    // Deserialization: Parse Base64 String to PrivateKey
    fun base64ToPrivateKey(base64Str: String): PrivateKey {
        val keyBytes = Base64.decode(base64Str, Base64.DEFAULT)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePrivate(spec)
    }

    // Compute ECDH Shared Key (P-256) and hash using SHA-256 to produce a 256-bit symmetric SecretKey
    fun calculateECDHSharedKey(privateKey: PrivateKey, publicKey: PublicKey): SecretKey {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        val sharedSecret = keyAgreement.generateSecret()
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest(sharedSecret)
        return SecretKeySpec(keyBytes, "AES")
    }

    // REMOVED: getSharedKey(user1, user2) — insecure name-based key derivation with zero secret entropy.
    // All callers must use proper ECDH key exchange via calculateECDHSharedKey() or fail explicitly.

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
        if (combined.size < 12) throw IllegalArgumentException("decryption_error: payload_too_short")
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
    fun encryptBytes(data: ByteArray, secretKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return combined
    }

    fun decryptBytes(combined: ByteArray, secretKey: SecretKey): ByteArray {
        if (combined.size < 12) throw IllegalArgumentException("Payload too short")
        val iv = ByteArray(12)
        val ciphertext = ByteArray(combined.size - 12)
        System.arraycopy(combined, 0, iv, 0, 12)
        System.arraycopy(combined, 12, ciphertext, 0, ciphertext.size)

        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(ciphertext)
    }

    // --- Added for security roadmap ---
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        return com.example.crypto.X3DHProtocol.hkdf(ikm, salt, info, length)
    }

    fun encryptAesGcm(plaintext: ByteArray, key: ByteArray, aad: ByteArray? = null): Pair<ByteArray, ByteArray> {
        val nonce = generateNonce()
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        val spec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), spec)
        if (aad != null) {
            cipher.updateAAD(aad)
        }
        val ciphertext = cipher.doFinal(plaintext)
        return Pair(ciphertext, nonce)
    }

    fun decryptAesGcm(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray, aad: ByteArray? = null): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        val spec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
        if (aad != null) {
            cipher.updateAAD(aad)
        }
        return cipher.doFinal(ciphertext)
    }

    fun generateNonce(): ByteArray {
        val nonce = ByteArray(12)
        java.security.SecureRandom().nextBytes(nonce)
        return nonce
    }

    fun computeHmacSha256(data: ByteArray, key: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        return java.security.MessageDigest.isEqual(a, b)
    }

    @Volatile
    private var isDatabaseResetting = false

    fun getDatabasePassphrase(context: android.content.Context): ByteArray {
        val prefs = context.getSharedPreferences("phantom_security", android.content.Context.MODE_PRIVATE)
        val encryptedKeyBase64 = prefs.getString("encrypted_db_key", null)
        val masterKey = getOrCreateMasterKey()
        
        if (encryptedKeyBase64 == null) {
            val rawKey = ByteArray(32)
            java.security.SecureRandom().nextBytes(rawKey)
            val encryptedBase64 = encrypt(Base64.encodeToString(rawKey, Base64.NO_WRAP), masterKey)
            prefs.edit().putString("encrypted_db_key", encryptedBase64).apply()
            return rawKey
        } else {
            return try {
                val decryptedStr = decrypt(encryptedKeyBase64, masterKey)
                Base64.decode(decryptedStr, Base64.DEFAULT)
            } catch (e: Exception) {
                android.util.Log.e("PHANTOM_CRYPTO", "Failed to decrypt database passphrase. Possible KeyStore corruption. Deleting database to allow recovery.", e)
                
                if (!isDatabaseResetting) {
                    isDatabaseResetting = true
                    try {
                        context.deleteDatabase("phantom_secure.db")
                    } finally {
                        isDatabaseResetting = false
                    }
                }

                val rawKey = ByteArray(32)
                java.security.SecureRandom().nextBytes(rawKey)
                val encryptedBase64 = encrypt(Base64.encodeToString(rawKey, Base64.NO_WRAP), masterKey)
                prefs.edit().putString("encrypted_db_key", encryptedBase64).apply()
                rawKey
            }
        }
    }

    fun pad(input: ByteArray, blockSize: Int = 128): ByteArray {
        val paddingLength = blockSize - (input.size % blockSize)
        val padded = ByteArray(input.size + paddingLength)
        System.arraycopy(input, 0, padded, 0, input.size)
        for (i in input.size until padded.size) {
            padded[i] = paddingLength.toByte()
        }
        return padded
    }

    fun unpad(input: ByteArray): ByteArray {
        if (input.isEmpty()) return input
        val paddingLength = input.last().toInt() and 0xFF
        if (paddingLength <= 0 || paddingLength > input.size) return input
        for (i in (input.size - paddingLength) until input.size) {
            if (input[i].toInt() != paddingLength) {
                return input
            }
        }
        val unpadded = ByteArray(input.size - paddingLength)
        System.arraycopy(input, 0, unpadded, 0, unpadded.size)
        return unpadded
    }
}


