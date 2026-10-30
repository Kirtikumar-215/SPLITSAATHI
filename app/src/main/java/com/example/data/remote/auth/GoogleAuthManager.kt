package com.example.data.remote.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

/**
 * Handles Google ID token authentication using Android Jetpack Credential Manager.
 * Never logs or exposes tokens or secrets.
 */
object GoogleAuthManager {

    val isConfigured: Boolean
        get() {
            val webClientId = try {
                BuildConfig.GOOGLE_WEB_CLIENT_ID
            } catch (_: Throwable) {
                ""
            }
            return webClientId.isNotBlank() &&
                    !webClientId.contains("placeholder", ignoreCase = true)
        }

    /**
     * Obtains a Google ID token from the user's Google accounts on the device.
     * Returns Pair(idToken, rawNonce).
     */
    suspend fun getGoogleIdToken(context: Context): AuthResult<Pair<String, String>> {
        val webClientId = try {
            BuildConfig.GOOGLE_WEB_CLIENT_ID
        } catch (_: Throwable) {
            ""
        }

        if (!isConfigured) {
            return AuthResult.Error(
                "Google Sign-In is not configured. Please supply a valid GOOGLE_WEB_CLIENT_ID in .env or AI Studio secrets."
            )
        }

        val credentialManager = CredentialManager.create(context)
        val rawNonce = UUID.randomUUID().toString()
        val bytes = rawNonce.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .setNonce(hashedNonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                request = request,
                context = context
            )
            when (val credential = result.credential) {
                is CustomCredential -> {
                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        AuthResult.Success(Pair(googleIdTokenCredential.idToken, rawNonce))
                    } else {
                        AuthResult.Error("Unexpected credential type: ${credential.type}")
                    }
                }
                else -> {
                    AuthResult.Error("Unsupported credential received.")
                }
            }
        } catch (_: GetCredentialCancellationException) {
            AuthResult.Error("Sign in cancelled by user.")
        } catch (e: GetCredentialException) {
            AuthResult.Error("Google Sign-In failed: ${e.message}", e)
        } catch (e: Throwable) {
            AuthResult.Error("Google Sign-In error: ${e.message}", e)
        }
    }
}
