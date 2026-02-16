package com.example.squintboyadvance.presentation.screens.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.example.squintboyadvance.presentation.navigation.Screen

@Composable
fun SettingsScreen(
    onNavigate: (Screen) -> Unit
) {
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                ListHeader {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.title3
                    )
                }
            }
            item {
                SettingsChip(
                    label = "Audio",
                    description = "Volume, enable/disable",
                    onClick = { onNavigate(Screen.AudioSettings) }
                )
            }
            item {
                SettingsChip(
                    label = "Video",
                    description = "Scaling, frameskip",
                    onClick = { onNavigate(Screen.VideoSettings) }
                )
            }
            item {
                SettingsChip(
                    label = "Controller",
                    description = "Input, layout, haptics",
                    onClick = { onNavigate(Screen.ControllerSettings) }
                )
            }
            item {
                SettingsChip(
                    label = "GB Palette",
                    description = "Color presets",
                    onClick = { onNavigate(Screen.PaletteSettings) }
                )
            }
            item {
                SettingsChip(
                    label = "Save Manager",
                    description = "Save slots, import/export",
                    onClick = { onNavigate(Screen.SaveManager) }
                )
            }
        }
    }
}

@Composable
private fun SettingsChip(
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Chip(
        onClick = onClick,
        label = { Text(label) },
        secondaryLabel = { Text(description) },
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors()
    )
}
