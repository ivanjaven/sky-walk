package com.example.skywalk.features.auth.domain.usecases

import com.example.skywalk.features.auth.domain.models.User
import com.example.skywalk.features.auth.domain.repository.AuthRepository

class SignInWithGoogleUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(idToken: String): Result<User> {
        return repository.signInWithGoogle(idToken)
    }
}