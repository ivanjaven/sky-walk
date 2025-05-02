package com.example.skywalk.features.auth.domain.usecases

import com.example.skywalk.features.auth.domain.models.User
import com.example.skywalk.features.auth.domain.repository.AuthRepository
import java.io.File

class UpdateProfileUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(
        displayName: String? = null,
        photoFile: File? = null
    ): Result<User> {
        return repository.updateProfile(displayName, photoFile)
    }
}