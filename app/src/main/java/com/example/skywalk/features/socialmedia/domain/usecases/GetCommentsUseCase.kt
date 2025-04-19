package com.example.skywalk.features.socialmedia.domain.usecases

import com.example.skywalk.features.socialmedia.domain.models.Comment
import com.example.skywalk.features.socialmedia.domain.repository.SocialMediaRepository
import kotlinx.coroutines.flow.Flow

class GetCommentsUseCase(
    private val repository: SocialMediaRepository
) {
    suspend operator fun invoke(postId: String): Flow<List<Comment>> {
        return repository.getComments(postId)
    }
}