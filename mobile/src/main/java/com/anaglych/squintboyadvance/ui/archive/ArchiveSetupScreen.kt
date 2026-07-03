package com.anaglych.squintboyadvance.ui.archive

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Save Sync settings: opt-in toggle, archive folder, retention policy.
 * The Drive connection card joins this screen in the cloud phase.
 */
@Composable
fun ArchiveSetupScreen(viewModel: ArchiveSetupViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsState()
    val folderMissing by viewModel.folderMissing.collectAsState()
    val rescanRunning by viewModel.rescanRunning.collectAsState()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) viewModel.setFolder(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Master toggle ────────────────────────────────────────────────
        SetupCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Save Sync", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Every save on your watch, archived here automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { viewModel.setEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }

        // ── Archive folder ───────────────────────────────────────────────
        SetupCard {
            Text("Archive Folder", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            val label = viewModel.folderLabel(settings.treeUri)
            if (settings.enabled && settings.treeUri == null) {
                WarningRow("Choose a folder to start archiving")
            }
            if (folderMissing) {
                WarningRow("Archive folder unavailable — choose it again to relink")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    label ?: "Not set",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (label != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { folderPicker.launch(null) }) {
                    Text(if (settings.treeUri == null) "Choose Folder" else "Change Folder")
                }
                if (settings.treeUri != null) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { viewModel.rescan() },
                        enabled = !rescanRunning,
                    ) {
                        Text(if (rescanRunning) "Scanning…" else "Rescan")
                    }
                }
            }
        }

        // ── Retention ────────────────────────────────────────────────────
        SetupCard {
            Text("Retention", style = MaterialTheme.typography.titleMedium)
            Text(
                "Pinned saves are always kept",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Text("Keep saves for", style = MaterialTheme.typography.labelLarge)
            PillRow(
                options = listOf("30 days" to 30, "90 days" to 90, "1 year" to 365, "Forever" to null),
                selected = settings.maxAgeDays,
                onSelect = { viewModel.setMaxAgeDays(it) },
            )
            Spacer(Modifier.height(8.dp))

            Text("Saves per game", style = MaterialTheme.typography.labelLarge)
            PillRow(
                options = listOf("20" to 20, "50" to 50, "100" to 100, "Unlimited" to null),
                selected = settings.maxCountPerGame,
                onSelect = { viewModel.setMaxCountPerGame(it) },
            )
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Thin older saves", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "After 7 days, keep one save per day",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.thinAfterDays != null,
                    onCheckedChange = { viewModel.setThinning(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SetupCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun WarningRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun <T> PillRow(
    options: List<Pair<String, T?>>,
    selected: T?,
    onSelect: (T?) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((label, value) in options) {
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}
