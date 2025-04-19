// com/example/skywalk/features/chat/domain/usecases/GetUserByIdUseCase.kt
package com.example.skywalk.features.chat.domain.usecases

import com.example.skywalk.features.chat.domain.models.ChatUser
import com.example.skywalk.features.chat.domain.repository.ChatRepository

class GetUserByIdUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(userId: String): ChatUser? {
        return repository.getUserById(userId)
    }
}