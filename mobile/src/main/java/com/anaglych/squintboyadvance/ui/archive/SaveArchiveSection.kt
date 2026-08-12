package com.anaglych.squintboyadvance.ui.archive

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import com.anaglych.squintboyadvance.shared.util.SaveSizeCheck
import com.anaglych.squintboyadvance.shared.util.SaveValidation
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
) {
    val context = LocalContext.current
    val available by SaveSyncGate.isAvailable(context).collectAsState()
    if (!available) return

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
    val pinnedDays by vm.pinnedDays.collectAsState()
    val oldestMonth by vm.oldestMonth.collectAsState()
    val selectedDay by vm.selectedDay.collectAsState()
    val folderMissing by vm.folderMissing.collectAsState()
    val restoreState by vm.restoreState.collectAsState()

    var pendingRestore by remember { mutableStateOf<ArchivedSaveEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<ArchivedSaveEntity?>(null) }
    var pendingNote by remember { mutableStateOf<ArchivedSaveEntity?>(null) }
    var pendingFileRestore by remember { mutableStateOf<PickedSaveFile?>(null) }

    // Manual upload: with sync on, the legacy backups UI is hidden, so this is
    // the way to send an arbitrary save file from phone storage to the watch.
    val filePickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { pendingFileRestore = resolvePickedFile(context, it) }
    }

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
            IconButton(
                onClick = { filePickLauncher.launch(arrayOf("*/*")) },
                enabled = watchConnected,
            ) {
                Icon(
                    Icons.Default.UploadFile,
                    contentDescription = "Send a save file to the watch",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

        if (saves.isEmpty()) {
            EmptyArchiveCard()
        } else {
            MonthHeatmap(
                dayCounts = dayCounts,
                pinnedDays = pinnedDays,
                selectedDay = selectedDay,
                onDayClick = { vm.selectDay(it) },
                oldestMonth = oldestMonth,
            )
            // The selected day's saves, always visible below the calendar.
            AnimatedContent(
                targetState = selectedDay,
                transitionSpec = {
                    (expandVertically(expandFrom = Alignment.Top) + fadeIn())
                        .togetherWith(shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut())
                },
                label = "day-filter",
            ) { day ->
                val shown = saves.filter { sameLocalDay(it.timestampMs, day) }
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(
                        dayHeaderFormat.format(day),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (shown.isEmpty()) {
                        Text(
                            "No saves on this day",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    for (save in shown) {
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
    }

    // ── Restore confirmation ────────────────────────────────────────────
    pendingRestore?.let { save ->
        RestoreConfirmDialog(
            title = "Restore to Watch",
            body = "The save from ${formatStamp(save.timestampMs)} will replace the " +
                "current save on your watch.",
            slideText = "Slide to restore",
            restoreState = restoreState,
            onConfirm = { vm.restoreToWatch(save) },
            onClose = {
                pendingRestore = null
                vm.clearRestoreMessage()
            },
        )
    }

    // ── Send-file confirmation ──────────────────────────────────────────
    pendingFileRestore?.let { picked ->
        val sizeWarning =
            if (picked.sizeBytes >= 0 &&
                SaveValidation.check(picked.sizeBytes, romId) != SaveSizeCheck.VALID
            ) "\n\nNote: this file size is unusual for this game — it may not be compatible."
            else ""
        RestoreConfirmDialog(
            title = "Send Save to Watch",
            body = "\"${picked.name}\" will replace the current save on your watch.$sizeWarning",
            slideText = "Slide to send",
            restoreState = restoreState,
            onConfirm = { vm.restoreFromFile(picked.uri) },
            onClose = {
                pendingFileRestore = null
                vm.clearRestoreMessage()
            },
        )
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

/**
 * Confirmation dialog shared by archive restore and send-from-file: slide to
 * confirm, then progress/result driven by [restoreState], auto-closing shortly
 * after success.
 */
@Composable
private fun RestoreConfirmDialog(
    title: String,
    body: String,
    slideText: String,
    restoreState: ArchiveRestoreState,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    LaunchedEffect(restoreState.message) {
        if (restoreState.message != null && !restoreState.isError && !restoreState.inProgress) {
            delay(1200)
            onClose()
        }
    }
    Dialog(onDismissRequest = { if (!restoreState.inProgress) onClose() }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    body,
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
                        text = slideText,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onConfirmed = onConfirm,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onClose,
                    enabled = !restoreState.inProgress,
                    modifier = Modifier.align(Alignment.End),
                ) { Text(if (restoreState.message != null) "Close" else "Cancel") }
            }
        }
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
private fun ArchivePromoCard(onOpenSetup: () -> Unit) {
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
                    Text("Automatic Save Archive", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Every save on your watch, archived and synced automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenSetup, modifier = Modifier.align(Alignment.End)) {
                Text("Set up")
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

private data class PickedSaveFile(val uri: Uri, val name: String, val sizeBytes: Long)

/** Display name + size for a picked document; size is -1 when the provider doesn't report it. */
private fun resolvePickedFile(context: Context, uri: Uri): PickedSaveFile {
    var name = uri.lastPathSegment ?: "save file"
    var size = -1L
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
            if (nameIdx >= 0) c.getString(nameIdx)?.let { name = it }
            if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
        }
    }
    return PickedSaveFile(uri, name, size)
}

private val dayHeaderFormat = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
private val stampFormat = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())

private fun formatStamp(timestampMs: Long): String =
    stampFormat.format(Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()))

private fun sameLocalDay(timestampMs: Long, day: LocalDate): Boolean =
    Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()).toLocalDate() == day
