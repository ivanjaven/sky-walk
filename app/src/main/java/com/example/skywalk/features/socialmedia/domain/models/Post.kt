// com/example/skywalk/features/socialmedia/domain/models/Post.kt
package com.example.skywalk.features.socialmedia.domain.models

import java.util.Date

data class Post(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String? = null,
    val content: String = "",
    val imageUrls: List<String>? = null,
    val location: String? = null,
    val createdAt: Date = Date(),
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val isLikedByCurrentUser: Boolean = false,
    val likeUsernames: List<String> = emptyList(),
    val likeUserIds: List<String> = emptyList()
)