package com.example.skywalk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skywalk.core.ui.screens.MainScreen
import com.example.skywalk.core.ui.theme.SkyWalkTheme
import com.example.skywalk.features.auth.presentation.screens.AuthScreen
import com.example.skywalk.features.auth.presentation.viewmodel.AuthState
import com.example.skywalk.features.auth.presentation.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import timber.log.Timber

class MainActivity : ComponentActivity() {
    private val firebaseAuth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()

            // Log auth state changes for debugging
            LaunchedEffect(authState) {
                Timber.d("Auth state changed to: $authState")
            }

            // Add a Firebase AuthStateListener directly in MainActivity for redundancy
            LaunchedEffect(Unit) {
                val authStateListener = FirebaseAuth.AuthStateListener { auth ->
                    val user = auth.currentUser
                    if (user == null) {
                        Timber.d("Firebase AuthStateListener: User is signed out")
                        // Force a check of the auth state
                        authViewModel.checkAuthState()
                    } else {
                        Timber.d("Firebase AuthStateListener: User is signed in")
                    }
                }

                firebaseAuth.addAuthStateListener(authStateListener)
            }

            SkyWalkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (authState) {
                        is AuthState.Authenticated -> {
                            MainScreen(
                                onSignOut = {
                                    // This provides a redundant way to sign out
                                    authViewModel.signOut()
                                }
                            )
                        }
                        else -> {
                            AuthScreen(
                                onAuthenticated = { /* Navigation handled by state change */ },
                                googleSignInClient = googleSignInClient
                            )
                        }
                    }
                }
            }
        }
    }
}