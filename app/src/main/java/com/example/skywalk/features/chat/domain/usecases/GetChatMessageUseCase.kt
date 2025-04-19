// com/example/skywalk/features/chat/domain/usecases/GetChatMessagesUseCase.kt
package com.example.skywalk.features.chat.domain.usecases

import com.example.skywalk.features.chat.domain.models.ChatMessage
import com.example.skywalk.features.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

class GetChatMessagesUseCase(private val repository: ChatRepository) {
    operator fun invoke(chatRoomId: String): Flow<List<ChatMessage>> {
        return repository.getChatMessages(chatRoomId)
    }
}