package com.example.data.remote.auth

import android.util.Log
import com.example.data.remote.SupabaseClientManager
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Repository interface for Supabase Authentication operations.
 */
interface AuthRepository {
    val authState: StateFlow<AuthState>
    val currentUserId: String?
    val currentUserEmail: String?

    suspend fun signUpWithEmail(email: String, password: String, displayName: String): AuthResult<AuthState.Authenticated>
    suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthState.Authenticated>
    suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String? = null): AuthResult<AuthState.Authenticated>
    suspend fun signOut(): AuthResult<Unit>
    suspend fun resetPasswordForEmail(email: String, redirectUrl: String? = "splitsaathi://reset-password"): AuthResult<Unit>
    suspend fun updateUserPassword(newPassword: String): AuthResult<Unit>
    suspend fun ensureCloudProfileExists(displayName: String, email: String?): AuthResult<Unit>
    suspend fun refreshSession(): AuthState
}

/**
 * Concrete implementation using Supabase Kotlin SDK.
 * Safely handles unconfigured / offline states without crashing.
 */
class SupabaseAuthRepository(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Uninitialized)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUserId: String?
        get() = when (val state = _authState.value) {
            is AuthState.Authenticated -> state.userId
            else -> SupabaseClientManager.clientOrNull?.auth?.currentUserOrNull()?.id
        }

    override val currentUserEmail: String?
        get() = when (val state = _authState.value) {
            is AuthState.Authenticated -> state.email
            else -> SupabaseClientManager.clientOrNull?.auth?.currentUserOrNull()?.email
        }

    init {
        observeSession()
    }

    private fun observeSession() {
        val client = SupabaseClientManager.clientOrNull
        if (client == null) {
            _authState.value = AuthState.SignedOut
            return
        }

        scope.launch {
            try {
                client.auth.sessionStatus.collect { status ->
                    when (status) {
                        is SessionStatus.Authenticated -> {
                            val user = client.auth.currentUserOrNull()
                            if (user != null) {
                                val isVerified = user.emailConfirmedAt != null
                                _authState.value = AuthState.Authenticated(
                                    userId = user.id,
                                    email = user.email,
                                    isEmailVerified = isVerified,
                                    provider = user.appMetadata?.get("provider")?.toString() ?: "email"
                                )
                            } else {
                                _authState.value = AuthState.SignedOut
                            }
                        }
                        is SessionStatus.NotAuthenticated -> {
                            _authState.value = AuthState.SignedOut
                        }
                        is SessionStatus.Initializing -> {
                            _authState.value = AuthState.Loading
                        }
                        else -> {
                            // Other statuses (RefreshFailure, etc.)
                            val user = client.auth.currentUserOrNull()
                            if (user != null) {
                                _authState.value = AuthState.Authenticated(
                                    userId = user.id,
                                    email = user.email,
                                    isEmailVerified = user.emailConfirmedAt != null
                                )
                            } else {
                                _authState.value = AuthState.SignedOut
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                _authState.value = AuthState.SignedOut
            }
        }
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): AuthResult<AuthState.Authenticated> {
        val client = SupabaseClientManager.clientOrNull
            ?: return AuthResult.Error("Supabase is not configured. Running in offline/local mode.")

        return try {
            client.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
                data = buildJsonObject {
                    put("display_name", displayName.trim())
                }
            }

            val user = client.auth.currentUserOrNull()
            if (user != null) {
                // Ensure cloud profile is populated
                ensureCloudProfileExists(displayName.trim().ifEmpty { "User" }, email.trim())

                val authSuccess = AuthState.Authenticated(
                    userId = user.id,
                    email = user.email,
                    isEmailVerified = user.emailConfirmedAt != null,
                    provider = "email"
                )
                _authState.value = authSuccess
                AuthResult.Success(authSuccess)
            } else {
                AuthResult.Success(
                    AuthState.Authenticated(
                        userId = "",
                        email = email.trim(),
                        isEmailVerified = false,
                        provider = "email"
                    )
                )
            }
        } catch (e: Throwable) {
            val friendlyMsg = mapAuthErrorMessage(e)
            AuthResult.Error(friendlyMsg, e)
        }
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String
    ): AuthResult<AuthState.Authenticated> {
        val client = SupabaseClientManager.clientOrNull
            ?: return AuthResult.Error("Supabase is not configured. Running in offline/local mode.")

        return try {
            client.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }

            val user = client.auth.currentUserOrNull()
            if (user != null) {
                val authSuccess = AuthState.Authenticated(
                    userId = user.id,
                    email = user.email,
                    isEmailVerified = user.emailConfirmedAt != null,
                    provider = "email"
                )
                _authState.value = authSuccess
                AuthResult.Success(authSuccess)
            } else {
                AuthResult.Error("Failed to obtain user session after sign-in.")
            }
        } catch (e: Throwable) {
            val friendlyMsg = mapAuthErrorMessage(e)
            AuthResult.Error(friendlyMsg, e)
        }
    }

    override suspend fun signInWithGoogleIdToken(
        idToken: String,
        rawNonce: String?
    ): AuthResult<AuthState.Authenticated> {
        val client = SupabaseClientManager.clientOrNull
            ?: return AuthResult.Error("Supabase is not configured. Running in offline/local mode.")

        return try {
            client.auth.signInWith(IDToken) {
                this.idToken = idToken
                this.provider = Google
                this.nonce = rawNonce
            }

            val user = client.auth.currentUserOrNull()
            if (user != null) {
                // Determine display name from user metadata or fallback
                val nameFromMeta = user.userMetadata?.get("full_name")?.toString()?.replace("\"", "")
                    ?: user.userMetadata?.get("name")?.toString()?.replace("\"", "")
                    ?: user.email?.substringBefore("@")
                    ?: "Google User"

                ensureCloudProfileExists(nameFromMeta, user.email)

                val authSuccess = AuthState.Authenticated(
                    userId = user.id,
                    email = user.email,
                    isEmailVerified = true,
                    provider = "google"
                )
                _authState.value = authSuccess
                AuthResult.Success(authSuccess)
            } else {
                AuthResult.Error("Failed to obtain user session after Google sign-in.")
            }
        } catch (e: Throwable) {
            val friendlyMsg = mapAuthErrorMessage(e)
            AuthResult.Error(friendlyMsg, e)
        }
    }

    override suspend fun signOut(): AuthResult<Unit> {
        val client = SupabaseClientManager.clientOrNull
            ?: return AuthResult.Success(Unit)

        return try {
            client.auth.signOut()
            _authState.value = AuthState.SignedOut
            AuthResult.Success(Unit)
        } catch (e: Throwable) {
            // Even if remote sign-out fails, set local state to SignedOut
            _authState.value = AuthState.SignedOut
            AuthResult.Success(Unit)
        }
    }

    override suspend fun resetPasswordForEmail(
        email: String,
        redirectUrl: String?
    ): AuthResult<Unit> {
        val client = SupabaseClientManager.clientOrNull
            ?: return AuthResult.Error("Supabase is not configured. Running in offline/local mode.")

        return try {
            client.auth.resetPasswordForEmail(
                email = email.trim(),
                redirectUrl = redirectUrl
            )
            AuthResult.Success(Unit)
        } catch (e: Throwable) {
            val friendlyMsg = mapAuthErrorMessage(e)
            AuthResult.Error(friendlyMsg, e)
        }
    }

    override suspend fun updateUserPassword(newPassword: String): AuthResult<Unit> {
        val client = SupabaseClientManager.clientOrNull
            ?: return AuthResult.Error("Supabase is not configured. Running in offline/local mode.")

        return try {
            client.auth.updateUser {
                password = newPassword
            }
            AuthResult.Success(Unit)
        } catch (e: Throwable) {
            val friendlyMsg = mapAuthErrorMessage(e)
            AuthResult.Error(friendlyMsg, e)
        }
    }

    override suspend fun ensureCloudProfileExists(
        displayName: String,
        email: String?
    ): AuthResult<Unit> {
        val client = SupabaseClientManager.clientOrNull ?: return AuthResult.Success(Unit)
        val uid = client.auth.currentUserOrNull()?.id ?: return AuthResult.Success(Unit)

        return try {
            // Upsert / insert profile with RLS compliance
            val profile = CloudProfileDto(
                id = uid,
                displayName = displayName.ifBlank { "User" },
                email = email,
                avatarKey = "avatar_1",
                upiId = null,
                upiConsent = false
            )
            client.postgrest.from("profiles").upsert(profile)
            AuthResult.Success(Unit)
        } catch (e: Throwable) {
            // Profile may already exist or offline, return safe result
            AuthResult.Success(Unit)
        }
    }

    override suspend fun refreshSession(): AuthState {
        val client = SupabaseClientManager.clientOrNull ?: return AuthState.SignedOut
        return try {
            val user = client.auth.currentUserOrNull()
            if (user != null) {
                AuthState.Authenticated(
                    userId = user.id,
                    email = user.email,
                    isEmailVerified = user.emailConfirmedAt != null
                ).also { _authState.value = it }
            } else {
                AuthState.SignedOut.also { _authState.value = it }
            }
        } catch (_: Throwable) {
            AuthState.SignedOut.also { _authState.value = it }
        }
    }

    private fun mapAuthErrorMessage(e: Throwable): String {
        val msg = e.message?.lowercase() ?: ""
        return when {
            msg.contains("invalid login credentials") || msg.contains("invalid_grant") ->
                "Invalid email or password. Please check your credentials and try again."
            msg.contains("user already registered") || msg.contains("user_already_exists") ->
                "An account with this email address already exists. Please log in instead."
            msg.contains("password should be at least") || msg.contains("weak_password") ->
                "Password must be at least 6 characters long."
            msg.contains("invalid email") || msg.contains("email_address_invalid") ->
                "Please enter a valid email address."
            msg.contains("network") || msg.contains("unable to resolve host") || msg.contains("connect") ->
                "Network connection unavailable. Please check your internet connection."
            msg.contains("rate limit") ->
                "Too many requests. Please wait a moment and try again."
            else ->
                "Authentication failed: ${e.message ?: "Unknown error"}"
        }
    }
}
