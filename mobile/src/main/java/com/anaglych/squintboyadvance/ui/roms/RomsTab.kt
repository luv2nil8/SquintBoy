package com.anaglych.squintboyadvance.ui.roms

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anaglych.squintboyadvance.shared.model.SystemType
import com.anaglych.squintboyadvance.shared.model.WatchRomEntry
import com.anaglych.squintboyadvance.ui.RomPickerTrigger
import com.anaglych.squintboyadvance.ui.RomTransferItem
import com.anaglych.squintboyadvance.ui.RomTransferViewModel
import com.anaglych.squintboyadvance.ui.TransferStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RomsTab(
    watchConnected: Boolean,
    onRomSelected: (WatchRomEntry) -> Unit,
    transferViewModel: RomTransferViewModel = viewModel(),
    watchRomListViewModel: WatchRomListViewModel = viewModel(),
) {
    // Note: watchRomListViewModel may be hoisted from CompanionApp (same Activity scope = same instance)
    val roms by transferViewModel.roms.collectAsStateWithLifecycle()
    val sending by transferViewModel.sending.collectAsStateWithLifecycle()
    val watchRoms by watchRomListViewModel.watchRoms.collectAsStateWithLifecycle()
    val isLoading by watchRomListViewModel.isLoading.collectAsStateWithLifecycle()
    val displayNames by watchRomListViewModel.displayNames.collectAsStateWithLifecycle()

    // URIs currently animating out (will be removed from ViewModel after animation)
    var dismissingUris by remember { mutableStateOf(setOf<Uri>()) }
    val scope = rememberCoroutineScope()

    // System filter for the watch library
    var filterSystem by remember { mutableStateOf<SystemType?>(null) }
    val filteredWatchRoms = remember(watchRoms, filterSystem) {
        if (filterSystem == null) watchRoms else watchRoms.filter { it.systemType == filterSystem }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) transferViewModel.addRoms(uris)
    }

    // Watch can request the ROM picker via the Wearable message layer
    val shouldOpenPicker by RomPickerTrigger.shouldOpen.collectAsStateWithLifecycle()
    LaunchedEffect(shouldOpenPicker) {
        if (shouldOpenPicker) {
            filePicker.launch(arrayOf("*/*"))
            RomPickerTrigger.consume()
        }
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
            // ── Transfer section ────────────────────────────────────────
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
                            ) { Text("Send All") }
                        }
                    }
                }

                val activeTransfers = roms.filter { it.status == TransferStatus.SENDING }
                if (activeTransfers.isNotEmpty()) {
                    item {
                        LinearProgressIndicator(
                            progress = { activeTransfers.map { it.progress }.average().toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }

                items(roms, key = { it.uri.toString() }) { rom ->
                    AnimatedVisibility(
                        visible = rom.uri !in dismissingUris,
                        exit = slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(300),
                        ) + shrinkVertically(animationSpec = tween(300, delayMillis = 250)),
                    ) {
                        val dismissAction: () -> Unit = {
                            dismissingUris = dismissingUris + rom.uri
                            scope.launch {
                                delay(600)
                                transferViewModel.removeRom(rom)
                                dismissingUris = dismissingUris - rom.uri
                                if (watchConnected) watchRomListViewModel.requestRomList()
                            }
                        }
                        TransferRomCard(
                            item = rom,
                            watchConnected = watchConnected,
                            sending = sending,
                            onSend = { transferViewModel.sendRom(rom) },
                            onDismiss = if (rom.status == TransferStatus.COMPLETE) dismissAction else null,
                            onCancel = if (rom.status == TransferStatus.ERROR) dismissAction else null,
                        )
                    }
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            }

            // ── Library heading + filter pills ──────────────────────────
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Library",
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
                    SystemFilterPills(
                        selected = filterSystem,
                        onSelect = { filterSystem = it },
                    )
                }
            }

            // ── Watch library items ─────────────────────────────────────
            // Show spinner only when syncing with an empty list.
            // If we have cached data, render it while the background sync runs.
            if (isLoading && filteredWatchRoms.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else if (filteredWatchRoms.isEmpty()) {
                item {
                    Text(
                        if (filterSystem != null) "No ${filterSystem?.name} ROMs on watch"
                        else if (watchConnected) "No ROMs on watch"
                        else "Connect watch to sync library",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(filteredWatchRoms, key = { it.romId }) { entry ->
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { visible = true }
                    AnimatedVisibility(
                        visible = visible,
                        enter = slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(300),
                        ) + fadeIn(animationSpec = tween(300)),
                    ) {
                        WatchRomCard(
                            entry = entry,
                            displayName = displayNames[entry.romId] ?: entry.romId.substringBeforeLast('.'),
                            onClick = { onRomSelected(entry) },
                        )
                    }
                }
                // Subtle sync indicator at the bottom when refreshing cached data
                if (isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Filter pills ─────────────────────────────────────────────────────────────

@Composable
private fun SystemFilterPills(
    selected: SystemType?,
    onSelect: (SystemType?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        PillChip(label = "All", active = selected == null, onClick = { onSelect(null) })
        Spacer(Modifier.width(6.dp))
        PillChip(label = "GBA", active = selected == SystemType.GBA, onClick = { onSelect(SystemType.GBA) })
        Spacer(Modifier.width(6.dp))
        PillChip(label = "GBC", active = selected == SystemType.GBC, onClick = { onSelect(SystemType.GBC) })
        Spacer(Modifier.width(6.dp))
        PillChip(label = "GB", active = selected == SystemType.GB, onClick = { onSelect(SystemType.GB) })
    }
}

@Composable
private fun PillChip(label: String, active: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = active,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

// ── Transfer card ─────────────────────────────────────────────────────────────

@Composable
private fun TransferRomCard(
    item: RomTransferItem,
    watchConnected: Boolean,
    sending: Boolean,
    onSend: () -> Unit,
    onDismiss: (() -> Unit)?,
    onCancel: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val badgeColor = when (item.systemType) {
        com.anaglych.squintboyadvance.shared.model.SystemType.GB -> Color(0xFF306230)
        com.anaglych.squintboyadvance.shared.model.SystemType.GBC -> Color(0xFFDA70D6)
        com.anaglych.squintboyadvance.shared.model.SystemType.GBA -> Color(0xFF6A5ACD)
        null -> MaterialTheme.colorScheme.surfaceVariant
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
                        Text(item.systemType?.displayName ?: "?", style = MaterialTheme.typography.labelSmall)
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
                if (item.status == TransferStatus.ERROR) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Transfer failed. Try again?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            when (item.status) {
                TransferStatus.PENDING -> {
                    IconButton(onClick = onSend, enabled = watchConnected && !sending) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    // No remove button — transfer items are not removable
                }
                TransferStatus.SENDING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TransferStatus.COMPLETE -> {
                    // Tapping the checkmark dismisses the tile and refreshes the library
                    IconButton(onClick = { onDismiss?.invoke() }) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                TransferStatus.ERROR -> {
                    onCancel?.let {
                        IconButton(onClick = it) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onSend, enabled = watchConnected && !sending) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Retry",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

// ── Watch library card ────────────────────────────────────────────────────────

@Composable
private fun WatchRomCard(entry: WatchRomEntry, displayName: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val badgeColor = when (entry.systemType) {
        SystemType.GB -> Color(0xFF306230)
        SystemType.GBC -> Color(0xFFDA70D6)
        SystemType.GBA -> Color(0xFF6A5ACD)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
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
                    displayName,
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
            // Chevron hint that the card is tappable
            Text(
                "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
