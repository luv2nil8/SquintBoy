package com.anaglych.squintboyadvance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GbGreen,
    onPrimary = GbDarkest,
    primaryContainer = GbGreenDark,
    onPrimaryContainer = GbGreenLight,
    secondary = GbGreenLight,
    onSecondary = GbDarkest,
    background = SurfaceDark,
    onBackground = OnSurface,
    surface = SurfaceMedium,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceDim,
    error = Crimson,
    onError = Color.White,
)

@Composable
fun SquintBoyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
