package com.example.journal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.journal.ui.navigation.JournalNavHost
import com.example.journal.ui.theme.JournalTheme

/**
 * The app's only Activity. All screens are composables inside [JournalNavHost], so moving between
 * them is handled by Navigation Compose rather than by starting new Activities.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the status and navigation bars; each Scaffold adds the right insets back.
        enableEdgeToEdge()
        setContent {
            JournalTheme {
                JournalNavHost()
            }
        }
    }
}
