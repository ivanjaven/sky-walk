// com/example/skywalk/features/chat/domain/models/ChatMessage.kt
package com.example.skywalk.features.chat.domain.models

data class ChatMessage(
    val id: String,
    val chatRoomId: String,
    val senderId: String,
    val senderName: String,
    val senderPhotoUrl: String? = null,
    val content: String = "",
    val imageUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val status: MessageStatus = MessageStatus.SENT
)

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}