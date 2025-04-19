// com/example/skywalk/features/socialmedia/domain/models/Comment.kt
package com.example.skywalk.features.socialmedia.domain.models

import java.util.Date

data class Comment(
    val id: String = "",
    val postId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String? = null,
    val content: String = "",
    val createdAt: Date = Date(),
    val likeCount: Int = 0,
    val isLikedByCurrentUser: Boolean = false
)