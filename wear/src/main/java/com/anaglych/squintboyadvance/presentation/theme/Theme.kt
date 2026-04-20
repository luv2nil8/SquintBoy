package com.anaglych.squintboyadvance.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.ReduceMotion
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
    error = DangerCrimson,
    onError = Color.White
)

@OptIn(ExperimentalWearFoundationApi::class)
private val noReduceMotion = object : ReduceMotion {
    @Composable override fun enabled(): Boolean = false
}

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun SquintBoyAdvanceTheme(
    content: @Composable () -> Unit
) {
    // Override LocalReduceMotion so wear compose never reads Settings.Global "reduce_motion".
    // Xiaomi Wear OS restricts that key to targetSdk <= 34 and throws SecurityException.
    CompositionLocalProvider(LocalReduceMotion provides noReduceMotion) {
        MaterialTheme(
            colors = SquintBoyColors,
            content = content
        )
    }
}
