package com.arsipguru.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Indigo = Color(0xFF4F46E5)
val Violet = Color(0xFF7C5CFA)
val Cyan = Color(0xFF06B6D4)

private val LightScheme = lightColorScheme(
    primary = Indigo, onPrimary = Color.White, secondary = Cyan,
    background = Color(0xFFF5F6FC), surface = Color.White,
    surfaceVariant = Color(0xFFECEEFA), onSurface = Color(0xFF1B1D2A),
    onSurfaceVariant = Color(0xFF5B5F73),
    primaryContainer = Color(0xFFE3E3FF), onPrimaryContainer = Color(0xFF1A1470)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9EA1FF), onPrimary = Color(0xFF1A1470), secondary = Cyan,
    background = Color(0xFF0F1020), surface = Color(0xFF181A2E),
    surfaceVariant = Color(0xFF23263D), onSurface = Color(0xFFE6E7F5),
    onSurfaceVariant = Color(0xFFA9ADC4),
    primaryContainer = Color(0xFF2B2C6B), onPrimaryContainer = Color(0xFFE0E0FF)
)

@Composable
fun ArsipTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme, content = content)
}
