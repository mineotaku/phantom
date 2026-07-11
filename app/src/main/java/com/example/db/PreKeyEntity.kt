package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signed_prekeys")
data class SignedPreKeyEntity(
    @PrimaryKey val keyId: Int,
    val publicKey: String,    // Base64
    val privateKey: String,   // Base64
    val signature: String,    // Base64
    val createdAt: Long
)

@Entity(tableName = "one_time_prekeys")
data class OneTimePreKeyEntity(
    @PrimaryKey val keyId: Int,
    val publicKey: String,    // Base64
    val privateKey: String,   // Base64
    val isConsumed: Boolean,
    val createdAt: Long
)
