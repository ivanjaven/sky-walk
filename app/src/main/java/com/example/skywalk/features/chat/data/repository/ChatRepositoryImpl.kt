// com/example/skywalk/features/chat/data/repository/ChatRepositoryImpl.kt
package com.example.skywalk.features.chat.data.repository

import com.example.skywalk.core.firebase.FirebaseConfig
import com.example.skywalk.features.chat.data.remote.FirebaseChatService
import com.example.skywalk.features.chat.domain.models.ChatMessage
import com.example.skywalk.features.chat.domain.models.ChatRoom
import com.example.skywalk.features.chat.domain.models.ChatUser
import com.example.skywalk.features.chat.domain.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
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

    suspend fun getChatRoomsFresh(): List<ChatRoom> {
        return try {
            // Directly get a fresh snapshot without using Flow
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return emptyList()

            val snapshot = FirebaseConfig.firestore
                .collection("chat_rooms")
                .whereArrayContains("participantIds", currentUserId)
                .orderBy("lastActivityTimestamp", Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                try {
                    // Parse the ChatRoom from document
                    val roomId = doc.id
                    val participantIds = doc.get("participantIds") as? List<String> ?: emptyList()
                    val lastMessageId = doc.getString("lastMessageId")
                    val timestamp = doc.getLong("lastActivityTimestamp") ?: 0

                    // Get participants info (excluding current user)
                    val participants = mutableListOf<ChatUser>()
                    for (participantId in participantIds) {
                        if (participantId != currentUserId) {
                            firebaseChatService.getUserById(participantId)?.let {
                                participants.add(it)
                            }
                        }
                    }

                    // Get last message
                    var lastMessage: ChatMessage? = null
                    if (lastMessageId != null) {
                        val messageDoc = FirebaseConfig.firestore
                            .collection("chat_messages")
                            .document(lastMessageId)
                            .get()
                            .await()
                        if (messageDoc.exists()) {
                            lastMessage = firebaseChatService.parseChatMessage(messageDoc)
                        }
                    }

                    // Get unread count
                    val unreadCount = doc.getLong("unreadCount_$currentUserId")?.toInt() ?: 0

                    ChatRoom(
                        id = roomId,
                        participants = participants,
                        lastMessage = lastMessage,
                        unreadCount = unreadCount,
                        timestamp = timestamp
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing chat room: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting fresh chat rooms: ${e.message}")
            emptyList()
        }
    }
}