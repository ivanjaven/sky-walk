// com/example/skywalk/features/socialmedia/domain/models/Like.kt
package com.example.skywalk.features.socialmedia.domain.models

import java.util.Date

data class Like(
    val id: String = "",
    val postId: String = "",
    val userId: String = "",
    val userName: String = "",
    val createdAt: Date = Date()
)