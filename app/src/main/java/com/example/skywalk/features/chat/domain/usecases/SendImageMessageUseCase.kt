// com/example/skywalk/features/chat/domain/usecases/SendImageMessageUseCase.kt
package com.example.skywalk.features.chat.domain.usecases

import com.example.skywalk.features.chat.domain.models.ChatMessage
import com.example.skywalk.features.chat.domain.repository.ChatRepository
import java.io.File

class SendImageMessageUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(chatRoomId: String, imageFile: File): Result<ChatMessage> {
        if (!imageFile.exists()) {
            return Result.failure(IllegalArgumentException("Image file does not exist"))
        }
        return repository.sendImageMessage(chatRoomId, imageFile)
    }
}