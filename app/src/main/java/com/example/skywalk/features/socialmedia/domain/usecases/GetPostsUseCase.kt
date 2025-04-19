package com.example.skywalk.features.socialmedia.domain.usecases

import com.example.skywalk.features.socialmedia.domain.models.Post
import com.example.skywalk.features.socialmedia.domain.repository.SocialMediaRepository
import kotlinx.coroutines.flow.Flow

class GetPostsUseCase(
    private val repository: SocialMediaRepository
) {
    suspend operator fun invoke(limit: Int = 10, lastPostId: String? = null): Flow<List<Post>> {
        return repository.getPosts(limit, lastPostId)
    }
}