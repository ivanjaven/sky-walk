// com/example/skywalk/features/socialmedia/domain/usecases/CreatePostUseCase.kt
package com.example.skywalk.features.socialmedia.domain.usecases

import com.example.skywalk.features.socialmedia.domain.models.Post
import com.example.skywalk.features.socialmedia.domain.repository.SocialMediaRepository
import java.io.File

class CreatePostUseCase(
    private val repository: SocialMediaRepository
) {
    suspend operator fun invoke(content: String, imageFiles: List<File>? = null): Result<Post> {
        if (content.isBlank() && (imageFiles == null || imageFiles.isEmpty())) {
            return Result.failure(IllegalArgumentException("Post must have content or at least one image"))
        }

        return repository.createPost(content, imageFiles)
    }
}