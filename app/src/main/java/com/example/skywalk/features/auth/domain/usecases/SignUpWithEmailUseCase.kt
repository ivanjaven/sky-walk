package com.example.skywalk.features.auth.domain.usecases

import com.example.skywalk.features.auth.domain.models.User
import com.example.skywalk.features.auth.domain.repository.AuthRepository

class SignUpWithEmailUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String, displayName: String): Result<User> {
        return repository.signUpWithEmail(email, password, displayName)
    }
}