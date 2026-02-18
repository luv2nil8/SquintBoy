package com.example.squintboyadvance.ui.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.squintboyadvance.shared.model.GbPalette
import com.example.squintboyadvance.shared.model.ScaleMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchSettingsScreen(
    watchConnected: Boolean,
    viewModel: WatchSettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    if (settings == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isLoading) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Loading settings...")
                } else {
                    Text(
                        if (watchConnected) "Load settings from watch" else "Connect watch first",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadSettings() },
                        enabled = watchConnected && !isLoading,
                    ) {
                        Text("Load from Watch")
                    }
                }
            }
        }
        return
    }

    val s = settings!!

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Actions ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.loadSettings() },
                    enabled = watchConnected && !isLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Reload")
                }
                Button(
                    onClick = { viewModel.pushToWatch() },
                    enabled = watchConnected && !isSaving && viewModel.isDirty,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isSaving) "Saving..." else "Save to Watch")
                }
            }
        }

        // ── Audio ──
        item { SectionHeader("Audio") }
        item {
            SwitchSetting("Audio Enabled", s.audioEnabled) {
                viewModel.updateLocal { it.copy(audioEnabled = !it.audioEnabled) }
            }
        }
        item {
            SliderSetting("Volume", "${(s.audioVolume * 100).toInt()}%", s.audioVolume, 0f..1f) { v ->
                viewModel.updateLocal { it.copy(audioVolume = v) }
            }
        }

        // ── Video ──
        item { SectionHeader("Video") }
        item {
            DropdownSetting(
                label = "GBA Scale",
                selected = s.gbaScaleMode.displayName,
                options = ScaleMode.entries.map { it.displayName },
                onSelect = { name ->
                    val mode = ScaleMode.entries.first { it.displayName == name }
                    viewModel.updateLocal { it.copy(gbaScaleMode = mode) }
                },
            )
        }
        if (s.gbaScaleMode == ScaleMode.CUSTOM) {
            item {
                SliderSetting("GBA Scale", "${"%.1f".format(s.gbaCustomScale)}x", s.gbaCustomScale, 0.5f..4f) { v ->
                    viewModel.updateLocal { it.copy(gbaCustomScale = v) }
                }
            }
        }
        item {
            SwitchSetting("GBA Bilinear Filter", s.gbaFilterEnabled) {
                viewModel.updateLocal { it.copy(gbaFilterEnabled = !it.gbaFilterEnabled) }
            }
        }
        item {
            DropdownSetting(
                label = "GB Scale",
                selected = s.gbScaleMode.displayName,
                options = ScaleMode.entries.map { it.displayName },
                onSelect = { name ->
                    val mode = ScaleMode.entries.first { it.displayName == name }
                    viewModel.updateLocal { it.copy(gbScaleMode = mode) }
                },
            )
        }
        if (s.gbScaleMode == ScaleMode.CUSTOM) {
            item {
                SliderSetting("GB Scale", "${"%.1f".format(s.gbCustomScale)}x", s.gbCustomScale, 0.5f..4f) { v ->
                    viewModel.updateLocal { it.copy(gbCustomScale = v) }
                }
            }
        }
        item {
            SwitchSetting("GB Bilinear Filter", s.gbFilterEnabled) {
                viewModel.updateLocal { it.copy(gbFilterEnabled = !it.gbFilterEnabled) }
            }
        }

        // ── Gameplay ──
        item { SectionHeader("Gameplay") }
        item {
            SliderSetting(
                "Frameskip",
                if (s.frameskip < 0) "Auto" else "${s.frameskip}",
                s.frameskip.toFloat(),
                -1f..4f,
                steps = 4,
            ) { v ->
                viewModel.updateLocal { it.copy(frameskip = v.toInt()) }
            }
        }
        item {
            SwitchSetting("Show FPS", s.showFps) {
                viewModel.updateLocal { it.copy(showFps = !it.showFps) }
            }
        }
        item {
            SwitchSetting("Auto-Save", s.autoSaveEnabled) {
                viewModel.updateLocal { it.copy(autoSaveEnabled = !it.autoSaveEnabled) }
            }
        }
        if (s.autoSaveEnabled) {
            item {
                SliderSetting("Interval", "${s.autoSaveIntervalSec}s", s.autoSaveIntervalSec.toFloat(), 15f..300f, steps = 18) { v ->
                    viewModel.updateLocal { it.copy(autoSaveIntervalSec = v.toInt()) }
                }
            }
        }
        item {
            SliderSetting("Turbo", "${"%.1f".format(s.turboSpeed)}x", s.turboSpeed, 1.5f..4f) { v ->
                viewModel.updateLocal { it.copy(turboSpeed = v) }
            }
        }

        // ── GB Palette ──
        item { SectionHeader("GB Palette") }
        item {
            DropdownSetting(
                label = "Palette",
                selected = s.colorPalette.displayName,
                options = GbPalette.entries.map { it.displayName },
                onSelect = { name ->
                    val palette = GbPalette.entries.first { it.displayName == name }
                    viewModel.updateLocal { it.copy(colorPalette = palette) }
                },
            )
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SwitchSetting(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun SliderSetting(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSetting(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 16.dp))
        Spacer(Modifier.weight(1f))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(0.5f),
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true,
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { onSelect(option); expanded = false },
                    )
                }
            }
        }
    }
}
