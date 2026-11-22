package dev.slipaway.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.slipaway.data.ThemeMode

/** Colors from the app icon: a navy night and the amber light coming through the door. */
object Brand {
    val Navy = Color(0xFF1B2340)
    val NavyDeep = Color(0xFF10152A)
    val Indigo = Color(0xFF2E3A66)
    val Amber = Color(0xFFF5B544)
    val Paper = Color(0xFFF7F5F0)
}

private val LightColors = lightColorScheme(
    primary = Brand.Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2F5),
    onPrimaryContainer = Color(0xFF121833),
    secondary = Color(0xFF7A5200),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2AE),
    onSecondaryContainer = Color(0xFF2B1A00),
    tertiary = Brand.Indigo,
    background = Brand.Paper,
    onBackground = Brand.Navy,
    surface = Brand.Paper,
    onSurface = Brand.Navy,
    surfaceVariant = Color(0xFFE6E8F0),
    onSurfaceVariant = Color(0xFF5A6078),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFAF6),
    surfaceContainer = Color(0xFFF1F0EB),
    surfaceContainerHigh = Color(0xFFEBEAE4),
    surfaceContainerHighest = Color(0xFFE5E4DE),
    outline = Color(0xFF8D92A8),
    outlineVariant = Color(0xFFDCDFE8),
)

private val DarkColors = darkColorScheme(
    primary = Brand.Amber,
    onPrimary = Brand.Navy,
    primaryContainer = Color(0xFF5C4300),
    onPrimaryContainer = Color(0xFFFFE2AE),
    secondary = Color(0xFFB8C3F0),
    onSecondary = Brand.Navy,
    secondaryContainer = Brand.Indigo,
    onSecondaryContainer = Color(0xFFDDE2F5),
    tertiary = Color(0xFFB8C3F0),
    background = Brand.NavyDeep,
    onBackground = Color(0xFFEEF0F7),
    surface = Brand.NavyDeep,
    onSurface = Color(0xFFEEF0F7),
    surfaceVariant = Brand.Indigo,
    onSurfaceVariant = Color(0xFFA3A9C2),
    surfaceContainerLowest = Color(0xFF0B0F1F),
    surfaceContainerLow = Color(0xFF161C35),
    surfaceContainer = Brand.Navy,
    surfaceContainerHigh = Color(0xFF222A4A),
    surfaceContainerHighest = Color(0xFF2A3356),
    outline = Color(0xFF7A81A0),
    outlineVariant = Brand.Indigo,
)

/** Whether the app is drawn dark, and whether it uses its own palette (not wallpaper colors). */
data class ThemeInfo(val dark: Boolean, val brandColors: Boolean)

val LocalThemeInfo = staticCompositionLocalOf { ThemeInfo(dark = false, brandColors = true) }

/** Green for "ready" states, readable on both backgrounds. */
@Composable
fun okColor(): Color = if (LocalThemeInfo.current.dark) Color(0xFF7FD1A8) else Color(0xFF2E7D5B)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun SlipAwayTheme(dark: Boolean, wallpaperColors: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val useWallpaper = wallpaperColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        useWallpaper ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalThemeInfo provides ThemeInfo(dark, brandColors = !useWallpaper)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
