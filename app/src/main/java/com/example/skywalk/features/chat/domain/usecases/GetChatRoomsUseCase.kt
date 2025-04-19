// com/example/skywalk/features/chat/domain/usecases/GetChatRoomsUseCase.kt
package com.example.skywalk.features.chat.domain.usecases

import com.example.skywalk.features.chat.domain.models.ChatRoom
import com.example.skywalk.features.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

class GetChatRoomsUseCase(private val repository: ChatRepository) {
    operator fun invoke(): Flow<List<ChatRoom>> {
        return repository.getChatRooms()
    }
}