package com.anaglych.squintboyadvance.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anaglych.squintboyadvance.shared.model.ControllerLayout
import com.anaglych.squintboyadvance.shared.model.GbColorPalette
import com.anaglych.squintboyadvance.shared.model.ScaleMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchSettingsScreen(
    watchConnected: Boolean,
    onOpenLicenses: () -> Unit = {},
    viewModel: WatchSettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

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
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
            FrameskipSetting("GBA Frameskip", s.gbaFrameskip) { value ->
                viewModel.updateLocal { it.copy(gbaFrameskip = value) }
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
        item {
            FrameskipSetting("GB Frameskip", s.gbFrameskip) { value ->
                viewModel.updateLocal { it.copy(gbFrameskip = value) }
            }
        }

        // ── Controls ──
        item { SectionHeader("Controls") }
        item {
            SwitchSetting("Overlay Visible", s.controllerLayout.visible) {
                viewModel.updateLocal { it.copy(controllerLayout = it.controllerLayout.copy(visible = !it.controllerLayout.visible)) }
            }
        }
        item {
            SliderSetting("Outline Opacity", "${(s.controllerLayout.buttonOpacity * 100).toInt()}%", s.controllerLayout.buttonOpacity, 0f..1f, steps = 9) { v ->
                viewModel.updateLocal { it.copy(controllerLayout = it.controllerLayout.copy(buttonOpacity = v)) }
            }
        }
        item {
            SliderSetting("Pressed Opacity", "${(s.controllerLayout.pressedOpacity * 100).toInt()}%", s.controllerLayout.pressedOpacity, 0f..0.5f, steps = 4) { v ->
                viewModel.updateLocal { it.copy(controllerLayout = it.controllerLayout.copy(pressedOpacity = v)) }
            }
        }
        item {
            SliderSetting("Label Opacity", "${(s.controllerLayout.labelOpacity * 100).toInt()}%", s.controllerLayout.labelOpacity, 0f..1f, steps = 9) { v ->
                viewModel.updateLocal { it.copy(controllerLayout = it.controllerLayout.copy(labelOpacity = v)) }
            }
        }
        item {
            val labelSizeName = when (s.controllerLayout.labelSize.toInt()) {
                9 -> "Tiny"; 11 -> "Small"; 13 -> "Normal"; 15 -> "Large"; 17 -> "Huge"
                else -> "${s.controllerLayout.labelSize.toInt()}sp"
            }
            SliderSetting("Label Size", labelSizeName, s.controllerLayout.labelSize, 9f..17f, steps = 3) { v ->
                viewModel.updateLocal { it.copy(controllerLayout = it.controllerLayout.copy(labelSize = v)) }
            }
        }
        item {
            SwitchSetting("Haptic Feedback", s.controllerLayout.hapticFeedback) {
                val newValue = !s.controllerLayout.hapticFeedback
                viewModel.updateLocal { it.copy(controllerLayout = it.controllerLayout.copy(hapticFeedback = newValue)) }
                if (newValue) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }

        item {
            ResetControlsButton(onClick = {
                viewModel.updateLocal { it.copy(controllerLayout = ControllerLayout()) }
            })
        }

        // ── GB Palette ──
        item { SectionHeader("GB Palette") }
        item {
            PaletteSetting(
                selectedIndex = s.gbPaletteIndex,
                onSelect = { index -> viewModel.updateLocal { it.copy(gbPaletteIndex = index) } },
            )
        }

        item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }
        item {
            val uriHandler = LocalUriHandler.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri("https://lospec.com/shop") }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Palettes by Lospec", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "lospec.com — support their work",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenLicenses)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Open Source Licenses", style = MaterialTheme.typography.bodyMedium)
                Text("›", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun FrameskipSetting(label: String, current: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Off", 1 to "1", 2 to "2", 3 to "3").forEach { (value, chip) ->
                FilterChip(
                    selected = current == value,
                    onClick = { onSelect(value) },
                    label = { Text(chip) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteSetting(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = GbColorPalette.ALL.getOrNull(selectedIndex)
        ?: GbColorPalette.ALL[GbColorPalette.DEFAULT_INDEX]

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Palette", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 16.dp))
        Spacer(Modifier.weight(1f))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selected.name,
                onValueChange = {},
                readOnly = true,
                leadingIcon = { PaletteSwatch(palette = selected, size = 20.dp) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(0.6f),
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true,
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                GbColorPalette.ALL.forEachIndexed { index, palette ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                PaletteSwatch(palette = palette, size = 22.dp)
                                Text(palette.name, style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        onClick = { onSelect(index); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun PaletteSwatch(palette: GbColorPalette, size: Dp) {
    val c0 = Color(palette.c0)  // darkest — top-left
    val c1 = Color(palette.c1)  // top-right
    val c2 = Color(palette.c2)  // bottom-left
    val c3 = Color(palette.c3)  // lightest — bottom-right
    Canvas(modifier = Modifier.size(size)) {
        drawArc(color = c0, startAngle = 180f, sweepAngle = 90f, useCenter = true)
        drawArc(color = c1, startAngle = 270f, sweepAngle = 90f, useCenter = true)
        drawArc(color = c2, startAngle = 90f,  sweepAngle = 90f, useCenter = true)
        drawArc(color = c3, startAngle = 0f,   sweepAngle = 90f, useCenter = true)
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

@Composable
private fun ResetControlsButton(onClick: () -> Unit) {
    val crimson = Color(0xFFEC1358)
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        border = androidx.compose.foundation.BorderStroke(1.dp, crimson),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = crimson),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Reset Controls to Defaults")
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
