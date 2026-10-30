package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Singleton manager for the Supabase Kotlin client.
 *
 * Responsibilities:
 * - Initializes and provides access to [SupabaseClient] with Auth, Postgrest, and Realtime plugins.
 * - Safely detects missing or placeholder configurations without crashing the application.
 * - Configures PKCE authentication flow and OkHttp engine.
 *
 * Note: Remote authentication and cloud sync logic will be connected in Phase 2.2+.
 * Existing Room database and offline flows remain authoritative and unaffected.
 */
object SupabaseClientManager {

    private const val TAG = "SupabaseClientManager"

    /**
     * Checks if valid non-placeholder Supabase credentials are provided.
     */
    val isConfigured: Boolean
        get() {
            val url = BuildConfig.SUPABASE_URL.trim()
            val key = BuildConfig.SUPABASE_ANON_KEY.trim()
            return url.isNotEmpty() &&
                    key.isNotEmpty() &&
                    !url.contains("placeholder", ignoreCase = true) &&
                    !url.contains("[YOUR-", ignoreCase = true) &&
                    !key.contains("placeholder", ignoreCase = true) &&
                    !key.contains("[YOUR-", ignoreCase = true)
        }

    /**
     * Lazily initialized Supabase client instance.
     * Returns null if credentials are empty, placeholder, or invalid.
     */
    val clientOrNull: SupabaseClient? by lazy {
        val url = BuildConfig.SUPABASE_URL.trim()
        val key = BuildConfig.SUPABASE_ANON_KEY.trim()

        if (url.isEmpty() || key.isEmpty()) {
            logWarn(TAG, "Supabase credentials are missing. Running in local/offline-only mode.")
            null
        } else if (!isConfigured) {
            logInfo(TAG, "Supabase configured with placeholder credentials. Running in local/offline-only mode.")
            null
        } else {
            try {
                createSupabaseClient(
                    supabaseUrl = url,
                    supabaseKey = key
                ) {
                    httpEngine = OkHttp.create()

                    install(Auth) {
                        flowType = FlowType.PKCE
                        alwaysAutoRefresh = true
                    }
                    install(Postgrest)
                    install(Realtime)
                }.also {
                    logInfo(TAG, "Supabase client successfully initialized for $url")
                }
            } catch (e: Exception) {
                logError(TAG, "Failed to initialize Supabase client: ${e.message}", e)
                null
            }
        }
    }

    private fun logInfo(tag: String, msg: String) {
        try {
            Log.i(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    private fun logWarn(tag: String, msg: String) {
        try {
            Log.w(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    private fun logError(tag: String, msg: String, tr: Throwable?) {
        try {
            Log.e(tag, msg, tr)
        } catch (_: Throwable) {
            println("[$tag] $msg: ${tr?.message}")
        }
    }

    /**
     * Returns the active [SupabaseClient] or throws [IllegalStateException] if not configured.
     */
    fun requireClient(): SupabaseClient {
        return clientOrNull ?: throw IllegalStateException(
            "Supabase client is not configured. Please supply valid SUPABASE_URL and SUPABASE_ANON_KEY via .env or AI Studio secrets."
        )
    }

    /**
     * Safe accessor for the Auth plugin.
     */
    val authOrNull: Auth?
        get() = clientOrNull?.auth

    /**
     * Safe accessor for the Postgrest plugin.
     */
    val postgrestOrNull: Postgrest?
        get() = clientOrNull?.postgrest

    /**
     * Safe accessor for the Realtime plugin.
     */
    val realtimeOrNull: Realtime?
        get() = clientOrNull?.realtime
}
