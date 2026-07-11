package com.example.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object StrongBoxKeyManager {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val MASTER_ALIAS = "phantom_strongbox_master"
    private const val IDENTITY_ALIAS = "phantom_identity_key"

    fun isStrongBoxAvailable(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        } else {
            false
        }
    }

    fun getKeyBackingType(context: Context): String {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val hasMaster = keyStore.containsAlias(MASTER_ALIAS)
        if (!hasMaster) return "Not Provisioned"
        
        return if (isStrongBoxAvailable(context)) {
            "StrongBox HSM"
        } else {
            "TEE (Hardware Secure)"
        }
    }

    fun generateMasterKey(context: Context): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val specBuilder = KeyGenParameterSpec.Builder(
            MASTER_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isStrongBoxAvailable(context)) {
            try {
                specBuilder.setIsStrongBoxBacked(true)
                keyGenerator.init(specBuilder.build())
                return keyGenerator.generateKey()
            } catch (e: StrongBoxUnavailableException) {
                // fallback to TEE
            }
        }

        specBuilder.setIsStrongBoxBacked(false)
        keyGenerator.init(specBuilder.build())
        return keyGenerator.generateKey()
    }

    fun generateIdentityKeyPair(context: Context): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE_PROVIDER)
        val specBuilder = KeyGenParameterSpec.Builder(
            IDENTITY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setKeySize(256)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isStrongBoxAvailable(context)) {
            try {
                specBuilder.setIsStrongBoxBacked(true)
                keyPairGenerator.initialize(specBuilder.build())
                return keyPairGenerator.generateKeyPair()
            } catch (e: StrongBoxUnavailableException) {
                // fallback to TEE
            }
        }

        specBuilder.setIsStrongBoxBacked(false)
        keyPairGenerator.initialize(specBuilder.build())
        return keyPairGenerator.generateKeyPair()
    }

    fun getMasterKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        return keyStore.getKey(MASTER_ALIAS, null) as? SecretKey
    }

    fun getIdentityKeyPair(): KeyPair? {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val privateKey = keyStore.getKey(IDENTITY_ALIAS, null) as? java.security.PrivateKey ?: return null
        val publicKey = keyStore.getCertificate(IDENTITY_ALIAS)?.publicKey ?: return null
        return KeyPair(publicKey, privateKey)
    }

    fun deleteAllKeys() {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        keyStore.deleteEntry(MASTER_ALIAS)
        keyStore.deleteEntry(IDENTITY_ALIAS)
    }
}
