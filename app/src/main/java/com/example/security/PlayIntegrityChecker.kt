package com.example.security

import android.content.Context
import android.util.Log

data class IntegrityResult(
    val meetsBasicIntegrity: Boolean,
    val meetsDeviceIntegrity: Boolean,
    val meetsStrongIntegrity: Boolean,
    val errorMessage: String?
)

object PlayIntegrityChecker {
    private const val TAG = "PlayIntegrityChecker"
    private const val PREFS_NAME = "phantom_integrity"
    private const val KEY_LAST_CHECK = "last_check_time"
    private const val KEY_BASIC = "meets_basic"
    private const val KEY_DEVICE = "meets_device"
    private const val KEY_STRONG = "meets_strong"
    private const val KEY_ERROR = "error_msg"

    fun isPlayServicesAvailable(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.google.android.gms", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun checkRootMethod1(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (java.io.File(path).exists()) return true
        }
        return false
    }

    private fun checkRootMethod2(): Boolean {
        val tags = android.os.Build.TAGS
        return tags != null && tags.contains("test-keys")
    }

    private fun checkRootMethod3(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val inReader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            inReader.readLine() != null
        } catch (t: Throwable) {
            false
        } finally {
            process?.destroy()
        }
    }

    fun isRooted(): Boolean {
        return checkRootMethod1() || checkRootMethod2() || checkRootMethod3()
    }

    fun isEmulator(): Boolean {
        return android.os.Build.FINGERPRINT.startsWith("generic") ||
                android.os.Build.FINGERPRINT.startsWith("unknown") ||
                android.os.Build.MODEL.contains("google_sdk") ||
                android.os.Build.MODEL.contains("Emulator") ||
                android.os.Build.MODEL.contains("Android SDK built for x86") ||
                android.os.Build.BOARD == "QC_Reference_Phone" ||
                android.os.Build.MANUFACTURER.contains("Genymotion") ||
                android.os.Build.HOST.startsWith("Build") ||
                (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic")) ||
                "google_sdk" == android.os.Build.PRODUCT
    }

    suspend fun checkIntegrity(context: Context, serverUrl: String, sessionToken: String): IntegrityResult {
        val cached = getCachedResult(context)
        if (cached != null) return cached

        val rooted = isRooted()
        val emulator = isEmulator()
        
        val meetsBasic = !rooted && !emulator
        val meetsDevice = !rooted && !emulator && isPlayServicesAvailable(context)
        val meetsStrong = meetsDevice && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
        
        val errorMsg = when {
            rooted -> "Device is ROOTED (Root binaries detected)"
            emulator -> "Running inside an EMULATOR sandbox"
            !isPlayServicesAvailable(context) -> "Google Play Services unavailable"
            else -> null
        }

        val result = IntegrityResult(
            meetsBasicIntegrity = meetsBasic,
            meetsDeviceIntegrity = meetsDevice,
            meetsStrongIntegrity = meetsStrong,
            errorMessage = errorMsg
        )
        cacheResult(context, result)
        return result
    }

    fun getCachedResult(context: Context): IntegrityResult? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0)
        // 1 hour TTL
        if (System.currentTimeMillis() - lastCheck > 60 * 60 * 1000) {
            return null
        }
        return IntegrityResult(
            meetsBasicIntegrity = prefs.getBoolean(KEY_BASIC, false),
            meetsDeviceIntegrity = prefs.getBoolean(KEY_DEVICE, false),
            meetsStrongIntegrity = prefs.getBoolean(KEY_STRONG, false),
            errorMessage = prefs.getString(KEY_ERROR, null)
        )
    }

    fun cacheResult(context: Context, result: IntegrityResult) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            putBoolean(KEY_BASIC, result.meetsBasicIntegrity)
            putBoolean(KEY_DEVICE, result.meetsDeviceIntegrity)
            putBoolean(KEY_STRONG, result.meetsStrongIntegrity)
            putString(KEY_ERROR, result.errorMessage)
            apply()
        }
    }
}
