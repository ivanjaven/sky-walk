// com/example/skywalk/features/chat/presentation/viewmodel/ChatRoomViewModel.kt
package com.example.skywalk.features.chat.presentation.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.skywalk.features.chat.data.repository.ChatRepositoryImpl
import com.example.skywalk.features.chat.domain.models.ChatMessage
import com.example.skywalk.features.chat.domain.models.ChatUser
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

    // Current user ID
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

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

        if (text.isBlank() && imageUri == null) {
            return
        }

        _isSending.value = true

        viewModelScope.launch {
            try {
                // Clear input first for immediate feedback
                _messageText.value = ""
                _selectedImage.value = null

                // Send message(s)
                if (text.isNotBlank()) {
                    val result = sendTextMessageUseCase(chatRoomId, text)
                    result.onFailure { error ->
                        Timber.e(error, "Error sending text message")
                        _uiState.value = ChatRoomUiState.Error("Failed to send message. Please try again.")
                    }
                }

                if (imageUri != null) {
                    // Convert Uri to File
                    val context = getApplication<Application>()
                    val tempFile = File(context.cacheDir, "temp_message_image_${System.currentTimeMillis()}.jpg")

                    context.contentResolver.openInputStream(imageUri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    val result = sendImageMessageUseCase(chatRoomId, tempFile)
                    result.onFailure { error ->
                        Timber.e(error, "Error sending image message")
                        _uiState.value = ChatRoomUiState.Error("Failed to send image. Please try again.")
                    }

                    // Clean up temp file
                    tempFile.delete()
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
                markMessagesAsReadUseCase(chatRoomId)
            } catch (e: Exception) {
                Timber.e(e, "Error marking messages as read")
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