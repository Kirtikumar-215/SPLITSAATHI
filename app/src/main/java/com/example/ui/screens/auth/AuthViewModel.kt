package com.example.ui.screens.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.auth.AuthRepository
import com.example.data.remote.auth.AuthResult
import com.example.data.remote.auth.AuthState
import com.example.data.remote.auth.GoogleAuthManager
import com.example.data.remote.auth.SupabaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State holder and controller for Authentication UI flows.
 */
class AuthViewModel(
    val authRepository: AuthRepository = SupabaseAuthRepository()
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthState.Uninitialized)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _isPasswordResetRequested = MutableStateFlow(false)
    val isPasswordResetRequested: StateFlow<Boolean> = _isPasswordResetRequested.asStateFlow()

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    fun signInWithEmail(email: String, pass: String, onSuccess: () -> Unit = {}) {
        if (email.isBlank() || pass.isBlank()) {
            _errorMessage.value = "Please enter both email and password."
            return
        }
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            val result = authRepository.signInWithEmail(email, pass)
            _isLoading.value = false
            if (result.isSuccess) {
                _successMessage.value = "Welcome back!"
                onSuccess()
            } else {
                _errorMessage.value = (result as? AuthResult.Error)?.message ?: "Sign in failed"
            }
        }
    }

    fun signUpWithEmail(name: String, email: String, pass: String, confirmPass: String, onSuccess: () -> Unit = {}) {
        if (name.isBlank()) {
            _errorMessage.value = "Please enter your name."
            return
        }
        if (email.isBlank() || !email.contains("@")) {
            _errorMessage.value = "Please enter a valid email address."
            return
        }
        if (pass.length < 6) {
            _errorMessage.value = "Password must be at least 6 characters long."
            return
        }
        if (pass != confirmPass) {
            _errorMessage.value = "Passwords do not match."
            return
        }

        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            val result = authRepository.signUpWithEmail(email, pass, name)
            _isLoading.value = false
            if (result.isSuccess) {
                _successMessage.value = "Account created successfully! Check your email to verify if required."
                onSuccess()
            } else {
                _errorMessage.value = (result as? AuthResult.Error)?.message ?: "Registration failed"
            }
        }
    }

    fun signInWithGoogle(context: Context, onSuccess: () -> Unit = {}) {
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            val googleResult = GoogleAuthManager.getGoogleIdToken(context)
            if (googleResult.isSuccess) {
                val (idToken, rawNonce) = googleResult.getOrNull()!!
                val result = authRepository.signInWithGoogleIdToken(idToken, rawNonce)
                _isLoading.value = false
                if (result.isSuccess) {
                    _successMessage.value = "Signed in with Google!"
                    onSuccess()
                } else {
                    _errorMessage.value = (result as? AuthResult.Error)?.message ?: "Google sign-in with Supabase failed."
                }
            } else {
                _isLoading.value = false
                _errorMessage.value = (googleResult as? AuthResult.Error)?.message ?: "Google sign-in cancelled or failed."
            }
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank() || !email.contains("@")) {
            _errorMessage.value = "Please enter a valid email address."
            return
        }
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            val result = authRepository.resetPasswordForEmail(email)
            _isLoading.value = false
            if (result.isSuccess) {
                _isPasswordResetRequested.value = true
                _successMessage.value = "Password reset link sent to $email. Please check your inbox."
            } else {
                _errorMessage.value = (result as? AuthResult.Error)?.message ?: "Failed to send reset email."
            }
        }
    }

    fun updatePassword(newPassword: String, confirmPassword: String, onSuccess: () -> Unit = {}) {
        if (newPassword.length < 6) {
            _errorMessage.value = "Password must be at least 6 characters."
            return
        }
        if (newPassword != confirmPassword) {
            _errorMessage.value = "Passwords do not match."
            return
        }
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            val result = authRepository.updateUserPassword(newPassword)
            _isLoading.value = false
            if (result.isSuccess) {
                _successMessage.value = "Password updated successfully!"
                onSuccess()
            } else {
                _errorMessage.value = (result as? AuthResult.Error)?.message ?: "Failed to update password."
            }
        }
    }

    fun signOut(onSuccess: () -> Unit = {}) {
        _isLoading.value = true
        viewModelScope.launch {
            authRepository.signOut()
            _isLoading.value = false
            _successMessage.value = "Signed out of cloud account."
            onSuccess()
        }
    }
}
