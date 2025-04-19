// com/example/skywalk/features/chat/domain/usecases/MarkMessagesAsReadUseCase.kt
package com.example.skywalk.features.chat.domain.usecases

import com.example.skywalk.features.chat.domain.repository.ChatRepository

class MarkMessagesAsReadUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(chatRoomId: String): Result<Unit> {
        return repository.markMessagesAsRead(chatRoomId)
    }
}