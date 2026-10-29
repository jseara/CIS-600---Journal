package com.example.journal.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.ktx.animateColorScheme
import com.materialkolor.rememberDynamicColorScheme

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

/**
 * The color scheme the app uses when no seed color is chosen: wallpaper-based "Material You"
 * colors on Android 12+ (if [dynamicColor] is on), otherwise the template's purple schemes.
 *
 * It is its own function so the theme picker can draw its "Default" circle in exactly the color
 * the app would use, instead of guessing.
 *
 * @param darkTheme Whether to return the dark variant.
 * @param dynamicColor Whether to use wallpaper colors when the device supports them.
 */
@Composable
fun defaultColorScheme(darkTheme: Boolean, dynamicColor: Boolean = true): ColorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    darkTheme -> DarkColorScheme
    else -> LightColorScheme
}

/**
 * The app's Material 3 theme. Every screen reads its colors and text styles from here.
 *
 * Which color scheme is used, in order of priority:
 * 1. [seedColor] set: MaterialKolor generates a full scheme from that one color.
 * 2. Android 12+ with [dynamicColor]: the wallpaper-based "Material You" colors.
 * 3. Otherwise the template's purple light/dark schemes.
 *
 * @param darkTheme Whether to use the dark variant. Defaults to the system setting.
 * @param dynamicColor Whether to use wallpaper colors on Android 12+ when no seed is set.
 * @param seedColor The color the user chose in the theme picker, or null for the default.
 * @param content The UI to theme.
 */
@Composable
fun JournalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    seedColor: Color? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // A chosen seed wins over wallpaper and template colors.
        seedColor != null -> rememberDynamicColorScheme(seedColor = seedColor, isDark = darkTheme)
        else -> defaultColorScheme(darkTheme, dynamicColor)
    }

    MaterialTheme(
        // animateColorScheme fades every color to its new value instead of jumping, so switching
        // light/dark or swiping through the theme picker looks smooth.
        colorScheme = animateColorScheme(colorScheme),
        typography = Typography,
        content = content
    )
}