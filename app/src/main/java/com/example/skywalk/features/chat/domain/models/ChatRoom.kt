// com/example/skywalk/features/chat/domain/models/ChatRoom.kt
package com.example.skywalk.features.chat.domain.models

data class ChatRoom(
    val id: String,
    val participants: List<ChatUser>,
    val lastMessage: ChatMessage? = null,
    val unreadCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)