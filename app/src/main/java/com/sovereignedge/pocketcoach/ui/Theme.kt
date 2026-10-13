package com.sovereignedge.pocketcoach.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F3A5F),
    secondary = Color(0xFF8A6A1F),
    tertiary = Color(0xFF2E7D5B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7F0),
    secondary = Color(0xFFF4B942),
    tertiary = Color(0xFF8FD3B4),
)

/** Whether the app should draw dark for [themeMode] ("system", "light" or "dark"). */
@Composable
fun isDarkTheme(themeMode: String): Boolean = when (themeMode) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}

/** Material You: follows the Pixel wallpaper palette; the fixed navy is only a fallback. */
@Composable
fun PocketCoachTheme(themeMode: String = "system", dynamic: Boolean = true, content: @Composable () -> Unit) {
    val dark = isDarkTheme(themeMode)
    val context = LocalContext.current
    val colors = when {
        dynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
