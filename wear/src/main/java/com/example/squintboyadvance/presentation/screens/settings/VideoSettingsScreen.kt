package com.example.squintboyadvance.presentation.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
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
import com.example.squintboyadvance.shared.model.ScaleMode

@Composable
fun VideoSettingsScreen(
    onOpenScaleEditor: (isGba: Boolean) -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val listState = rememberScalingLazyListState()
    var isGba by rememberSaveable { mutableStateOf(true) }

    val scaleMode = if (isGba) settings.gbaScaleMode else settings.gbScaleMode
    val filterEnabled = if (isGba) settings.gbaFilterEnabled else settings.gbFilterEnabled

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            item {
                ListHeader {
                    Text("Video", style = MaterialTheme.typography.title2, color = MaterialTheme.colors.primary)
                }
            }

            // System toggle — both chips always visible
            item {
                SystemToggle(
                    isGba = isGba,
                    onSelect = { isGba = it }
                )
            }

            // Scaling section header
            item {
                ListHeader {
                    Text("Scaling", style = MaterialTheme.typography.body1, color = MaterialTheme.colors.onSurface)
                }
            }

            // Scale mode row
            item {
                ScaleModeRow(
                    scaleMode = scaleMode,
                    onToggle = {
                        val newMode = if (scaleMode == ScaleMode.INTEGER)
                            ScaleMode.CUSTOM else ScaleMode.INTEGER
                        if (isGba) viewModel.setGbaScaleMode(newMode)
                        else viewModel.setGbScaleMode(newMode)
                    },
                    onEdit = { onOpenScaleEditor(isGba) }
                )
            }

            // Filter toggle
            item {
                ToggleChip(
                    checked = filterEnabled,
                    onCheckedChange = {
                        if (isGba) viewModel.setGbaFilter(it)
                        else viewModel.setGbFilter(it)
                    },
                    label = { Text("Bilinear Filter") },
                    toggleControl = {
                        Icon(
                            imageVector = ToggleChipDefaults.switchIcon(checked = filterEnabled),
                            contentDescription = null,
                            modifier = Modifier.size(ToggleChipDefaults.IconSize)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // --- Frameskip ---
            item {
                ListHeader {
                    Text("Frameskip", style = MaterialTheme.typography.body1, color = MaterialTheme.colors.onSurface)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
                ) {
                    val options = listOf(-1 to "Auto", 0 to "Off", 1 to "1", 2 to "2")
                    for ((value, label) in options) {
                        CompactChip(
                            onClick = { viewModel.setFrameskip(value) },
                            label = { Text(label) },
                            colors = if (settings.frameskip == value) {
                                ChipDefaults.primaryChipColors()
                            } else {
                                ChipDefaults.secondaryChipColors()
                            }
                        )
                    }
                }
            }

            // Show FPS toggle
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

@Composable
private fun SystemToggle(
    isGba: Boolean,
    onSelect: (Boolean) -> Unit
) {
    val gbaScale by animateFloatAsState(if (isGba) 1f else 0.85f, label = "gbaScale")
    val gbaAlpha by animateFloatAsState(if (isGba) 1f else 0.5f, label = "gbaAlpha")
    val gbScale by animateFloatAsState(if (!isGba) 1f else 0.85f, label = "gbScale")
    val gbAlpha by animateFloatAsState(if (!isGba) 1f else 0.5f, label = "gbAlpha")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
    ) {
        CompactChip(
            onClick = { onSelect(true) },
            label = { Text("GBA") },
            colors = if (isGba) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
            modifier = Modifier.graphicsLayer {
                scaleX = gbaScale
                scaleY = gbaScale
                alpha = gbaAlpha
            }
        )
        CompactChip(
            onClick = { onSelect(false) },
            label = { Text("GB/GBC") },
            colors = if (!isGba) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
            modifier = Modifier.graphicsLayer {
                scaleX = gbScale
                scaleY = gbScale
                alpha = gbAlpha
            }
        )
    }
}

@Composable
private fun ScaleModeRow(
    scaleMode: ScaleMode,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Toggle between Integer 2x / Custom
        Chip(
            onClick = onToggle,
            label = {
                Text(
                    text = if (scaleMode == ScaleMode.INTEGER) "Integer 2x" else "Custom",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            modifier = Modifier.weight(1f),
            colors = ChipDefaults.primaryChipColors()
        )

        // Edit button — only when Custom
        if (scaleMode == ScaleMode.CUSTOM) {
            Chip(
                onClick = onEdit,
                label = {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit scaling",
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
}
