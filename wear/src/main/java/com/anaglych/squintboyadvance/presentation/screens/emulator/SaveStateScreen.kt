package com.anaglych.squintboyadvance.presentation.screens.emulator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme

private val BLUE = Color(0xFF6A5ACD)

@Composable
fun SaveStateScreen(
    hasSaveState: Boolean,
    canUndoSave: Boolean,
    canUndoLoad: Boolean,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onUndoSave: () -> Unit,
    onUndoLoad: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    val green = MaterialTheme.colors.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            SaveLoadRow(
                label = "Save State",
                icon = Icons.Default.Save,
                chipBg = green.copy(alpha = 0.85f),
                chipEnabled = true,
                onChip = onSave,
                accentColor = green,
                undoEnabled = canUndoSave,
                onUndo = onUndoSave,
            )

            SaveLoadRow(
                label = "Load State",
                icon = Icons.Default.Restore,
                chipBg = BLUE.copy(alpha = 0.85f),
                disabledChipBg = BLUE.copy(alpha = 0.15f),
                chipEnabled = hasSaveState,
                onChip = onLoad,
                accentColor = BLUE,
                undoEnabled = canUndoLoad,
                onUndo = onUndoLoad,
            )
        }
    }
}

@Composable
private fun SaveLoadRow(
    label: String,
    icon: ImageVector,
    chipBg: Color,
    disabledChipBg: Color = chipBg.copy(alpha = 0.10f),
    chipEnabled: Boolean,
    onChip: () -> Unit,
    accentColor: Color,
    undoEnabled: Boolean,
    onUndo: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Chip(
            modifier = Modifier.weight(1f),
            onClick = onChip,
            enabled = chipEnabled,
            colors = ChipDefaults.chipColors(
                backgroundColor = chipBg,
                disabledBackgroundColor = disabledChipBg,
            ),
            label = { androidx.wear.compose.material.Text(label) },
            icon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(ChipDefaults.IconSize),
                )
            },
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onUndo,
            enabled = undoEnabled,
            modifier = Modifier.size(52.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color.White.copy(alpha = 0.12f),
                disabledBackgroundColor = Color.White.copy(alpha = 0.06f),
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                tint = accentColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
