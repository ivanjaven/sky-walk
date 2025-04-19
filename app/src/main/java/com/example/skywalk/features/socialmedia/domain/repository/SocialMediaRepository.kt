// com/example/skywalk/features/socialmedia/domain/repository/SocialMediaRepository.kt
package com.example.skywalk.features.socialmedia.domain.repository

import com.example.skywalk.features.socialmedia.domain.models.Comment
import com.example.skywalk.features.socialmedia.domain.models.Like
import com.example.skywalk.features.socialmedia.domain.models.Post
import kotlinx.coroutines.flow.Flow
import java.io.File

interface SocialMediaRepository {
    // Posts
    suspend fun getPosts(limit: Int, lastPostId: String? = null): Flow<List<Post>>
    suspend fun getPostById(postId: String): Flow<Post?>
    suspend fun createPost(content: String, imageFiles: List<File>?): Result<Post>
    suspend fun deletePost(postId: String): Result<Unit>

    // Likes
    suspend fun likePost(postId: String): Result<Unit>
    suspend fun unlikePost(postId: String): Result<Unit>
    suspend fun getLikes(postId: String): Flow<List<Like>>

    // Comments
    suspend fun getComments(postId: String): Flow<List<Comment>>
    suspend fun addComment(postId: String, content: String): Result<Comment>
    suspend fun deleteComment(commentId: String): Result<Unit>
}