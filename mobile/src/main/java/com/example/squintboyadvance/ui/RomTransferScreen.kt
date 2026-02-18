package com.example.squintboyadvance.ui

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RomTransferScreen(viewModel: RomTransferViewModel = viewModel()) {
    val roms by viewModel.roms.collectAsStateWithLifecycle()
    val watchConnected by viewModel.watchConnected.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addRoms(uris)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Squint Boy Advance") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                actions = {
                    if (roms.any { it.status == TransferStatus.PENDING || it.status == TransferStatus.ERROR }) {
                        TextButton(
                            onClick = { viewModel.sendAll() },
                            enabled = watchConnected && !sending,
                        ) {
                            Text("Send All")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { filePicker.launch(arrayOf("*/*")) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Select ROMs")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Connection status
            ConnectionStatusBar(watchConnected) { viewModel.refreshConnectionStatus() }

            // Active transfer progress
            val activeTransfers = roms.filter { it.status == TransferStatus.SENDING }
            if (activeTransfers.isNotEmpty()) {
                val overallProgress = activeTransfers.map { it.progress }.average().toFloat()
                LinearProgressIndicator(
                    progress = { overallProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            if (roms.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Tap + to select ROM files",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(roms, key = { it.uri.toString() }) { rom ->
                        RomCard(
                            item = rom,
                            watchConnected = watchConnected,
                            sending = sending,
                            onSend = { viewModel.sendRom(rom) },
                            onRemove = { viewModel.removeRom(rom) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusBar(connected: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Watch,
            contentDescription = null,
            tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (connected) "Watch: Connected" else "Watch: Disconnected",
            style = MaterialTheme.typography.bodyMedium,
            color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (connected) {
            Spacer(Modifier.width(6.dp))
            Text("●", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onRefresh) {
            Text("Refresh", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RomCard(
    item: RomTransferItem,
    watchConnected: Boolean,
    sending: Boolean,
    onSend: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val badgeColor = when (item.systemType) {
        SystemType.GB -> Color(0xFF306230)
        SystemType.GBC -> Color(0xFFDA70D6)
        SystemType.GBA -> Color(0xFF6A5ACD)
        SystemType.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
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
                    item.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
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
                    Text(
                        item.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            when (item.status) {
                TransferStatus.PENDING -> {
                    IconButton(
                        onClick = onSend,
                        enabled = watchConnected && !sending,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(20.dp))
                    }
                }
                TransferStatus.SENDING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TransferStatus.COMPLETE -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Complete",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
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
