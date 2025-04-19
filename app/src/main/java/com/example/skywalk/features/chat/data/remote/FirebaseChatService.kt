// com/example/skywalk/features/chat/data/remote/FirebaseChatService.kt
package com.example.skywalk.features.chat.data.remote

import android.net.Uri
import com.example.skywalk.core.firebase.FirebaseConfig
import com.example.skywalk.features.chat.domain.models.ChatMessage
import com.example.skywalk.features.chat.domain.models.ChatRoom
import com.example.skywalk.features.chat.domain.models.ChatUser
import com.example.skywalk.features.chat.domain.models.MessageStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.*

class FirebaseChatService {
    private val db = FirebaseConfig.firestore
    private val storage = FirebaseConfig.storage
    private val auth = FirebaseAuth.getInstance()

    private val usersCollection = db.collection("users")
    private val chatRoomsCollection = db.collection("chat_rooms")
    private val messagesCollection = db.collection("chat_messages")

    // Get current user ID
    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // Get current user display name
    private suspend fun getCurrentUserDisplayName(): String {
        return try {
            val userDoc = currentUserId?.let { usersCollection.document(it).get().await() }
            userDoc?.getString("displayName") ?: "Anonymous"
        } catch (e: Exception) {
            Timber.e(e, "Error getting current user name")
            "Anonymous"
        }
    }

    // Get current user photo URL
    private suspend fun getCurrentUserPhotoUrl(): String? {
        return try {
            val userDoc = currentUserId?.let { usersCollection.document(it).get().await() }
            userDoc?.getString("photoUrl")
        } catch (e: Exception) {
            Timber.e(e, "Error getting current user photo URL")
            null
        }
    }

    // Get user by ID
    suspend fun getUserById(userId: String): ChatUser? {
        return try {
            val userDoc = usersCollection.document(userId).get().await()
            if (userDoc.exists()) {
                ChatUser(
                    id = userId,
                    displayName = userDoc.getString("displayName") ?: "Anonymous",
                    photoUrl = userDoc.getString("photoUrl"),
                    lastSeen = userDoc.getLong("lastLoginAt") ?: 0,
                    isOnline = false
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting user by ID")
            null
        }
    }

    // Search users
    suspend fun searchUsers(query: String): List<ChatUser> {
        return try {
            val snapshot = usersCollection.get().await()
            snapshot.documents.mapNotNull { doc ->
                val id = doc.id
                val displayName = doc.getString("displayName") ?: return@mapNotNull null
                val photoUrl = doc.getString("photoUrl")
                val lastSeen = doc.getLong("lastLoginAt") ?: 0

                // Don't include current user in search results
                if (id == currentUserId) return@mapNotNull null

                // Filter by display name (case-insensitive)
                if (query.isNotEmpty() && !displayName.contains(query, ignoreCase = true)) {
                    return@mapNotNull null
                }

                ChatUser(
                    id = id,
                    displayName = displayName,
                    photoUrl = photoUrl,
                    lastSeen = lastSeen,
                    isOnline = false
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error searching users")
            emptyList()
        }
    }

    // Get user's chat rooms
    fun getUserChatRooms(): Flow<List<ChatRoom>> = callbackFlow {
        val userId = currentUserId ?: throw IllegalStateException("User not authenticated")

        val listener = chatRoomsCollection
            .whereArrayContains("participantIds", userId)
            .orderBy("lastActivityTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Error fetching chat rooms")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val chatRooms = mutableListOf<ChatRoom>()

                    // Launch a coroutine to handle async operations
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                        try {
                            snapshot.documents.forEach { doc ->
                                try {
                                    val roomId = doc.id
                                    val participantIds = doc.get("participantIds") as? List<String> ?: emptyList()
                                    val lastMessageId = doc.getString("lastMessageId")
                                    val timestamp = doc.getLong("lastActivityTimestamp") ?: 0

                                    // Get participants info (excluding current user)
                                    val participants = mutableListOf<ChatUser>()

                                    // Get all participants in a batch
                                    participantIds.forEach { participantId ->
                                        if (participantId != userId) {
                                            getUserById(participantId)?.let { participants.add(it) }
                                        }
                                    }

                                    // Get the last message for preview
                                    var lastMessage: ChatMessage? = null
                                    if (lastMessageId != null) {
                                        val messageDoc = messagesCollection.document(lastMessageId).get().await()
                                        if (messageDoc.exists()) {
                                            lastMessage = parseChatMessage(messageDoc)
                                        }
                                    }

                                    // Get unread count for current user
                                    val unreadCount = doc.getLong("unreadCount_$userId")?.toInt() ?: 0

                                    val chatRoom = ChatRoom(
                                        id = roomId,
                                        participants = participants,
                                        lastMessage = lastMessage,
                                        unreadCount = unreadCount,
                                        timestamp = timestamp
                                    )

                                    chatRooms.add(chatRoom)
                                } catch (e: Exception) {
                                    Timber.e(e, "Error parsing chat room document")
                                }
                            }

                            trySend(chatRooms)
                        } catch (e: Exception) {
                            Timber.e(e, "Error processing chat rooms")
                        }
                    }
                }
            }

        awaitClose { listener.remove() }
    }

    // Get or create a chat room between two users
    suspend fun getOrCreateChatRoom(otherUserId: String): Result<ChatRoom> {
        try {
            val userId = currentUserId ?: return Result.failure(IllegalStateException("User not authenticated"))

            // First check if a chat room already exists between these users
            val existingRoomQuery = chatRoomsCollection
                .whereArrayContains("participantIds", userId)
                .get()
                .await()

            for (doc in existingRoomQuery.documents) {
                val participantIds = doc.get("participantIds") as? List<String> ?: continue
                if (participantIds.size == 2 && participantIds.contains(otherUserId)) {
                    // Found existing room
                    val roomId = doc.id
                    val timestamp = doc.getLong("lastActivityTimestamp") ?: 0

                    // Get the other user's details
                    val otherUser = getUserById(otherUserId) ?: return Result.failure(Exception("User not found"))

                    // Get the last message if available
                    val lastMessageId = doc.getString("lastMessageId")
                    var lastMessage: ChatMessage? = null
                    if (lastMessageId != null) {
                        val messageDoc = messagesCollection.document(lastMessageId).get().await()
                        if (messageDoc.exists()) {
                            lastMessage = parseChatMessage(messageDoc)
                        }
                    }

                    // Get unread count for current user
                    val unreadCount = doc.getLong("unreadCount_$userId")?.toInt() ?: 0

                    val chatRoom = ChatRoom(
                        id = roomId,
                        participants = listOf(otherUser),
                        lastMessage = lastMessage,
                        unreadCount = unreadCount,
                        timestamp = timestamp
                    )

                    return Result.success(chatRoom)
                }
            }

            // No existing room, create a new one
            val otherUser = getUserById(otherUserId) ?: return Result.failure(Exception("User not found"))
            val roomId = chatRoomsCollection.document().id

            val roomData = hashMapOf(
                "participantIds" to listOf(userId, otherUserId),
                "lastActivityTimestamp" to System.currentTimeMillis(),
                "createdAt" to System.currentTimeMillis(),
                "unreadCount_$userId" to 0,
                "unreadCount_$otherUserId" to 0
            )

            chatRoomsCollection.document(roomId).set(roomData).await()

            val chatRoom = ChatRoom(
                id = roomId,
                participants = listOf(otherUser),
                timestamp = System.currentTimeMillis()
            )

            return Result.success(chatRoom)
        } catch (e: Exception) {
            Timber.e(e, "Error getting or creating chat room")
            return Result.failure(e)
        }
    }

    // Get messages for a chat room
    fun getChatMessages(chatRoomId: String): Flow<List<ChatMessage>> = callbackFlow {
        val listener = messagesCollection
            .whereEqualTo("chatRoomId", chatRoomId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Error fetching chat messages")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        try {
                            parseChatMessage(doc)
                        } catch (e: Exception) {
                            Timber.e(e, "Error parsing chat message document")
                            null
                        }
                    }
                    trySend(messages)
                }
            }

        awaitClose { listener.remove() }
    }

    // Send a text message
    suspend fun sendTextMessage(chatRoomId: String, content: String): Result<ChatMessage> {
        try {
            val userId = currentUserId ?: return Result.failure(IllegalStateException("User not authenticated"))
            val userName = getCurrentUserDisplayName()
            val userPhotoUrl = getCurrentUserPhotoUrl()

            val messageId = messagesCollection.document().id
            val timestamp = System.currentTimeMillis()

            val messageData = hashMapOf<String, Any>(
                "id" to messageId,
                "chatRoomId" to chatRoomId,
                "senderId" to userId,
                "senderName" to userName,
                "senderPhotoUrl" to (userPhotoUrl ?: ""),
                "content" to content,
                "timestamp" to timestamp,
                "isRead" to false,
                "status" to MessageStatus.SENT.name
            )

            // Update the chat room with the last message info
            val roomUpdateData = mutableMapOf<String, Any>(
                "lastMessageId" to messageId,
                "lastActivityTimestamp" to timestamp
            )

            // Get the room to find the other participant
            val roomDoc = chatRoomsCollection.document(chatRoomId).get().await()
            val participantIds = roomDoc.get("participantIds") as? List<String> ?: emptyList()
            val otherUserIds = participantIds.filter { it != userId }

            // Increment the unread counter for other participants
            for (otherUserId in otherUserIds) {
                roomUpdateData["unreadCount_$otherUserId"] = FieldValue.increment(1)
            }

            // Perform the updates in a batch
            val batch = db.batch()
            batch.set(messagesCollection.document(messageId), messageData)
            batch.update(chatRoomsCollection.document(chatRoomId), roomUpdateData as Map<String, Any>)
            batch.commit().await()

            val message = ChatMessage(
                id = messageId,
                chatRoomId = chatRoomId,
                senderId = userId,
                senderName = userName,
                senderPhotoUrl = userPhotoUrl,
                content = content,
                timestamp = timestamp,
                isRead = false,
                status = MessageStatus.SENT
            )

            return Result.success(message)
        } catch (e: Exception) {
            Timber.e(e, "Error sending text message")
            return Result.failure(e)
        }
    }

    // Send an image message
    suspend fun sendImageMessage(chatRoomId: String, imageFile: File): Result<ChatMessage> {
        try {
            val userId = currentUserId ?: return Result.failure(IllegalStateException("User not authenticated"))
            val userName = getCurrentUserDisplayName()
            val userPhotoUrl = getCurrentUserPhotoUrl()

            val messageId = messagesCollection.document().id
            val timestamp = System.currentTimeMillis()

            // Upload the image to Firebase Storage
            val imageRef = storage.reference.child("chat_images/$chatRoomId/${UUID.randomUUID()}")
            val uploadTask = imageRef.putFile(Uri.fromFile(imageFile)).await()
            val imageUrl = imageRef.downloadUrl.await().toString()

            val messageData = hashMapOf<String, Any>(
                "id" to messageId,
                "chatRoomId" to chatRoomId,
                "senderId" to userId,
                "senderName" to userName,
                "senderPhotoUrl" to (userPhotoUrl ?: ""),
                "imageUrl" to imageUrl,
                "timestamp" to timestamp,
                "isRead" to false,
                "status" to MessageStatus.SENT.name
            )

            // Update the chat room with the last message info
            val roomUpdateData = mutableMapOf<String, Any>(
                "lastMessageId" to messageId,
                "lastActivityTimestamp" to timestamp
            )

            // Get the room to find the other participant
            val roomDoc = chatRoomsCollection.document(chatRoomId).get().await()
            val participantIds = roomDoc.get("participantIds") as? List<String> ?: emptyList()
            val otherUserIds = participantIds.filter { it != userId }

            // Increment the unread counter for other participants
            for (otherUserId in otherUserIds) {
                roomUpdateData["unreadCount_$otherUserId"] = FieldValue.increment(1)
            }

            // Perform the updates in a batch
            val batch = db.batch()
            batch.set(messagesCollection.document(messageId), messageData)
            batch.update(chatRoomsCollection.document(chatRoomId), roomUpdateData as Map<String, Any>)
            batch.commit().await()

            val message = ChatMessage(
                id = messageId,
                chatRoomId = chatRoomId,
                senderId = userId,
                senderName = userName,
                senderPhotoUrl = userPhotoUrl,
                imageUrl = imageUrl,
                timestamp = timestamp,
                isRead = false,
                status = MessageStatus.SENT
            )

            return Result.success(message)
        } catch (e: Exception) {
            Timber.e(e, "Error sending image message")
            return Result.failure(e)
        }
    }

    // Mark messages as read
    suspend fun markMessagesAsRead(chatRoomId: String): Result<Unit> {
        try {
            val userId = currentUserId ?: return Result.failure(IllegalStateException("User not authenticated"))

            // Reset the unread counter for this user
            val updateData = mutableMapOf<String, Any>(
                "unreadCount_$userId" to 0
            )

            chatRoomsCollection.document(chatRoomId)
                .update(updateData as Map<String, Any>)
                .await()

            // Mark unread messages as read
            val unreadMessages = messagesCollection
                .whereEqualTo("chatRoomId", chatRoomId)
                .whereNotEqualTo("senderId", userId) // Only messages from others
                .whereEqualTo("isRead", false)
                .get()
                .await()

            val batch = db.batch()
            unreadMessages.documents.forEach { doc ->
                batch.update(doc.reference, "isRead", true)
                batch.update(doc.reference, "status", MessageStatus.READ.name)
            }

            batch.commit().await()

            return Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error marking messages as read")
            return Result.failure(e)
        }
    }

    // Parse a chat message from a Firestore document
    private fun parseChatMessage(doc: com.google.firebase.firestore.DocumentSnapshot): ChatMessage {
        val id = doc.getString("id") ?: doc.id
        val chatRoomId = doc.getString("chatRoomId") ?: ""
        val senderId = doc.getString("senderId") ?: ""
        val senderName = doc.getString("senderName") ?: "Anonymous"
        val senderPhotoUrl = doc.getString("senderPhotoUrl")
        val content = doc.getString("content") ?: ""
        val imageUrl = doc.getString("imageUrl")
        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
        val isRead = doc.getBoolean("isRead") ?: false
        val statusStr = doc.getString("status") ?: MessageStatus.SENT.name
        val status = try {
            MessageStatus.valueOf(statusStr)
        } catch (e: Exception) {
            MessageStatus.SENT
        }

        return ChatMessage(
            id = id,
            chatRoomId = chatRoomId,
            senderId = senderId,
            senderName = senderName,
            senderPhotoUrl = senderPhotoUrl,
            content = content,
            imageUrl = imageUrl,
            timestamp = timestamp,
            isRead = isRead,
            status = status
        )
    }
}