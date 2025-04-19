// com/example/skywalk/features/chat/domain/usecases/SendTextMessageUseCase.kt
package com.example.skywalk.features.chat.domain.usecases

import com.example.skywalk.features.chat.domain.models.ChatMessage
import com.example.skywalk.features.chat.domain.repository.ChatRepository

class SendTextMessageUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(chatRoomId: String, content: String): Result<ChatMessage> {
        if (content.isBlank()) {
            return Result.failure(IllegalArgumentException("Message content cannot be empty"))
        }
        return repository.sendTextMessage(chatRoomId, content)
    }
}