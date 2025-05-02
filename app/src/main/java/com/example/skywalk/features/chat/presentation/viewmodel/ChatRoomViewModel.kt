// com/example/skywalk/features/chat/presentation/viewmodel/ChatRoomViewModel.kt
package com.example.skywalk.features.chat.presentation.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.skywalk.features.chat.data.repository.ChatRepositoryImpl
import com.example.skywalk.features.chat.domain.models.ChatMessage
import com.example.skywalk.features.chat.domain.models.ChatUser
import com.example.skywalk.features.chat.domain.models.MessageStatus
import com.example.skywalk.features.chat.domain.usecases.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ChatRoomViewModel(application: Application) : AndroidViewModel(application) {
    // Repository and use cases
    private val repository = ChatRepositoryImpl()
    private val getChatMessagesUseCase = GetChatMessagesUseCase(repository)
    private val sendTextMessageUseCase = SendTextMessageUseCase(repository)
    private val sendImageMessageUseCase = SendImageMessageUseCase(repository)
    private val markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository)
    private val getUserByIdUseCase = GetUserByIdUseCase(repository)

    // Current chat room ID
    private val _chatRoomId = MutableStateFlow<String?>(null)
    val chatRoomId: StateFlow<String?> = _chatRoomId

    // Other user in the chat
    private val _otherUser = MutableStateFlow<ChatUser?>(null)
    val otherUser: StateFlow<ChatUser?> = _otherUser

    // UI state
    private val _uiState = MutableStateFlow<ChatRoomUiState>(ChatRoomUiState.Loading)
    val uiState: StateFlow<ChatRoomUiState> = _uiState

    // Messages
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    // Message input
    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText

    // Selected image
    private val _selectedImage = MutableStateFlow<Uri?>(null)
    val selectedImage: StateFlow<Uri?> = _selectedImage

    // Is sending
    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    // Current user details
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private val currentUserName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Me"
    private val currentUserPhotoUrl = FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()

    fun setChatRoom(chatRoomId: String, otherUserId: String) {
        _chatRoomId.value = chatRoomId
        loadOtherUser(otherUserId)
        loadMessages(chatRoomId)
        markMessagesAsRead(chatRoomId)
    }

    private fun loadOtherUser(userId: String) {
        viewModelScope.launch {
            try {
                val user = getUserByIdUseCase(userId)
                _otherUser.value = user
            } catch (e: Exception) {
                Timber.e(e, "Error loading other user")
            }
        }
    }

    private fun loadMessages(chatRoomId: String) {
        viewModelScope.launch {
            _uiState.value = ChatRoomUiState.Loading

            try {
                // Add this line to set a timeout for the Loading state
                // This ensures we transition to Success state even if no messages are received
                launch {
                    kotlinx.coroutines.delay(2000) // 2 seconds timeout
                    if (_uiState.value is ChatRoomUiState.Loading) {
                        _uiState.value = ChatRoomUiState.Success
                    }
                }

                getChatMessagesUseCase(chatRoomId)
                    .catch { e ->
                        Timber.e(e, "Error loading messages")
                        _uiState.value = ChatRoomUiState.Error(e.message ?: "Failed to load messages")
                    }
                    .collect { msgs ->
                        _messages.value = msgs
                        _uiState.value = ChatRoomUiState.Success
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error in loadMessages")
                _uiState.value = ChatRoomUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun setMessageText(text: String) {
        _messageText.value = text
    }

    fun setSelectedImage(uri: Uri?) {
        _selectedImage.value = uri
    }

    fun sendMessage() {
        val chatRoomId = _chatRoomId.value ?: return
        val text = _messageText.value.trim()
        val imageUri = _selectedImage.value
        val currentId = currentUserId ?: return

        if (text.isBlank() && imageUri == null) {
            return
        }

        _isSending.value = true

        viewModelScope.launch {
            try {
                // Generate temporary message ID for optimistic update
                val tempMessageId = UUID.randomUUID().toString()
                val timestamp = System.currentTimeMillis()

                // Clear input first for immediate feedback
                val messageContent = text
                _messageText.value = ""
                val selectedImageCopy = imageUri
                _selectedImage.value = null

                // First, add an optimistic message to the UI
                if (messageContent.isNotBlank()) {
                    val optimisticMessage = ChatMessage(
                        id = tempMessageId,
                        chatRoomId = chatRoomId,
                        senderId = currentId,
                        senderName = currentUserName,
                        senderPhotoUrl = currentUserPhotoUrl,
                        content = messageContent,
                        timestamp = timestamp,
                        isRead = false,
                        status = MessageStatus.SENDING
                    )

                    // Add to the messages list
                    val updatedMessages = _messages.value.toMutableList()
                    updatedMessages.add(optimisticMessage)
                    _messages.value = updatedMessages

                    // Send the actual message
                    val result = sendTextMessageUseCase(chatRoomId, messageContent)

                    result.fold(
                        onSuccess = { sentMessage ->
                            // Update the optimistic message with the real one
                            val index = _messages.value.indexOfFirst { it.id == tempMessageId }
                            if (index != -1) {
                                val newMessages = _messages.value.toMutableList()
                                newMessages[index] = sentMessage
                                _messages.value = newMessages
                            }
                        },
                        onFailure = { error ->
                            // Update optimistic message to show failure
                            val index = _messages.value.indexOfFirst { it.id == tempMessageId }
                            if (index != -1) {
                                val newMessages = _messages.value.toMutableList()
                                newMessages[index] = newMessages[index].copy(status = MessageStatus.FAILED)
                                _messages.value = newMessages
                            }
                            Timber.e(error, "Error sending text message")
                            _uiState.value = ChatRoomUiState.Error("Failed to send message. Please try again.")
                        }
                    )
                }

                if (selectedImageCopy != null) {
                    // For image, add an optimistic image message
                    val optimisticImageMessage = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        chatRoomId = chatRoomId,
                        senderId = currentId,
                        senderName = currentUserName,
                        senderPhotoUrl = currentUserPhotoUrl,
                        imageUrl = selectedImageCopy.toString(), // Local URI for preview
                        timestamp = timestamp,
                        isRead = false,
                        status = MessageStatus.SENDING
                    )

                    // Add to the messages list
                    val updatedMessages = _messages.value.toMutableList()
                    updatedMessages.add(optimisticImageMessage)
                    _messages.value = updatedMessages

                    // Convert Uri to File
                    val context = getApplication<Application>()
                    val tempFile = File(context.cacheDir, "temp_message_image_${System.currentTimeMillis()}.jpg")

                    try {
                        context.contentResolver.openInputStream(selectedImageCopy)?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        val imageMessageId = optimisticImageMessage.id
                        val result = sendImageMessageUseCase(chatRoomId, tempFile)

                        result.fold(
                            onSuccess = { sentImageMessage ->
                                // Update the optimistic message with the real one
                                val index = _messages.value.indexOfFirst { it.id == imageMessageId }
                                if (index != -1) {
                                    val newMessages = _messages.value.toMutableList()
                                    newMessages[index] = sentImageMessage
                                    _messages.value = newMessages
                                }
                            },
                            onFailure = { error ->
                                // Update optimistic message to show failure
                                val index = _messages.value.indexOfFirst { it.id == imageMessageId }
                                if (index != -1) {
                                    val newMessages = _messages.value.toMutableList()
                                    newMessages[index] = newMessages[index].copy(status = MessageStatus.FAILED)
                                    _messages.value = newMessages
                                }
                                Timber.e(error, "Error sending image message")
                                _uiState.value = ChatRoomUiState.Error("Failed to send image. Please try again.")
                            }
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Error processing image")
                        _uiState.value = ChatRoomUiState.Error("Failed to process image. Please try again.")
                    } finally {
                        // Clean up temp file
                        tempFile.delete()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in sendMessage")
                _uiState.value = ChatRoomUiState.Error(e.message ?: "Unknown error")
            } finally {
                _isSending.value = false
            }
        }
    }

    fun markMessagesAsRead(chatRoomId: String) {
        viewModelScope.launch {
            try {
                val result = markMessagesAsReadUseCase(chatRoomId)
                result.onFailure { error ->
                    Timber.e(error, "Error marking messages as read")
                    // Retry once after a short delay
                    kotlinx.coroutines.delay(500)
                    markMessagesAsReadUseCase(chatRoomId)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in markMessagesAsRead")
            }
        }
    }

    fun formatTime(timestamp: Long): String {
        val date = Date(timestamp)
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
    }

    fun formatDateHeader(timestamp: Long): String {
        val date = Date(timestamp)
        val now = Calendar.getInstance()
        val messageDate = Calendar.getInstance().apply { time = date }

        return when {
            isSameDay(now, messageDate) -> "Today"
            isYesterday(now, messageDate) -> "Yesterday"
            else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(date)
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(today: Calendar, otherDate: Calendar): Boolean {
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = today.timeInMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return isSameDay(yesterday, otherDate)
    }

    fun shouldShowDateHeader(currentIndex: Int): Boolean {
        val messages = _messages.value
        if (currentIndex == 0) return true

        val currentMessage = messages[currentIndex]
        val previousMessage = messages[currentIndex - 1]

        val currentDate = Calendar.getInstance().apply {
            timeInMillis = currentMessage.timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val previousDate = Calendar.getInstance().apply {
            timeInMillis = previousMessage.timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return currentDate.timeInMillis != previousDate.timeInMillis
    }

    fun isFromCurrentUser(message: ChatMessage): Boolean {
        return message.senderId == currentUserId
    }
}

sealed class ChatRoomUiState {
    object Loading : ChatRoomUiState()
    object Success : ChatRoomUiState()
    data class Error(val message: String) : ChatRoomUiState()
}