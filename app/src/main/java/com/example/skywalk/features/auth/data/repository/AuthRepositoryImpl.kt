package com.example.skywalk.features.auth.data.repository

import com.example.skywalk.features.auth.data.remote.FirebaseAuthService
import com.example.skywalk.features.auth.domain.models.User
import com.example.skywalk.features.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import java.io.File

class AuthRepositoryImpl : AuthRepository {
    private val firebaseAuthService = FirebaseAuthService()

    override val currentUser: Flow<User?>
        get() = firebaseAuthService.currentUser

    override suspend fun signInWithEmail(email: String, password: String): Result<User> {
        return firebaseAuthService.signInWithEmail(email, password)
    }

    override suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<User> {
        return firebaseAuthService.signUpWithEmail(email, password, displayName)
    }

    override suspend fun signInWithGoogle(idToken: String): Result<User> {
        return firebaseAuthService.signInWithGoogle(idToken)
    }

    override suspend fun signOut(): Result<Unit> {
        return firebaseAuthService.signOut()
    }

    override suspend fun isUserAuthenticated(): Boolean {
        return firebaseAuthService.isUserAuthenticated()
    }

    // Implement the new updateProfile method
    override suspend fun updateProfile(displayName: String?, photoFile: File?): Result<User> {
        return firebaseAuthService.updateProfile(displayName, photoFile)
    }
}