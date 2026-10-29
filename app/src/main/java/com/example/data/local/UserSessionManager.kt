package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages local user session and credentials for SplitSaathi.
 * Ensures anonymous onboarding is stored securely and persistently on device.
 * Returning users bypass onboarding directly into dashboard.
 */
class UserSessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "splitsaathi_user_session"
        private const val KEY_USER_ID = "current_user_id"
        private const val KEY_USER_NAME = "current_user_name"
        private const val KEY_USER_PHONE = "current_user_phone"
        private const val KEY_USER_UPI = "current_user_upi"
        private const val KEY_UPI_CONSENT = "current_upi_consent"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

        @Volatile
        private var INSTANCE: UserSessionManager? = null

        fun getInstance(context: Context): UserSessionManager {
            return INSTANCE ?: synchronized(this) {
                val instance = UserSessionManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    var currentUserId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var currentUserName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var currentUserPhone: String?
        get() = prefs.getString(KEY_USER_PHONE, null)
        set(value) = prefs.edit().putString(KEY_USER_PHONE, value).apply()

    var currentUserUpi: String?
        get() = prefs.getString(KEY_USER_UPI, null)
        set(value) = prefs.edit().putString(KEY_USER_UPI, value).apply()

    var isUpiConsentGranted: Boolean
        get() = prefs.getBoolean(KEY_UPI_CONSENT, false)
        set(value) = prefs.edit().putBoolean(KEY_UPI_CONSENT, value).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false) && !currentUserId.isNullOrEmpty()
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    fun saveUserSession(userId: String, name: String, phone: String?, upiId: String?, upiConsent: Boolean) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_PHONE, phone)
            .putString(KEY_USER_UPI, upiId)
            .putBoolean(KEY_UPI_CONSENT, upiConsent)
            .putBoolean(KEY_ONBOARDING_COMPLETED, true)
            .apply()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
