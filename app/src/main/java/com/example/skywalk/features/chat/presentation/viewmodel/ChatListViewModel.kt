// com/example/skywalk/features/chat/presentation/viewmodel/ChatListViewModel.kt
package com.example.skywalk.features.chat.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.skywalk.features.chat.data.repository.ChatRepositoryImpl
import com.example.skywalk.features.chat.domain.models.ChatRoom
import com.example.skywalk.features.chat.domain.models.ChatUser
import com.example.skywalk.features.chat.domain.usecases.GetChatRoomsUseCase
import com.example.skywalk.features.chat.domain.usecases.GetOrCreateChatRoomUseCase
import com.example.skywalk.features.chat.domain.usecases.SearchUsersUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

class ChatListViewModel(application: Application) : AndroidViewModel(application) {
    // Repository and use cases
    private val repository = ChatRepositoryImpl()
    private val getChatRoomsUseCase = GetChatRoomsUseCase(repository)
    private val searchUsersUseCase = SearchUsersUseCase(repository)
    private val getOrCreateChatRoomUseCase = GetOrCreateChatRoomUseCase(repository)

    // UI state
    private val _uiState = MutableStateFlow<ChatListUiState>(ChatListUiState.Loading)
    val uiState: StateFlow<ChatListUiState> = _uiState

    // Chat rooms
    private val _chatRooms = MutableStateFlow<List<ChatRoom>>(emptyList())
    val chatRooms: StateFlow<List<ChatRoom>> = _chatRooms

    // Search results
    private val _searchResults = MutableStateFlow<List<ChatUser>>(emptyList())
    val searchResults: StateFlow<List<ChatUser>> = _searchResults

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Is searching
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    // Total unread count for badge
    private val _totalUnreadCount = MutableStateFlow(0)
    val totalUnreadCount: StateFlow<Int> = _totalUnreadCount

    init {
        loadChatRooms()
    }

    private fun loadChatRooms() {
        viewModelScope.launch {
            _uiState.value = ChatListUiState.Loading

            try {
                getChatRoomsUseCase()
                    .catch { e ->
                        Timber.e(e, "Error loading chat rooms")
                        _uiState.value = ChatListUiState.Error(e.message ?: "Failed to load chat rooms")
                    }
                    .collect { rooms ->
                        _chatRooms.value = rooms
                        _totalUnreadCount.value = rooms.sumOf { it.unreadCount }
                        _uiState.value = ChatListUiState.Success
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error in loadChatRooms")
                _uiState.value = ChatListUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query

        if (query.isBlank()) {
            _isSearching.value = false
            _searchResults.value = emptyList()
        } else {
            _isSearching.value = true
            searchUsers(query)
        }
    }

    private fun searchUsers(query: String) {
        viewModelScope.launch {
            try {
                val users = searchUsersUseCase(query)
                _searchResults.value = users
            } catch (e: Exception) {
                Timber.e(e, "Error searching users")
                _searchResults.value = emptyList()
            }
        }
    }

    fun startChat(userId: String, onNavigate: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val result = getOrCreateChatRoomUseCase(userId)
                result.fold(
                    onSuccess = { chatRoom ->
                        onNavigate(chatRoom.id)
                    },
                    onFailure = { error ->
                        Timber.e(error, "Error starting chat")
                        _uiState.value = ChatListUiState.Error("Failed to start chat. Please try again.")
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Error in startChat")
                _uiState.value = ChatListUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _isSearching.value = false
        _searchResults.value = emptyList()
    }

    fun formatTimestamp(timestamp: Long): String {
        val now = Calendar.getInstance().time
        val messageTime = Date(timestamp)
        val diffInMillis = now.time - messageTime.time
        val diffInMinutes = diffInMillis / (60 * 1000)
        val diffInHours = diffInMinutes / 60
        val diffInDays = diffInHours / 24

        return when {
            diffInMinutes < 1 -> "Just now"
            diffInMinutes < 60 -> "${diffInMinutes}m ago"
            diffInHours < 24 -> "${diffInHours}h ago"
            diffInDays < 7 -> "${diffInDays}d ago"
            else -> {
                val dateFormat = if (diffInDays < 365) {
                    SimpleDateFormat("MMM d", Locale.getDefault())
                } else {
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                }
                dateFormat.format(messageTime)
            }
        }
    }
}

sealed class ChatListUiState {
    object Loading : ChatListUiState()
    object Success : ChatListUiState()
    data class Error(val message: String) : ChatListUiState()
}