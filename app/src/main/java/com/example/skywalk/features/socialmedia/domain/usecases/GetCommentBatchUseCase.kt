// Create a new file: GetCommentsBatchUseCase.kt
package com.example.skywalk.features.socialmedia.domain.usecases

import com.example.skywalk.features.socialmedia.domain.models.Comment
import com.example.skywalk.features.socialmedia.domain.repository.SocialMediaRepository

class GetCommentsBatchUseCase(
    private val repository: SocialMediaRepository
) {
    suspend operator fun invoke(postId: String, limit: Int = 10, lastCommentId: String? = null): Result<List<Comment>> {
        return repository.getCommentsBatch(postId, limit, lastCommentId)
    }
}