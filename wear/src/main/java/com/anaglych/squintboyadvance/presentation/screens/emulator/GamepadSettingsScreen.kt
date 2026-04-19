package com.anaglych.squintboyadvance.presentation.screens.emulator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.anaglych.squintboyadvance.shared.model.ButtonId
import com.anaglych.squintboyadvance.shared.model.GamepadMapping

private val RECORD_RED = Color(0xFFEC1358)
private val SLOT_BLUE  = Color(0xFF6A5ACD)
private val PAIR_GREEN = Color(0xFF9BBC0F)

private fun buttonLabel(button: ButtonId): String = when (button) {
    ButtonId.A          -> "A"
    ButtonId.B          -> "B"
    ButtonId.START      -> "Start"
    ButtonId.SELECT     -> "Select"
    ButtonId.DPAD_UP    -> "D-pad Up"
    ButtonId.DPAD_DOWN  -> "D-pad Down"
    ButtonId.DPAD_LEFT  -> "D-pad Left"
    ButtonId.DPAD_RIGHT -> "D-pad Right"
    ButtonId.L          -> "L"
    ButtonId.R          -> "R"
}

@Composable
fun GamepadSettingsScreen(
    gamepadMapping: GamepadMapping,
    recordingState: EmulatorViewModel.RecordingState,
    onStartRecordAll: () -> Unit,
    onSkip: () -> Unit,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showingPairScreen by remember { mutableStateOf(false) }

    BackHandler {
        when {
            showingPairScreen -> showingPairScreen = false
            recordingState is EmulatorViewModel.RecordingState.Recording -> onSkip()
            else -> onDismiss()
        }
    }

    val listState = rememberScalingLazyListState()
    val buttons = remember { ButtonId.values().toList() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.93f)),
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    "Gamepad",
                    style = MaterialTheme.typography.title3,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            // Find & Pair chip — full width
            item {
                Chip(
                    onClick = { showingPairScreen = true },
                    colors = ChipDefaults.chipColors(backgroundColor = PAIR_GREEN.copy(alpha = 0.85f)),
                    label = { Text("Find & Pair Controller", fontSize = 11.sp) },
                    icon = {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Record All / Defaults row
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(
                        onClick = onStartRecordAll,
                        colors = ChipDefaults.chipColors(backgroundColor = RECORD_RED.copy(alpha = 0.85f)),
                        label = { Text("Record All", fontSize = 11.sp) },
                        icon = {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Chip(
                        onClick = onResetDefaults,
                        colors = ChipDefaults.chipColors(backgroundColor = Color.White.copy(alpha = 0.10f)),
                        label = { Text("Defaults", fontSize = 11.sp) },
                        icon = {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // Mapping list
            items(buttons) { button ->
                MappingRow(button = button, keyCode = gamepadMapping.forButton(button))
            }
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Chip(
                    onClick = onDismiss,
                    colors = ChipDefaults.chipColors(backgroundColor = Color.White.copy(alpha = 0.08f)),
                    label = { Text("Done", fontSize = 11.sp) },
                    icon = {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                )
            }
        }

        if (recordingState is EmulatorViewModel.RecordingState.Recording) {
            RecordingOverlay(
                button = recordingState.targetButton,
                stepIndex = recordingState.stepIndex,
                totalSteps = recordingState.totalSteps,
                onSkip = onSkip,
            )
        }

        if (showingPairScreen) {
            ControllerPairScreen(onDismiss = { showingPairScreen = false })
        }
    }
}

@Composable
private fun MappingRow(button: ButtonId, keyCode: Int?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buttonLabel(button),
            style = MaterialTheme.typography.body2,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f),
        )
        Text(
            if (keyCode != null) GamepadMapping.keyCodeLabel(keyCode) else "—",
            style = MaterialTheme.typography.body2,
            color = if (keyCode != null) SLOT_BLUE else Color.White.copy(alpha = 0.35f),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RecordingOverlay(
    button: ButtonId,
    stepIndex: Int,
    totalSteps: Int,
    onSkip: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            Text(
                "${stepIndex + 1} / $totalSteps",
                style = MaterialTheme.typography.caption2,
                color = Color.White.copy(alpha = 0.5f),
            )
            Icon(
                Icons.Default.Bluetooth,
                contentDescription = null,
                tint = RECORD_RED,
                modifier = Modifier.size(28.dp),
            )
            Text(
                "Press controller\nbutton for:",
                style = MaterialTheme.typography.body2,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
            Text(
                buttonLabel(button),
                style = MaterialTheme.typography.title2,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onSkip,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.width(80.dp),
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Skip", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Skip", fontSize = 11.sp)
            }
        }
    }
}
