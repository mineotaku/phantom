package com.example.sync

import android.util.Base64
import com.example.db.DeviceEntity
import com.example.db.PhantomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

data class LinkedDevice(
    val deviceId: String,
    val deviceName: String,
    val publicKey: ByteArray,
    val linkedAt: Long,
    val lastSeenAt: Long,
    val isRevoked: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LinkedDevice) return false
        return deviceId == other.deviceId &&
                deviceName == other.deviceName &&
                publicKey.contentEquals(other.publicKey) &&
                linkedAt == other.linkedAt &&
                lastSeenAt == other.lastSeenAt &&
                isRevoked == other.isRevoked
    }

    override fun hashCode(): Int {
        var result = deviceId.hashCode()
        result = 31 * result + deviceName.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + linkedAt.hashCode()
        result = 31 * result + lastSeenAt.hashCode()
        result = 31 * result + isRevoked.hashCode()
        return result
    }
}

class MultiDeviceManager(private val repository: PhantomRepository) {
    val linkedDevices = MutableStateFlow<List<LinkedDevice>>(emptyList())

    suspend fun registerDevice(deviceId: String, deviceName: String, publicKey: ByteArray) {
        val entity = DeviceEntity(
            deviceId = deviceId,
            userId = "", // global user
            publicKey = Base64.encodeToString(publicKey, Base64.NO_WRAP),
            deviceName = deviceName,
            linkedAt = System.currentTimeMillis(),
            lastSeenAt = System.currentTimeMillis(),
            isRevoked = false
        )
        repository.insertDevice(entity)
        loadDevices()
    }

    suspend fun loadDevices() {
        val list = repository.getAllDevices()
        linkedDevices.value = list.map {
            LinkedDevice(
                deviceId = it.deviceId,
                deviceName = it.deviceName,
                publicKey = Base64.decode(it.publicKey, Base64.DEFAULT),
                linkedAt = it.linkedAt,
                lastSeenAt = it.lastSeenAt,
                isRevoked = it.isRevoked
            )
        }
    }

    suspend fun revokeDevice(deviceId: String) {
        repository.revokeDevice(deviceId)
        loadDevices()
    }

    fun generateLinkingQrData(localIdentityPublic: ByteArray, deviceId: String): String {
        val obj = JSONObject().apply {
            put("publicKey", Base64.encodeToString(localIdentityPublic, Base64.NO_WRAP))
            put("deviceId", deviceId)
        }
        return Base64.encodeToString(obj.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun parseLinkingQrData(data: String): Pair<ByteArray, String>? {
        return try {
            val jsonStr = String(Base64.decode(data, Base64.DEFAULT), Charsets.UTF_8)
            val obj = JSONObject(jsonStr)
            val pubBytes = Base64.decode(obj.getString("publicKey"), Base64.DEFAULT)
            val devId = obj.getString("deviceId")
            Pair(pubBytes, devId)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun syncReadReceipt(messageId: String, readAt: Long): String {
        return JSONObject().apply {
            put("type", "sync_read")
            put("message_id", messageId)
            put("read_at", readAt)
        }.toString()
    }

    suspend fun syncDeletion(messageId: String): String {
        return JSONObject().apply {
            put("type", "sync_delete")
            put("message_id", messageId)
        }.toString()
    }

    fun getActiveDeviceCount(): Int {
        return linkedDevices.value.count { !it.isRevoked }
    }
}
