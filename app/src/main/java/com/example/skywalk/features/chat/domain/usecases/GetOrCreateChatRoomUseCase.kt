// com/example/skywalk/features/chat/domain/usecases/GetOrCreateChatRoomUseCase.kt
package com.example.skywalk.features.chat.domain.usecases

import com.example.skywalk.features.chat.domain.models.ChatRoom
import com.example.skywalk.features.chat.domain.repository.ChatRepository

class GetOrCreateChatRoomUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(otherUserId: String): Result<ChatRoom> {
        return repository.getOrCreateChatRoom(otherUserId)
    }
}