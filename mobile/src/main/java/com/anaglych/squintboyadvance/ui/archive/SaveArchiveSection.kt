package com.anaglych.squintboyadvance.ui.archive

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anaglych.squintboyadvance.data.db.ArchivedSaveEntity
import com.anaglych.squintboyadvance.ui.components.SlideToConfirm
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The per-game save archive: calendar/list toggle, day expansion, restore,
 * pin, note, delete. Lives at the top of the Saves tab in RomManagementScreen.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SaveArchiveSection(
    romId: String,
    watchConnected: Boolean,
    onOpenSetup: () -> Unit,
    onUpgrade: () -> Unit = {},
) {
    val context = LocalContext.current
    val available by SaveSyncGate.isAvailable(context).collectAsState()
    if (!available) {
        ArchivePromoCard(onOpenSetup = onUpgrade, locked = true)
        return
    }

    val application = context.applicationContext as Application
    val vm: SaveArchiveViewModel = viewModel(
        factory = SaveArchiveViewModel.factory(application, romId),
        key = "archive_$romId",
    )

    val syncSettings by vm.syncSettings.collectAsState()
    if (!syncSettings.enabled) {
        ArchivePromoCard(onOpenSetup)
        return
    }

    val saves by vm.saves.collectAsState()
    val dayCounts by vm.dayCounts.collectAsState()
    val viewMode by vm.viewMode.collectAsState()
    val selectedDay by vm.selectedDay.collectAsState()
    val folderMissing by vm.folderMissing.collectAsState()
    val restoreState by vm.restoreState.collectAsState()

    var pendingRestore by remember { mutableStateOf<ArchivedSaveEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<ArchivedSaveEntity?>(null) }
    var pendingNote by remember { mutableStateOf<ArchivedSaveEntity?>(null) }

    Column {
        // ── Header ───────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Save Archive",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            ViewModePill("Calendar", viewMode == ArchiveViewMode.CALENDAR) {
                vm.setViewMode(ArchiveViewMode.CALENDAR)
            }
            Spacer(Modifier.width(6.dp))
            ViewModePill("List", viewMode == ArchiveViewMode.LIST) {
                vm.setViewMode(ArchiveViewMode.LIST)
            }
            IconButton(onClick = onOpenSetup) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Save Sync settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (folderMissing) {
            WarningBanner("Archive folder unavailable — relink in settings", onOpenSetup)
        }
        val driveErrors by vm.driveErrors.collectAsState()
        if ("QUOTA" in driveErrors) {
            WarningBanner("Google Drive is full — uploads paused", onOpenSetup)
        }
        if ("AUTH" in driveErrors) {
            WarningBanner("Reconnect Google Drive to resume syncing", onOpenSetup)
        }

        when {
            saves.isEmpty() -> EmptyArchiveCard()
            viewMode == ArchiveViewMode.CALENDAR -> {
                CalendarHeatmap(
                    dayCounts = dayCounts,
                    selectedDay = selectedDay,
                    onDayClick = { vm.selectDay(it) },
                )
                // The day "opens": its saves expand out below the tapped cell's grid.
                AnimatedContent(
                    targetState = selectedDay,
                    transitionSpec = {
                        (expandVertically(expandFrom = Alignment.Top) + fadeIn())
                            .togetherWith(shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut())
                    },
                    label = "day-expand",
                ) { day ->
                    if (day != null) {
                        DayPanel(
                            day = day,
                            saves = saves.filter { sameLocalDay(it.timestampMs, day) },
                            watchConnected = watchConnected,
                            onTogglePin = { vm.togglePin(it) },
                            onRestore = { pendingRestore = it },
                            onEditNote = { pendingNote = it },
                            onDelete = { pendingDelete = it },
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (save in saves) {
                    ArchivedSaveRow(
                        save = save,
                        watchConnected = watchConnected,
                        onTogglePin = { vm.togglePin(save) },
                        onRestore = { pendingRestore = save },
                        onEditNote = { pendingNote = save },
                        onDelete = { pendingDelete = save },
                    )
                }
            }
        }
    }

    // ── Restore confirmation ────────────────────────────────────────────
    pendingRestore?.let { save ->
        // Auto-close shortly after a successful restore.
        LaunchedEffect(restoreState.message) {
            if (restoreState.message != null && !restoreState.isError && !restoreState.inProgress) {
                delay(1200)
                pendingRestore = null
                vm.clearRestoreMessage()
            }
        }
        Dialog(onDismissRequest = {
            if (!restoreState.inProgress) {
                pendingRestore = null
                vm.clearRestoreMessage()
            }
        }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Restore to Watch",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The save from ${formatStamp(save.timestampMs)} will replace the " +
                            "current save on your watch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    when {
                        restoreState.inProgress -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.width(20.dp).height(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(restoreState.message ?: "Restoring…")
                        }
                        restoreState.message != null -> Text(
                            restoreState.message!!,
                            color = if (restoreState.isError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        )
                        else -> SlideToConfirm(
                            text = "Slide to restore",
                            accentColor = MaterialTheme.colorScheme.primary,
                            onConfirmed = { vm.restoreToWatch(save) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            pendingRestore = null
                            vm.clearRestoreMessage()
                        },
                        enabled = !restoreState.inProgress,
                        modifier = Modifier.align(Alignment.End),
                    ) { Text(if (restoreState.message != null) "Close" else "Cancel") }
                }
            }
        }
    }

    // ── Delete confirmation ─────────────────────────────────────────────
    pendingDelete?.let { save ->
        Dialog(onDismissRequest = { pendingDelete = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Delete Archived Save",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The save from ${formatStamp(save.timestampMs)} will be removed from " +
                            "the archive" +
                            (if (save.driveFileId != null) " and from Google Drive." else "."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    SlideToConfirm(
                        text = "Slide to delete",
                        accentColor = MaterialTheme.colorScheme.error,
                        onConfirmed = {
                            vm.deleteSave(save)
                            pendingDelete = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { pendingDelete = null },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Cancel") }
                }
            }
        }
    }

    // ── Note editor ─────────────────────────────────────────────────────
    pendingNote?.let { save ->
        var text by remember(save.id) { mutableStateOf(save.note ?: "") }
        AlertDialog(
            onDismissRequest = { pendingNote = null },
            title = { Text("Note") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("e.g. Before Elite Four") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setNote(save, text)
                    pendingNote = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { pendingNote = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun WarningBanner(text: String, onFix: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
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
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onFix) { Text("Fix") }
    }
}

@Composable
private fun DayPanel(
    day: LocalDate,
    saves: List<ArchivedSaveEntity>,
    watchConnected: Boolean,
    onTogglePin: (ArchivedSaveEntity) -> Unit,
    onRestore: (ArchivedSaveEntity) -> Unit,
    onEditNote: (ArchivedSaveEntity) -> Unit,
    onDelete: (ArchivedSaveEntity) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(
            dayHeaderFormat.format(day),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (save in saves) {
            ArchivedSaveRow(
                save = save,
                watchConnected = watchConnected,
                onTogglePin = { onTogglePin(save) },
                onRestore = { onRestore(save) },
                onEditNote = { onEditNote(save) },
                onDelete = { onDelete(save) },
            )
        }
    }
}

@Composable
private fun ArchivePromoCard(onOpenSetup: () -> Unit, locked: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Automatic Save Archive", style = MaterialTheme.typography.titleSmall)
                        if (locked) {
                            Spacer(Modifier.width(8.dp))
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ) { Text("PRO") }
                        }
                    }
                    Text(
                        "Every save on your watch, archived and synced automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenSetup, modifier = Modifier.align(Alignment.End)) {
                Text(if (locked) "Upgrade" else "Set up")
            }
        }
    }
}

@Composable
private fun EmptyArchiveCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(
            "Play on your watch — saves appear here automatically",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun ViewModePill(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    )
}

private val dayHeaderFormat = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
private val stampFormat = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())

private fun formatStamp(timestampMs: Long): String =
    stampFormat.format(Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()))

private fun sameLocalDay(timestampMs: Long, day: LocalDate): Boolean =
    Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()).toLocalDate() == day
