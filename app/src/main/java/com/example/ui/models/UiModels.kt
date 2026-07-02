package com.example.ui.models

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class ChatMessage(
    val id: String,
    val sender: String,
    val text: String,
    val ciphertext: String,
    val mac: String,
    val timestamp: String,
    val isEncrypted: Boolean,
    val isDelivered: Boolean,
    val isRead: Boolean
)

data class ChatUser(
    val name: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int,
    val avatarColor: Color,
    val isOnline: Boolean = true,
    val publicKey: String
)

data class NetworkLog(
    val timestamp: Long,
    val type: String, // "IN" | "OUT" | "SEC" | "SYS" | "NET"
    val description: String
)

data class CryptoPipelineStep(
    val title: String,
    val icon: ImageVector,
    val data: String,
    val description: String,
    val color: Color
)
