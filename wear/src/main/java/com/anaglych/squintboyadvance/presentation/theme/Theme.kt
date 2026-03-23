package com.anaglych.squintboyadvance.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

private val SquintBoyColors = Colors(
    primary = GbGreen,
    primaryVariant = GbGreenDark,
    secondary = GbGreenLight,
    secondaryVariant = GbGreenDark,
    background = SurfaceDark,
    surface = SurfaceMedium,
    onPrimary = GbDarkest,
    onSecondary = GbDarkest,
    onBackground = OnSurface,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceDim,
    error = Crimson,
    onError = androidx.compose.ui.graphics.Color.White
)

@Composable
fun SquintBoyAdvanceTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = SquintBoyColors,
        content = content
    )
}
