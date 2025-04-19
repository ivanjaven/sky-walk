// com/example/skywalk/features/socialmedia/data/repository/SocialMediaRepositoryImpl.kt
package com.example.skywalk.features.socialmedia.data.repository

import com.example.skywalk.features.auth.data.remote.FirebaseAuthService
import com.example.skywalk.features.socialmedia.data.remote.FirebaseSocialService
import com.example.skywalk.features.socialmedia.domain.models.Comment
import com.example.skywalk.features.socialmedia.domain.models.Like
import com.example.skywalk.features.socialmedia.domain.models.Post
import com.example.skywalk.features.socialmedia.domain.repository.SocialMediaRepository
import kotlinx.coroutines.flow.Flow
import java.io.File

class SocialMediaRepositoryImpl : SocialMediaRepository {

    private val firebaseSocialService = FirebaseSocialService()
    private val authService = FirebaseAuthService()

    override suspend fun getPosts(limit: Int, lastPostId: String?): Flow<List<Post>> {
        return firebaseSocialService.getPosts(limit, lastPostId)
    }

    override suspend fun getPostById(postId: String): Flow<Post?> {
        return firebaseSocialService.getPostById(postId)
    }

    override suspend fun createPost(content: String, imageFiles: List<File>?): Result<Post> {
        return firebaseSocialService.createPost(content, imageFiles)
    }

    override suspend fun deletePost(postId: String): Result<Unit> {
        return firebaseSocialService.deletePost(postId)
    }

    override suspend fun likePost(postId: String): Result<Unit> {
        return firebaseSocialService.likePost(postId)
    }

    override suspend fun unlikePost(postId: String): Result<Unit> {
        return firebaseSocialService.unlikePost(postId)
    }

    override suspend fun getLikes(postId: String): Flow<List<Like>> {
        return firebaseSocialService.getLikes(postId)
    }

    override suspend fun getComments(postId: String): Flow<List<Comment>> {
        return firebaseSocialService.getComments(postId)
    }

    override suspend fun addComment(postId: String, content: String): Result<Comment> {
        return firebaseSocialService.addComment(postId, content)
    }

    override suspend fun deleteComment(commentId: String): Result<Unit> {
        return firebaseSocialService.deleteComment(commentId)
    }
}