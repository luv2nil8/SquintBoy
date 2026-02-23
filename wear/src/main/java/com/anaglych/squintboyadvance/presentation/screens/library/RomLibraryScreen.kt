package com.anaglych.squintboyadvance.presentation.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.anaglych.squintboyadvance.shared.model.RomMetadata

@Composable
fun RomLibraryScreen(
    onRomSelected: (RomMetadata) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: RomLibraryViewModel = viewModel()
) {
    val roms by viewModel.roms.collectAsState()
    val pickerState by viewModel.pickerState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.scanRoms()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Scaffold(
            timeText = { TimeText() },
            vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        ) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                // ── Action buttons ──────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = onSettingsClick,
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = MaterialTheme.colors.primary,
                            ),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                        Spacer(Modifier.width(16.dp))
                        Button(
                            onClick = { viewModel.sendOpenRomPicker() },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = MaterialTheme.colors.primary,
                            ),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add ROM")
                        }
                    }
                }

                // ── ROM list or empty state ─────────────────────────────
                if (roms.isEmpty()) {
                    item { EmptyLibraryState() }
                } else {
                    items(roms, key = { it.id }) { rom ->
                        RomCard(rom = rom, onClick = { onRomSelected(rom) })
                    }
                }
            }
        }

        // ── Phone picker overlay ────────────────────────────────────────
        if (pickerState != RomPickerState.IDLE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .clickable { viewModel.dismissPickerOverlay() },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colors.surface)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (pickerState) {
                        RomPickerState.SENDING -> {
                            Text(
                                "Opening on phone…",
                                style = MaterialTheme.typography.body2,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colors.primary,
                            )
                        }
                        RomPickerState.WAITING -> {
                            Text(
                                "Pick a ROM file on your phone",
                                style = MaterialTheme.typography.body2,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Tap to dismiss",
                                style = MaterialTheme.typography.caption3,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                            )
                        }
                        RomPickerState.ERROR -> {
                            Text(
                                "Phone not reachable",
                                style = MaterialTheme.typography.body2,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colors.error,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Tap to dismiss",
                                style = MaterialTheme.typography.caption3,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                            )
                        }
                        RomPickerState.IDLE -> {}
                    }
                }
            }
        }
    }
}
