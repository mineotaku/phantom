package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "linked_devices")
data class DeviceEntity(
    @PrimaryKey val deviceId: String,
    val userId: String,
    val publicKey: String,
    val deviceName: String,
    val linkedAt: Long,
    val lastSeenAt: Long,
    val isRevoked: Boolean
)
