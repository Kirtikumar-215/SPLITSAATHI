package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.data.local.SplitSaathiDatabase
import com.example.data.repository.SplitSaathiRepository
import com.example.ui.screens.MainScreen
import com.example.ui.theme.SplitSaathiTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: SplitSaathiRepository
    private var initialDeepLinkCode: String? = null
    private var initialAuthDeepLink: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = SplitSaathiDatabase.getInstance(applicationContext)
        val sessionManager = com.example.data.local.UserSessionManager.getInstance(applicationContext)
        repository = SplitSaathiRepository(db, sessionManager)

        initialDeepLinkCode = extractDeepLinkCode(intent)
        initialAuthDeepLink = extractAuthDeepLink(intent)

        setContent {
            SplitSaathiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        repository = repository,
                        initialDeepLinkCode = initialDeepLinkCode,
                        initialAuthDeepLink = initialAuthDeepLink
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val code = extractDeepLinkCode(intent)
        if (code != null) {
            initialDeepLinkCode = code
        }
        val authLink = extractAuthDeepLink(intent)
        if (authLink != null) {
            initialAuthDeepLink = authLink
        }
    }

    private fun extractDeepLinkCode(intent: Intent?): String? {
        val data: Uri? = intent?.data
        if (data != null && data.scheme == "splitsaathi" && data.host == "join") {
            // URL format: splitsaathi://join/<code>
            val pathSegments = data.pathSegments
            if (pathSegments.isNotEmpty()) {
                return pathSegments[0]
            }
        }
        return null
    }

    private fun extractAuthDeepLink(intent: Intent?): String? {
        val data: Uri? = intent?.data ?: return null
        if (data.scheme == "splitsaathi" && (data.host == "reset-password" || data.host == "auth")) {
            return data.toString()
        }
        return null
    }
}
