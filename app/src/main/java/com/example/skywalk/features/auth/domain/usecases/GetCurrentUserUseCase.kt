package com.example.skywalk.features.auth.domain.usecases

import com.example.skywalk.features.auth.domain.models.User
import com.example.skywalk.features.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

class GetCurrentUserUseCase(
    private val repository: AuthRepository
) {
    operator fun invoke(): Flow<User?> {
        return repository.currentUser
    }
}