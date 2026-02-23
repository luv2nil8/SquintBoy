package com.example.squintboyadvance.presentation.screens.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme

private val RED = Color(0xFFD32F2F)
private val BUTTON_SIZE = 52.dp
private val BUTTON_SPACING = 14.dp

/**
 * Pause menu overlay: 2×2 grid of round buttons plus a centred Exit button.
 *
 * ```
 * [🔇 Mute]   [▶ Resume]
 * [⊡ Interface] [↺ Reset]
 *      [✕ Exit]
 * ```
 */
@Composable
fun PauseOverlay(
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onResume: () -> Unit,
    onInterface: () -> Unit,
    onReset: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val primaryGreen = MaterialTheme.colors.primary

            // Row 1: Mute + Resume
            Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_SPACING)) {
                PauseButton(
                    icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    label = if (isMuted) "Unmute" else "Mute",
                    onClick = onToggleMute,
                    backgroundColor = primaryGreen.copy(alpha = 0.85f),
                )
                PauseButton(
                    icon = Icons.Default.PlayArrow,
                    label = "Resume",
                    onClick = onResume,
                    iconColor = primaryGreen,
                )
            }

            Spacer(Modifier.height(2.dp))

            // Row 2: Interface + Reset
            Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_SPACING)) {
                PauseButton(
                    icon = Icons.Default.AspectRatio,
                    label = "Display",
                    onClick = onInterface,
                    backgroundColor = primaryGreen.copy(alpha = 0.85f),
                )
                PauseButton(
                    icon = Icons.Default.Refresh,
                    label = "Reset",
                    onClick = onReset,
                    iconColor = RED,
                )
            }

            Spacer(Modifier.height(2.dp))

            // Row 3: Exit (centred)
            PauseButton(
                icon = Icons.Default.Close,
                label = "Exit",
                onClick = onExit,
                backgroundColor = RED.copy(alpha = 0.85f),
                iconColor = Color.White,
            )
        }
    }
}

@Composable
private fun PauseButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    backgroundColor: Color = Color.White.copy(alpha = 0.12f),
    iconColor: Color = Color.White,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(BUTTON_SIZE),
        colors = ButtonDefaults.buttonColors(backgroundColor = backgroundColor),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(22.dp),
        )
    }
}
