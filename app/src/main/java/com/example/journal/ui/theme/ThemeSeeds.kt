package com.example.journal.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.example.journal.R

/**
 * One color the user can pick in the theme picker.
 *
 * MaterialKolor turns this single "seed" color into a complete Material 3 color scheme (primary,
 * surfaces, containers, …) for both light and dark mode, so we only need to store one color each.
 *
 * @property nameRes The color's name, read aloud by TalkBack, so it lives in `strings.xml`.
 * @property color The seed color itself.
 */
data class ThemeSeed(
    @param:StringRes val nameRes: Int,
    val color: Color,
)

/**
 * The seed colors offered in the theme picker, in the order they appear.
 *
 * These are the one place in the app with hard-coded `Color(0x…)` values. That is deliberate:
 * they aren't styling for a component, they are the *data* the user chooses between (the 500
 * shades of the classic Material palette), and every color on screen is derived from them.
 */
val ThemeSeeds: List<ThemeSeed> = listOf(
    ThemeSeed(R.string.seed_red, Color(0xFFF44336)),
    ThemeSeed(R.string.seed_pink, Color(0xFFE91E63)),
    ThemeSeed(R.string.seed_purple, Color(0xFF9C27B0)),
    ThemeSeed(R.string.seed_indigo, Color(0xFF3F51B5)),
    ThemeSeed(R.string.seed_blue, Color(0xFF2196F3)),
    ThemeSeed(R.string.seed_teal, Color(0xFF009688)),
    ThemeSeed(R.string.seed_green, Color(0xFF4CAF50)),
    ThemeSeed(R.string.seed_amber, Color(0xFFFFC107)),
    ThemeSeed(R.string.seed_orange, Color(0xFFFF9800)),
    ThemeSeed(R.string.seed_brown, Color(0xFF795548)),
)

/**
 * The pages of the theme picker, in order: `null` first, meaning "Default" (no seed: wallpaper
 * colors on Android 12+, the template scheme below that), followed by the ten [ThemeSeeds].
 *
 * Default is kept out of [ThemeSeeds] itself so that list stays "real colors only"; only the
 * picker needs the extra page.
 */
val ThemePickerPages: List<ThemeSeed?> = listOf(null) + ThemeSeeds

/**
 * The page the picker opens on when no seed has been chosen yet (or the saved one is unknown).
 * It is the "Default" page, the first entry of [ThemePickerPages].
 */
const val DEFAULT_SEED_PAGE = 0

/**
 * Returns the index in [seedArgbs] that the picker should start on for [selectedArgb].
 *
 * Colors are compared as ARGB `Int`s (what `Color.toArgb()` returns, and what the app saves),
 * which keeps this a plain function that a JUnit test can call without any Compose setup.
 * A `null` in [seedArgbs] stands for the Default page, so a `null` selection finds it.
 * Returns [DEFAULT_SEED_PAGE] when nothing matches (including a null selection in a list
 * without a null entry).
 */
fun initialSeedPage(seedArgbs: List<Int?>, selectedArgb: Int?): Int {
    // indexOf works with null too: it finds the first null entry, if there is one.
    val index = seedArgbs.indexOf(selectedArgb)
    return if (index >= 0) index else DEFAULT_SEED_PAGE
}
