package com.example.skywalk.features.socialmedia.domain.usecases

import com.example.skywalk.features.socialmedia.domain.repository.SocialMediaRepository

class LikePostUseCase(
    private val repository: SocialMediaRepository
) {
    suspend operator fun invoke(postId: String): Result<Unit> {
        return repository.likePost(postId)
    }
}