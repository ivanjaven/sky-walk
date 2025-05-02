package com.example.skywalk.features.socialmedia.domain.usecases

import com.example.skywalk.features.socialmedia.domain.repository.SocialMediaRepository

class DeletePostUseCase(
    private val repository: SocialMediaRepository
) {
    suspend operator fun invoke(postId: String): Result<Unit> {
        if (postId.isBlank()) {
            return Result.failure(IllegalArgumentException("Post ID cannot be empty"))
        }

        return repository.deletePost(postId)
    }
}