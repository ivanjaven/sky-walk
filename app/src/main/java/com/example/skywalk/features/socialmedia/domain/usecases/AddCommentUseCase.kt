package com.example.skywalk.features.socialmedia.domain.usecases

import com.example.skywalk.features.socialmedia.domain.models.Comment
import com.example.skywalk.features.socialmedia.domain.repository.SocialMediaRepository

class AddCommentUseCase(
    private val repository: SocialMediaRepository
) {
    suspend operator fun invoke(postId: String, content: String): Result<Comment> {
        if (content.isBlank()) {
            return Result.failure(IllegalArgumentException("Comment cannot be empty"))
        }

        return repository.addComment(postId, content)
    }
}