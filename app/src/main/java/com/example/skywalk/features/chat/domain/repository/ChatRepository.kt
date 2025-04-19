// com/example/skywalk/features/chat/domain/repository/ChatRepository.kt
package com.example.skywalk.features.chat.domain.repository

import com.example.skywalk.features.chat.domain.models.ChatMessage
import com.example.skywalk.features.chat.domain.models.ChatRoom
import com.example.skywalk.features.chat.domain.models.ChatUser
import kotlinx.coroutines.flow.Flow
import java.io.File

interface ChatRepository {
    // User operations
    suspend fun getUserById(userId: String): ChatUser?
    suspend fun searchUsers(query: String): List<ChatUser>

    // Chat room operations
    fun getChatRooms(): Flow<List<ChatRoom>>
    suspend fun getOrCreateChatRoom(otherUserId: String): Result<ChatRoom>

    // Message operations
    fun getChatMessages(chatRoomId: String): Flow<List<ChatMessage>>
    suspend fun sendTextMessage(chatRoomId: String, content: String): Result<ChatMessage>
    suspend fun sendImageMessage(chatRoomId: String, imageFile: File): Result<ChatMessage>
    suspend fun markMessagesAsRead(chatRoomId: String): Result<Unit>
}