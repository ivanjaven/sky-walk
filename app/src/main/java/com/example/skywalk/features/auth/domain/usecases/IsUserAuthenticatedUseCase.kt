package com.example.skywalk.features.auth.domain.usecases

import com.example.skywalk.features.auth.domain.repository.AuthRepository

class IsUserAuthenticatedUseCase(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(): Boolean {
        return repository.isUserAuthenticated()
    }
}