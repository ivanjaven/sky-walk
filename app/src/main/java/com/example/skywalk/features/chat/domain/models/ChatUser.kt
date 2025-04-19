// com/example/skywalk/features/chat/domain/models/ChatUser.kt
package com.example.skywalk.features.chat.domain.models

data class ChatUser(
    val id: String,
    val displayName: String,
    val photoUrl: String?,
    val lastSeen: Long = 0,
    val isOnline: Boolean = false
)