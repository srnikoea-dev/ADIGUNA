package com.arsipguru.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFF2563EB)
val BrandDark = Color(0xFF0F2A6B)
val BrandSky = Color(0xFF38BDF8)
val Danger = Color(0xFFEF4444)
val Indigo = Color(0xFF4F46E5)
val Violet = Color(0xFF7C3AED)
val Cyan = Color(0xFF06B6D4)
private val LightScheme = lightColorScheme(
    primary = Brand, onPrimary = Color.White, secondary = BrandSky,
    primaryContainer = Color(0xFFDCE7FF), onPrimaryContainer = Color(0xFF0B2A6F),
    background = Color(0xFFF4F6FB), surface = Color.White,
    surfaceVariant = Color(0xFFEEF1F8), onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1), outlineVariant = Color(0xFFE2E8F0)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7AA2FF), onPrimary = Color(0xFF0B1B45), secondary = BrandSky,
    primaryContainer = Color(0xFF1E3A8A), onPrimaryContainer = Color(0xFFDCE7FF),
    background = Color(0xFF0B1020), surface = Color(0xFF131A2E),
    surfaceVariant = Color(0xFF1C2540), onSurface = Color(0xFFE5E9F5),
    onSurfaceVariant = Color(0xFF93A0BD),
    outline = Color(0xFF334066), outlineVariant = Color(0xFF263154)
)

@Composable
fun ArsipTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme, content = content)
}
