package com.example.skywalk.features.auth.presentation.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.skywalk.features.auth.presentation.viewmodel.AuthState
import com.example.skywalk.features.auth.presentation.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import timber.log.Timber

@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    googleSignInClient: GoogleSignInClient
) {
    val authNavController = rememberNavController()
    val viewModel: AuthViewModel = viewModel()
    val authState by viewModel.authState.collectAsState()

    // Google Sign-in result handling
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account?.idToken?.let { idToken ->
                    viewModel.signInWithGoogle(idToken)
                }
            } catch (e: ApiException) {
                Timber.e(e, "Google sign in failed")
            }
        }
    }

    // Handle authentication state changes
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                onAuthenticated()
            }
            else -> { /* Do nothing */ }
        }
    }

    // Auth navigation
    NavHost(
        navController = authNavController,
        startDestination = "login"
    ) {
        composable("login") {
            LoginScreen(
                viewModel = viewModel,
                onNavigateToRegister = { authNavController.navigate("register") },
                onGoogleSignInClick = {
                    val signInIntent = googleSignInClient.signInIntent
                    googleSignInLauncher.launch(signInIntent)
                }
            )
        }

        composable("register") {
            RegisterScreen(
                viewModel = viewModel,
                onNavigateToLogin = { authNavController.navigate("login") }
            )
        }
    }
}