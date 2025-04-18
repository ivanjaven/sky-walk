package com.example.skywalk.features.auth.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.skywalk.features.auth.data.repository.AuthRepositoryImpl
import com.example.skywalk.features.auth.domain.models.User
import com.example.skywalk.features.auth.domain.usecases.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    // Repository and use cases
    private val repository = AuthRepositoryImpl()
    private val signInWithEmailUseCase = SignInWithEmailUseCase(repository)
    private val signUpWithEmailUseCase = SignUpWithEmailUseCase(repository)
    private val signInWithGoogleUseCase = SignInWithGoogleUseCase(repository)
    private val signOutUseCase = SignOutUseCase(repository)
    private val getCurrentUserUseCase = GetCurrentUserUseCase(repository)
    private val isUserAuthenticatedUseCase = IsUserAuthenticatedUseCase(repository)

    // UI state
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState

    // Current user
    val currentUser: Flow<User?> = getCurrentUserUseCase()

    // Form values
    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName

    private val _emailError = MutableStateFlow<String?>(null)
    val emailError: StateFlow<String?> = _emailError

    private val _passwordError = MutableStateFlow<String?>(null)
    val passwordError: StateFlow<String?> = _passwordError

    private val _displayNameError = MutableStateFlow<String?>(null)
    val displayNameError: StateFlow<String?> = _displayNameError

    init {
        checkAuthState()
    }

    fun onEmailChange(newEmail: String) {
        _email.value = newEmail
        validateEmail()
    }

    fun onPasswordChange(newPassword: String) {
        _password.value = newPassword
        validatePassword()
    }

    fun onDisplayNameChange(newDisplayName: String) {
        _displayName.value = newDisplayName
        validateDisplayName()
    }

    private fun validateEmail(): Boolean {
        return if (_email.value.isEmpty()) {
            _emailError.value = "Email cannot be empty"
            false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(_email.value).matches()) {
            _emailError.value = "Invalid email format"
            false
        } else {
            _emailError.value = null
            true
        }
    }

    private fun validatePassword(): Boolean {
        return if (_password.value.isEmpty()) {
            _passwordError.value = "Password cannot be empty"
            false
        } else if (_password.value.length < 6) {
            _passwordError.value = "Password must be at least 6 characters"
            false
        } else {
            _passwordError.value = null
            true
        }
    }

    private fun validateDisplayName(): Boolean {
        return if (_displayName.value.isEmpty()) {
            _displayNameError.value = "Name cannot be empty"
            false
        } else {
            _displayNameError.value = null
            true
        }
    }

    fun signInWithEmail() {
        if (!validateEmail() || !validatePassword()) {
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = signInWithEmailUseCase(_email.value, _password.value)
            result.fold(
                onSuccess = {
                    _authState.value = AuthState.Authenticated
                    clearInputs()
                },
                onFailure = { error ->
                    _authState.value = AuthState.Error(error.message ?: "Authentication failed")
                    Timber.e(error, "Sign in failed")
                }
            )
        }
    }

    fun signUpWithEmail() {
        if (!validateEmail() || !validatePassword() || !validateDisplayName()) {
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = signUpWithEmailUseCase(_email.value, _password.value, _displayName.value)
            result.fold(
                onSuccess = {
                    _authState.value = AuthState.Authenticated
                    clearInputs()
                },
                onFailure = { error ->
                    _authState.value = AuthState.Error(error.message ?: "Registration failed")
                    Timber.e(error, "Sign up failed")
                }
            )
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = signInWithGoogleUseCase(idToken)
            result.fold(
                onSuccess = {
                    _authState.value = AuthState.Authenticated
                },
                onFailure = { error ->
                    _authState.value = AuthState.Error(error.message ?: "Google sign in failed")
                    Timber.e(error, "Google sign in failed")
                }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            try {
                // Force clear any cached auth state
                _authState.value = AuthState.Unauthenticated

                // Call the sign out use case
                val result = signOutUseCase()

                if (result.isFailure) {
                    Timber.e(result.exceptionOrNull(), "Sign out failed")
                }

                // Add a delay to ensure all Firebase listeners have time to respond
                kotlinx.coroutines.delay(100)

                // Force check the auth state again
                checkAuthState()
            } catch (e: Exception) {
                Timber.e(e, "Error during sign out")
                _authState.value = AuthState.Error("Error signing out")

                // Still try to check auth state
                checkAuthState()
            }
        }
    }

    fun checkAuthState() {
        viewModelScope.launch {
            val isAuthenticated = isUserAuthenticatedUseCase()
            _authState.value = if (isAuthenticated) AuthState.Authenticated else AuthState.Unauthenticated
        }
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Unauthenticated
        }
    }

    private fun clearInputs() {
        _email.value = ""
        _password.value = ""
        _displayName.value = ""
        _emailError.value = null
        _passwordError.value = null
        _displayNameError.value = null
    }
}

sealed class AuthState {
    object Initial : AuthState()
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    object Authenticated : AuthState()
    data class Error(val message: String) : AuthState()
}