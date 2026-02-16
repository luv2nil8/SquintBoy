package com.example.squintboyadvance.presentation.screens.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import com.example.squintboyadvance.shared.model.VideoScaling

@Composable
fun VideoSettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()
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
                    Text("Video", style = MaterialTheme.typography.title3)
                }
            }
            items(VideoScaling.entries.toList()) { scaling ->
                Chip(
                    onClick = { viewModel.setVideoScaling(scaling) },
                    label = { Text(scaling.displayName) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (settings.videoScaling == scaling) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    }
                )
            }
            item {
                ListHeader {
                    Text("Frameskip: ${settings.frameskip}", style = MaterialTheme.typography.body2)
                }
            }
            items(listOf(0, 1, 2)) { skip ->
                Chip(
                    onClick = { viewModel.setFrameskip(skip) },
                    label = { Text(if (skip == 0) "Auto" else "$skip") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (settings.frameskip == skip) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    }
                )
            }
            item {
                ToggleChip(
                    checked = settings.showFps,
                    onCheckedChange = { viewModel.setShowFps(it) },
                    label = { Text("Show FPS") },
                    toggleControl = {
                        Icon(
                            imageVector = ToggleChipDefaults.switchIcon(checked = settings.showFps),
                            contentDescription = null,
                            modifier = Modifier.size(ToggleChipDefaults.IconSize)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
