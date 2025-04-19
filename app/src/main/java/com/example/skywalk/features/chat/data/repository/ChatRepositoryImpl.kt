// com/example/skywalk/features/chat/data/repository/ChatRepositoryImpl.kt
package com.example.skywalk.features.chat.data.repository

import com.example.skywalk.features.chat.data.remote.FirebaseChatService
import com.example.skywalk.features.chat.domain.models.ChatMessage
import com.example.skywalk.features.chat.domain.models.ChatRoom
import com.example.skywalk.features.chat.domain.models.ChatUser
import com.example.skywalk.features.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import java.io.File

class ChatRepositoryImpl : ChatRepository {
    private val firebaseChatService = FirebaseChatService()

    override suspend fun getUserById(userId: String): ChatUser? {
        return firebaseChatService.getUserById(userId)
    }

    override suspend fun searchUsers(query: String): List<ChatUser> {
        return firebaseChatService.searchUsers(query)
    }

    override fun getChatRooms(): Flow<List<ChatRoom>> {
        return firebaseChatService.getUserChatRooms()
    }

    override suspend fun getOrCreateChatRoom(otherUserId: String): Result<ChatRoom> {
        return firebaseChatService.getOrCreateChatRoom(otherUserId)
    }

    override fun getChatMessages(chatRoomId: String): Flow<List<ChatMessage>> {
        return firebaseChatService.getChatMessages(chatRoomId)
    }

    override suspend fun sendTextMessage(chatRoomId: String, content: String): Result<ChatMessage> {
        return firebaseChatService.sendTextMessage(chatRoomId, content)
    }

    override suspend fun sendImageMessage(chatRoomId: String, imageFile: File): Result<ChatMessage> {
        return firebaseChatService.sendImageMessage(chatRoomId, imageFile)
    }

    override suspend fun markMessagesAsRead(chatRoomId: String): Result<Unit> {
        return firebaseChatService.markMessagesAsRead(chatRoomId)
    }
}