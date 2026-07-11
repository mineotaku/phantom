package com.example.security

import android.content.Context
import android.util.Base64
import com.example.db.PhantomRepository
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

object DuressPin {
    private const val PREFS_NAME = "phantom_security"
    private const val KEY_REAL_HASH = "real_pin_hash"
    private const val KEY_DURESS_HASH = "duress_pin_hash"

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(pin.toByteArray(Charsets.UTF_8))
    }

    private fun storeCombined(context: Context, key: String, pin: String) {
        val salt = generateSalt()
        val hash = hashPin(pin, salt)
        val combined = ByteArray(salt.size + hash.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(hash, 0, combined, salt.size, hash.size)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(key, Base64.encodeToString(combined, Base64.NO_WRAP)).apply()
    }

    private fun verifyCombined(context: Context, key: String, inputPin: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedCombinedStr = prefs.getString(key, null) ?: return false
        val combined = Base64.decode(storedCombinedStr, Base64.DEFAULT)
        if (combined.size < 48) return false
        val salt = ByteArray(16)
        val storedHash = ByteArray(32)
        System.arraycopy(combined, 0, salt, 0, 16)
        System.arraycopy(combined, 16, storedHash, 0, 32)
        val inputHash = hashPin(inputPin, salt)
        return MessageDigest.isEqual(storedHash, inputHash)
    }

    fun storeRealPin(context: Context, pin: String) {
        storeCombined(context, KEY_REAL_HASH, pin)
    }

    fun storeDuressPin(context: Context, pin: String) {
        storeCombined(context, KEY_DURESS_HASH, pin)
    }

    fun verifyRealPin(context: Context, inputPin: String): Boolean {
        return verifyCombined(context, KEY_REAL_HASH, inputPin)
    }

    fun verifyDuressPin(context: Context, inputPin: String): Boolean {
        return verifyCombined(context, KEY_DURESS_HASH, inputPin)
    }

    fun isAppLockEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_REAL_HASH)
    }

    fun isDuressEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_DURESS_HASH)
    }

    fun removeRealPin(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_REAL_HASH).apply()
    }

    fun removeDuressPin(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_DURESS_HASH).apply()
    }

    // Backwards compatibility layer
    fun isEnabled(context: Context): Boolean {
        return isDuressEnabled(context) || isAppLockEnabled(context)
    }

    fun verifyPin(context: Context, inputPin: String): Boolean {
        return verifyDuressPin(context, inputPin) || verifyRealPin(context, inputPin)
    }

    fun storePin(context: Context, pin: String) {
        storeDuressPin(context, pin)
    }

    fun removePin(context: Context) {
        removeDuressPin(context)
    }

    suspend fun executePanicWipe(context: Context, repository: PhantomRepository) {
        // 1. Wipe database entries
        try {
            repository.clearAllMessages()
            repository.clearSession()
            repository.clearAllDevices()
        } catch (e: Exception) {
            // ignore
        }

        // Close the database explicitly to release locks on the files
        try {
            repository.database?.close()
        } catch (e: Exception) {
            // ignore
        }

        // 2. Wipe all shared preferences files from disk
        try {
            val sharedPrefsDir = File(context.filesDir.parentFile, "shared_prefs")
            if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
                val files = sharedPrefsDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        // 3. Delete cache directory contents securely
        deleteDirectoryContents(context.cacheDir)

        // 4. Delete app files directory contents securely
        deleteDirectoryContents(context.filesDir)

        // 5. Directly delete the database files using absolute paths
        try {
            val dbFile = context.getDatabasePath("phantom_secure.db")
            val walFile = File(dbFile.absolutePath + "-wal")
            val shmFile = File(dbFile.absolutePath + "-shm")
            dbFile.delete()
            walFile.delete()
            shmFile.delete()
        } catch (e: Exception) {
            // ignore
        }

        // 6. Force exit process to prevent RAM remanence
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun deleteDirectoryContents(dir: File?) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                deleteDirectoryContents(file)
            }
            SelfDestructManager.secureDeleteFile(file)
        }
    }
}
