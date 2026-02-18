package com.example.squintboyadvance.ui.saves

import android.text.format.Formatter
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.squintboyadvance.shared.model.SaveFileEntry
import com.example.squintboyadvance.shared.model.SaveFileType
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun SaveSyncScreen(
    watchConnected: Boolean,
    viewModel: SaveSyncViewModel = viewModel(),
) {
    val savesByRom by viewModel.savesByRom.collectAsStateWithLifecycle()
    val localSaves by viewModel.localSaves.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val transferStatus by viewModel.transferStatus.collectAsStateWithLifecycle()

    val allRomIds = (savesByRom.keys + localSaves.keys).sorted()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Save Files",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { viewModel.requestSaveList() },
                    enabled = watchConnected && !isLoading,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        } else if (allRomIds.isEmpty()) {
            item {
                Text(
                    if (watchConnected) "No saves found. Tap refresh to scan watch." else "Connect watch to view saves",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            allRomIds.forEach { romId ->
                val watchSaves = savesByRom[romId].orEmpty()
                val phoneSaves = localSaves[romId].orEmpty()
                val romTransferStatus = transferStatus[romId]

                item(key = "header_$romId") {
                    RomSaveHeader(
                        romId = romId,
                        watchSaveCount = watchSaves.size,
                        transferStatus = romTransferStatus,
                        onBackupAll = { viewModel.pullSaves(romId) },
                        watchConnected = watchConnected,
                    )
                }

                // Watch saves
                if (watchSaves.isNotEmpty()) {
                    items(watchSaves, key = { "watch_${it.romId}_${it.fileName}" }) { entry ->
                        WatchSaveCard(entry)
                    }
                }

                // Local saves
                if (phoneSaves.isNotEmpty()) {
                    item(key = "local_header_$romId") {
                        Text(
                            "Local Backups",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(phoneSaves, key = { "local_${romId}_${it.name}" }) { file ->
                        val key = "$romId/${file.name}"
                        LocalSaveCard(
                            file = file,
                            transferStatus = transferStatus[key],
                            onRestore = { viewModel.pushSave(romId, file) },
                            watchConnected = watchConnected,
                        )
                    }
                }

                item(key = "divider_$romId") {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun RomSaveHeader(
    romId: String,
    watchSaveCount: Int,
    transferStatus: TransferStatus?,
    onBackupAll: () -> Unit,
    watchConnected: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                romId.substringBeforeLast('.'),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (transferStatus != null) {
                Text(
                    transferStatus.message ?: transferStatus.state.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (transferStatus.state) {
                        TransferState.DONE -> MaterialTheme.colorScheme.primary
                        TransferState.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        if (watchSaveCount > 0) {
            if (transferStatus?.state == TransferState.TRANSFERRING) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                TextButton(
                    onClick = onBackupAll,
                    enabled = watchConnected,
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Backup All")
                }
            }
        }
    }
}

@Composable
private fun WatchSaveCard(entry: SaveFileEntry) {
    val context = LocalContext.current
    val typeBadge = when (entry.type) {
        SaveFileType.SRAM_LIVE -> "SRAM" to Color(0xFF4CAF50)
        SaveFileType.SRAM_BACKUP -> "Backup" to Color(0xFFFF9800)
        SaveFileType.STATE -> "State" to Color(0xFF2196F3)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.fileName, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(containerColor = typeBadge.second, contentColor = Color.White) {
                        Text(typeBadge.first, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Formatter.formatShortFileSize(context, entry.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(entry.lastModified)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalSaveCard(
    file: File,
    transferStatus: TransferStatus?,
    onRestore: () -> Unit,
    watchConnected: Boolean,
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyMedium)
                Row {
                    Text(
                        Formatter.formatShortFileSize(context, file.length()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(file.lastModified())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (transferStatus != null && transferStatus.state != TransferState.IDLE) {
                    Text(
                        transferStatus.message ?: transferStatus.state.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (transferStatus.state) {
                            TransferState.DONE -> MaterialTheme.colorScheme.primary
                            TransferState.ERROR -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (transferStatus?.state == TransferState.TRANSFERRING) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRestore, enabled = watchConnected) {
                    Icon(Icons.Default.CloudUpload, contentDescription = "Restore to watch")
                }
            }
        }
    }
}
