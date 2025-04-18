package com.example.skywalk.features.auth.data.remote

import com.example.skywalk.features.auth.domain.models.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class UserFirestoreService {
    private val firestore = FirebaseFirestore.getInstance()
    private val userCollection = firestore.collection("users")

    suspend fun createOrUpdateUser(user: User): Result<User> {
        return try {
            val userData = hashMapOf(
                "id" to user.id,
                "email" to user.email,
                "displayName" to user.displayName,
                "photoUrl" to user.photoUrl,
                "isEmailVerified" to user.isEmailVerified,
                "createdAt" to System.currentTimeMillis(),
                "lastLoginAt" to System.currentTimeMillis()
            )

            userCollection.document(user.id).set(userData).await()
            Result.success(user)
        } catch (e: Exception) {
            Timber.e(e, "Error creating/updating user in Firestore")
            Result.failure(e)
        }
    }

    suspend fun getUserData(userId: String): Result<Map<String, Any>?> {
        return try {
            val document = userCollection.document(userId).get().await()
            if (document.exists()) {
                Result.success(document.data)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting user data from Firestore")
            Result.failure(e)
        }
    }

    suspend fun updateLastLogin(userId: String) {
        try {
            userCollection.document(userId)
                .update("lastLoginAt", System.currentTimeMillis())
                .await()
        } catch (e: Exception) {
            Timber.e(e, "Error updating last login time")
            // Non-critical error, we can just log it
        }
    }
}