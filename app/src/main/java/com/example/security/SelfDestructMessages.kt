package com.example.security

import org.json.JSONObject
import java.security.SecureRandom

enum class SelfDestructTimer(val durationMillis: Long, val displayName: String) {
    OFF(0, "Off"),
    SECONDS_5(5_000, "5 seconds"),
    SECONDS_30(30_000, "30 seconds"),
    MINUTES_1(60_000, "1 minute"),
    MINUTES_5(300_000, "5 minutes"),
    HOURS_1(3_600_000, "1 hour"),
    HOURS_24(86_400_000, "24 hours"),
    WEEKS_1(604_800_000, "1 week")
}

object SelfDestructManager {
    private val random = SecureRandom()

    fun calculateDestructionTime(timer: SelfDestructTimer): Long {
        if (timer == SelfDestructTimer.OFF) return 0
        return System.currentTimeMillis() + timer.durationMillis
    }

    fun isExpired(destructionTime: Long): Boolean {
        return destructionTime in 1..System.currentTimeMillis()
    }

    fun remainingTimeMillis(destructionTime: Long): Long {
        if (destructionTime <= 0) return 0
        return maxOf(0, destructionTime - System.currentTimeMillis())
    }

    fun formatRemainingTime(remainingMillis: Long): String {
        if (remainingMillis <= 0) return "0s"
        val seconds = remainingMillis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d ${hours % 24}h"
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }

    fun secureDeleteFile(file: java.io.File) {
        try {
            if (file.exists() && file.isFile) {
                // Overwrite file contents with random bytes to prevent recovery from storage flash cells
                val length = file.length()
                val randomBytes = ByteArray(minOf(65536, length.toInt()))
                val out = java.io.FileOutputStream(file)
                var written = 0L
                while (written < length) {
                    random.nextBytes(randomBytes)
                    val chunk = minOf(randomBytes.size.toLong(), length - written).toInt()
                    out.write(randomBytes, 0, chunk)
                    written += chunk
                }
                out.flush()
                out.fd.sync()
                out.close()
            }
        } catch (e: Exception) {
            // Log or ignore
        } finally {
            file.delete()
        }
    }

    fun createSelfDestructProtocolMessage(messageId: String): String {
        return JSONObject().apply {
            put("type", "self_destruct_ack")
            put("target_id", messageId)
        }.toString()
    }
}
