package com.example.skywalk.features.auth.data.remote

import android.net.Uri
import com.example.skywalk.features.auth.domain.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.File
import java.util.UUID

class FirebaseAuthService {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val userFirestoreService = UserFirestoreService()
    private val storage = FirebaseStorage.getInstance()

    // Create a MutableStateFlow to hold auth state and synchronize with AuthStateListener
    private val _authStateFlow = MutableStateFlow<Boolean>(auth.currentUser != null)

    val currentUser: Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val firebaseUser = auth.currentUser
            trySend(firebaseUser?.toUser())

            // Update our separate flow that's purely for auth state
            _authStateFlow.update { firebaseUser != null }

            // If a user just logged in, update their last login time
            firebaseUser?.let {
                launch {
                    userFirestoreService.updateLastLogin(it.uid)
                }
            }
        }

        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    val isAuthenticated: Flow<Boolean> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser != null)
        }

        // Immediately emit current state
        trySend(auth.currentUser != null)

        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<User> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user?.toUser() ?: throw Exception("Authentication failed")

            // Update the user's last login time in Firestore
            userFirestoreService.updateLastLogin(user.id)

            Result.success(user)
        } catch (e: Exception) {
            Timber.e(e, "Sign in with email failed")
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<User> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()

            // Update display name
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()

            result.user?.updateProfile(profileUpdates)?.await()

            val user = result.user?.toUser() ?: throw Exception("User creation failed")

            // Save the user to Firestore
            userFirestoreService.createOrUpdateUser(user)

            Result.success(user)
        } catch (e: Exception) {
            Timber.e(e, "Sign up with email failed")
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(idToken: String): Result<User> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user?.toUser() ?: throw Exception("Google sign in failed")

            // Save or update the user in Firestore
            userFirestoreService.createOrUpdateUser(user)

            Result.success(user)
        } catch (e: Exception) {
            Timber.e(e, "Sign in with Google failed")
            Result.failure(e)
        }
    }

    fun signOut(): Result<Unit> {
        return try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Sign out failed")
            Result.failure(e)
        }
    }

    fun isUserAuthenticated(): Boolean {
        return auth.currentUser != null
    }

    // New method to update profile
    suspend fun updateProfile(displayName: String? = null, photoFile: File? = null): Result<User> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))

            val profileUpdatesBuilder = UserProfileChangeRequest.Builder()

            // Update display name if provided
            if (!displayName.isNullOrBlank()) {
                profileUpdatesBuilder.setDisplayName(displayName)
            }

            // Upload and update photo if provided
            if (photoFile != null) {
                // Upload file to Firebase Storage
                val storageRef = storage.reference.child("profile_pictures/${currentUser.uid}/${UUID.randomUUID()}")
                storageRef.putFile(Uri.fromFile(photoFile)).await()

                // Get download URL
                val downloadUrl = storageRef.downloadUrl.await()

                // Set the photo URL in profile update
                profileUpdatesBuilder.setPhotoUri(downloadUrl)
            }

            // Apply the updates to Firebase Auth
            currentUser.updateProfile(profileUpdatesBuilder.build()).await()

            // Get the updated user
            val updatedUser = currentUser.toUser()

            // Update the user in Firestore
            userFirestoreService.createOrUpdateUser(updatedUser)

            Result.success(updatedUser)
        } catch (e: Exception) {
            Timber.e(e, "Update profile failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun FirebaseUser.toUser(): User {
        return User(
            id = uid,
            email = email ?: "",
            displayName = displayName,
            photoUrl = photoUrl?.toString(),
            isEmailVerified = isEmailVerified
        )
    }
}