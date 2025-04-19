// com/example/skywalk/features/chat/domain/usecases/SearchUsersUseCase.kt
package com.example.skywalk.features.chat.domain.usecases

import com.example.skywalk.features.chat.domain.models.ChatUser
import com.example.skywalk.features.chat.domain.repository.ChatRepository

class SearchUsersUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(query: String): List<ChatUser> {
        return repository.searchUsers(query)
    }
}