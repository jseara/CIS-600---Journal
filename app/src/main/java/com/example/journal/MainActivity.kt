package com.example.journal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.journal.ui.navigation.JournalNavHost
import com.example.journal.ui.theme.JournalTheme

/**
 * The app's only Activity. All screens are composables inside [JournalNavHost], so moving between
 * them is handled by Navigation Compose rather than by starting new Activities.
 *
 * It also owns the theme state (light/dark and the chosen seed color), because the theme has to
 * wrap the whole NavHost so that every screen changes color together.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Two edge-to-edge calls, on purpose. This first, plain one runs before anything is drawn,
        // so frame 1 already has the app laid out behind the system bars. The styled call inside
        // setContent (below) then makes the bar icons follow the in-app light/dark toggle.
        enableEdgeToEdge()
        setContent {
            // null = "follow the system"; true/false = the user picked dark/light with the toggle.
            // While it's null, changing the system's dark mode changes the app too.
            // rememberSaveable keeps the choice across rotation (but not after the app closes).
            var darkOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
            // `?:` (Elvis) falls back to the system setting when there is no override.
            val darkTheme = darkOverride ?: isSystemInDarkTheme()

            // Color can't be put in a Bundle, so we save its ARGB Int instead. null = Default (no seed).
            var seedArgb by rememberSaveable { mutableStateOf<Int?>(null) }
            val seedColor = seedArgb?.let { Color(it) }

            // Theme picker preview. While the dialog is open, `previewing` is true and
            // `previewColor` is the centered seed, where null means "preview Default". A separate
            // flag is needed because null can't mean both "Default" and "not previewing".
            // Not saved: after rotation the reopened dialog sets them again.
            var previewing by remember { mutableStateOf(false) }
            var previewColor by remember { mutableStateOf<Color?>(null) }

            // Draw behind the status and navigation bars (each Scaffold adds the insets back).
            // By default the bar icons follow the *system* dark mode, which would leave dark icons
            // on a dark app when the system is light. SystemBarStyle.auto's last argument decides
            // "is it dark?", so we answer with the app's own setting, and redo it when that changes.
            // DisposableEffect(darkTheme) re-runs whenever darkTheme changes; nothing to undo.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                    // API 24-25 can't draw dark navigation-bar icons, so Android always uses the dark
                    // scrim there; a transparent one would leave white buttons on a light app. This
                    // is the activity library's own default dark scrim (it's internal, so it's
                    // written out here). API 29+ ignores the scrim and enforces contrast itself.
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b),
                    ) { darkTheme },
                )
                onDispose {}
            }

            // While previewing, show the preview (possibly Default); otherwise the applied seed.
            JournalTheme(darkTheme = darkTheme, seedColor = if (previewing) previewColor else seedColor) {
                // A themed background behind every screen. During the crossfade between screens
                // both are partly transparent, and without this the always-light window background
                // would show through as a flash in dark mode. Each screen's Scaffold also uses
                // `background`/`onBackground` by default, so their colors don't change.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    JournalNavHost(
                        isDark = darkTheme,
                        onToggleDark = { darkOverride = !darkTheme },
                        seedColor = seedColor,
                        onSeedSelected = { seedArgb = it?.toArgb() },
                        onPreviewSeed = {
                            previewing = true
                            previewColor = it
                        },
                        onPreviewEnd = {
                            previewing = false
                            previewColor = null
                        },
                    )
                }
            }
        }
    }
}
