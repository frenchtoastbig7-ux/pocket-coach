package com.sovereignedge.pocketcoach.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The look: near-black surfaces, a hot orange-red accent, condensed bold uppercase headings, tight cards.
 * Dynamic (Material You) colour is opt-in; the app's own palette is the default.
 */

val Accent = Color(0xFFFF4F1F)
val AccentDark = Color(0xFFC63A12)
val Lime = Color(0xFFB8F25A)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1409),
    onPrimaryContainer = Color(0xFFFFC9B8),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2A2A2A),
    onSecondaryContainer = Color(0xFFE6E6E6),
    tertiary = Lime,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF203A08),
    onTertiaryContainer = Color(0xFFD9FFA8),
    background = Color(0xFF0C0C0D),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF0C0C0D),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1E1E21),
    onSurfaceVariant = Color(0xFFA9A9AF),
    surfaceContainer = Color(0xFF161618),
    surfaceContainerHigh = Color(0xFF1C1C1F),
    surfaceContainerHighest = Color(0xFF242428),
    surfaceContainerLow = Color(0xFF111113),
    outline = Color(0xFF4A4A50),
    outlineVariant = Color(0xFF2C2C30),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDACE),
    onPrimaryContainer = Color(0xFF3A1409),
    secondary = Color(0xFF4A4A50),
    secondaryContainer = Color(0xFFE4E4E8),
    tertiary = Color(0xFF3E7A0A),
    tertiaryContainer = Color(0xFFDDF5B8),
    onTertiaryContainer = Color(0xFF1C3A00),
    background = Color(0xFFF7F7F8),
    surface = Color(0xFFF7F7F8),
    surfaceVariant = Color(0xFFE8E8EC),
    onSurfaceVariant = Color(0xFF55555C),
    surfaceContainer = Color(0xFFEFEFF2),
    surfaceContainerHigh = Color(0xFFE9E9ED),
    surfaceContainerHighest = Color(0xFFE2E2E7),
    outline = Color(0xFFB4B4BC),
    outlineVariant = Color(0xFFD6D6DC),
)

/** Roboto Condensed ships on every Android device; no font files needed. */
val Condensed = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Bold),
)

private val base = Typography()
val CoachTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
    displayMedium = base.displayMedium.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
    displaySmall = base.displaySmall.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    titleLarge = base.titleLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleMedium = base.titleMedium.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = 0.sp),
    titleSmall = base.titleSmall.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.2.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
    labelSmall = base.labelSmall.copy(letterSpacing = 1.sp),
)

val CoachShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** Whether the app should draw dark for [themeMode] ("system", "light" or "dark"). */
@Composable
fun isDarkTheme(themeMode: String): Boolean = when (themeMode) {
    "light" -> false
    "system" -> isSystemInDarkTheme()
    else -> true
}

@Composable
fun PocketCoachTheme(themeMode: String = "dark", dynamic: Boolean = false, content: @Composable () -> Unit) {
    val dark = isDarkTheme(themeMode)
    val context = LocalContext.current
    val colors = when {
        dynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = CoachTypography, shapes = CoachShapes, content = content)
}

/** Section labels and column headers: small caps-style uppercase. */
@Composable
fun eyebrow(): TextStyle = MaterialTheme.typography.labelMedium
