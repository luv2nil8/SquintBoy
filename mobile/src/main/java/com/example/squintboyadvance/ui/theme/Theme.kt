package com.example.squintboyadvance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GbGreen = Color(0xFF9BBC0F)
private val GbGreenLight = Color(0xFFC4D428)
private val GbGreenDark = Color(0xFF306230)
private val SurfaceDark = Color(0xFF1A1A2E)
private val SurfaceMedium = Color(0xFF16213E)

private val DarkColorScheme = darkColorScheme(
    primary = GbGreen,
    onPrimary = Color(0xFF0F380F),
    primaryContainer = GbGreenDark,
    onPrimaryContainer = GbGreenLight,
    secondary = GbGreenLight,
    onSecondary = Color(0xFF0F380F),
    background = SurfaceDark,
    onBackground = Color(0xFFE0E0E0),
    surface = SurfaceMedium,
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2A2A4A),
    onSurfaceVariant = Color(0xFF8A8A8A),
    error = Color(0xFFCF6679),
    onError = Color.Black,
)

@Composable
fun SquintBoyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
