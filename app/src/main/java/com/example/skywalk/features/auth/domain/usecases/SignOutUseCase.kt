package com.example.skywalk.features.auth.domain.usecases

import com.example.skywalk.features.auth.domain.repository.AuthRepository

class SignOutUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return repository.signOut()
    }
}