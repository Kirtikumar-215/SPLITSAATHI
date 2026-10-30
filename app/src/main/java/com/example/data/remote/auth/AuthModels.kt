package com.example.data.remote.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Authentication states for SplitSaathi Supabase integration.
 */
sealed class AuthState {
    object Uninitialized : AuthState()
    object Loading : AuthState()
    object SignedOut : AuthState()
    data class Authenticated(
        val userId: String,
        val email: String?,
        val isEmailVerified: Boolean,
        val provider: String = "email"
    ) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * Result wrapper for auth operations.
 */
sealed class AuthResult<out T> {
    data class Success<out T>(val data: T) : AuthResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : AuthResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data
    fun exceptionOrNull(): Throwable? = (this as? Error)?.cause
}

/**
 * DTO matching public.profiles schema for initial profile synchronization.
 */
@Serializable
data class CloudProfileDto(
    val id: String,
    @SerialName("display_name")
    val displayName: String,
    val email: String? = null,
    val phone: String? = null,
    @SerialName("avatar_key")
    val avatarKey: String = "avatar_1",
    @SerialName("upi_id")
    val upiId: String? = null,
    @SerialName("upi_consent")
    val upiConsent: Boolean = false
)
