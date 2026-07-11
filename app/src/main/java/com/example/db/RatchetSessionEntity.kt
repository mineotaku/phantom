package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ratchet_sessions")
data class RatchetSessionEntity(
    @PrimaryKey val peerId: String,
    val rootKey: String,              // Base64-encoded
    val sendingChainKey: String,      // Base64-encoded
    val receivingChainKey: String,    // Base64-encoded
    val dhSendingPrivateKey: String,  // Base64-encoded
    val dhSendingPublicKey: String,   // Base64-encoded
    val dhReceivingPublicKey: String, // Base64-encoded
    val sendingCounter: Int,
    val receivingCounter: Int,
    val previousChainLength: Int,
    val skippedMessageKeys: String,   // JSON map
    val createdAt: Long,
    val updatedAt: Long
)
