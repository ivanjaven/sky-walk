package com.example.skywalk.features.auth.domain.repository

import com.example.skywalk.features.auth.domain.models.User
import kotlinx.coroutines.flow.Flow
import java.io.File

interface AuthRepository {
    val currentUser: Flow<User?>

    suspend fun signInWithEmail(email: String, password: String): Result<User>
    suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<User>
    suspend fun signInWithGoogle(idToken: String): Result<User>
    suspend fun signOut(): Result<Unit>
    suspend fun isUserAuthenticated(): Boolean

    // New method for updating profile
    suspend fun updateProfile(displayName: String? = null, photoFile: File? = null): Result<User>
}