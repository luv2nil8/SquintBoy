package com.example.squintboyadvance.ui.roms

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.squintboyadvance.shared.model.SystemType
import com.example.squintboyadvance.shared.model.WatchRomEntry
import com.example.squintboyadvance.ui.RomTransferItem
import com.example.squintboyadvance.ui.RomTransferViewModel
import com.example.squintboyadvance.ui.TransferStatus

@Composable
fun RomsTab(
    watchConnected: Boolean,
    transferViewModel: RomTransferViewModel = viewModel(),
    watchRomListViewModel: WatchRomListViewModel = viewModel(),
) {
    val roms by transferViewModel.roms.collectAsStateWithLifecycle()
    val sending by transferViewModel.sending.collectAsStateWithLifecycle()
    val watchRoms by watchRomListViewModel.watchRoms.collectAsStateWithLifecycle()
    val isLoading by watchRomListViewModel.isLoading.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) transferViewModel.addRoms(uris)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { filePicker.launch(arrayOf("*/*")) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Select ROMs")
            }
        },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Transfer section ──
            if (roms.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Transfer",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.weight(1f))
                        if (roms.any { it.status == TransferStatus.PENDING || it.status == TransferStatus.ERROR }) {
                            TextButton(
                                onClick = { transferViewModel.sendAll() },
                                enabled = watchConnected && !sending,
                            ) {
                                Text("Send All")
                            }
                        }
                    }
                }

                val activeTransfers = roms.filter { it.status == TransferStatus.SENDING }
                if (activeTransfers.isNotEmpty()) {
                    item {
                        val overallProgress = activeTransfers.map { it.progress }.average().toFloat()
                        LinearProgressIndicator(
                            progress = { overallProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }

                items(roms, key = { it.uri.toString() }) { rom ->
                    TransferRomCard(
                        item = rom,
                        watchConnected = watchConnected,
                        sending = sending,
                        onSend = { transferViewModel.sendRom(rom) },
                        onRemove = { transferViewModel.removeRom(rom) },
                    )
                }
            }

            // ── Watch Library section ──
            item {
                if (roms.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Watch Library",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { watchRomListViewModel.requestRomList() },
                        enabled = watchConnected,
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
            } else if (watchRoms.isEmpty()) {
                item {
                    Text(
                        if (watchConnected) "No ROMs on watch" else "Connect watch to view library",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(watchRoms, key = { it.romId }) { entry ->
                    WatchRomCard(
                        entry = entry,
                        onDelete = { watchRomListViewModel.deleteRom(entry.romId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferRomCard(
    item: RomTransferItem,
    watchConnected: Boolean,
    sending: Boolean,
    onSend: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val badgeColor = when (item.systemType) {
        com.example.squintboyadvance.ui.SystemType.GB -> Color(0xFF306230)
        com.example.squintboyadvance.ui.SystemType.GBC -> Color(0xFFDA70D6)
        com.example.squintboyadvance.ui.SystemType.GBA -> Color(0xFF6A5ACD)
        com.example.squintboyadvance.ui.SystemType.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
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
                Text(item.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(containerColor = badgeColor, contentColor = Color.White) {
                        Text(item.systemType.label, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Formatter.formatShortFileSize(context, item.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.status == TransferStatus.SENDING) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
                if (item.status == TransferStatus.ERROR && item.errorMessage != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(item.errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.width(8.dp))

            when (item.status) {
                TransferStatus.PENDING -> {
                    IconButton(onClick = onSend, enabled = watchConnected && !sending) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(20.dp))
                    }
                }
                TransferStatus.SENDING -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                }
                TransferStatus.COMPLETE -> {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Complete", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
                TransferStatus.ERROR -> {
                    IconButton(onClick = onSend, enabled = watchConnected && !sending) {
                        Icon(Icons.Default.Error, contentDescription = "Retry", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchRomCard(entry: WatchRomEntry, onDelete: () -> Unit) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    val badgeColor = when (entry.systemType) {
        SystemType.GB -> Color(0xFF306230)
        SystemType.GBC -> Color(0xFFDA70D6)
        SystemType.GBA -> Color(0xFF6A5ACD)
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ROM?") },
            text = { Text("Delete ${entry.romId} and all its saves from the watch?") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
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
                Text(
                    entry.romId.substringBeforeLast('.'),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(containerColor = badgeColor, contentColor = Color.White) {
                        Text(entry.systemType.name, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Formatter.formatShortFileSize(context, entry.fileSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (entry.totalPlayTimeMs > 0) {
                        Spacer(Modifier.width(8.dp))
                        val hours = entry.totalPlayTimeMs / 3_600_000
                        val mins = (entry.totalPlayTimeMs % 3_600_000) / 60_000
                        Text(
                            "${hours}h ${mins}m",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
