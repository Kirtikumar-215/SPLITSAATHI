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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = SplitSaathiDatabase.getInstance(applicationContext)
        val sessionManager = com.example.data.local.UserSessionManager.getInstance(applicationContext)
        repository = SplitSaathiRepository(db, sessionManager)

        initialDeepLinkCode = extractDeepLinkCode(intent)

        setContent {
            SplitSaathiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        repository = repository,
                        initialDeepLinkCode = initialDeepLinkCode
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
}
