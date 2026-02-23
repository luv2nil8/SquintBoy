package com.example.squintboyadvance.ui.roms

import android.app.Application
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.squintboyadvance.ui.components.SlideToConfirm
import java.text.DateFormat
import java.util.Date

@Composable
fun RomManagementScreen(
    romId: String,
    systemType: SystemType,
    watchConnected: Boolean,
    onRomDeleted: () -> Unit = {},
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: RomManagementViewModel = viewModel(
        factory = RomManagementViewModel.factory(application, romId),
        key = romId,
    )

    val watchSave by viewModel.watchSave.collectAsStateWithLifecycle()
    val isLoadingWatchSave by viewModel.isLoadingWatchSave.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()
    val backupTransfer by viewModel.backupTransfer.collectAsStateWithLifecycle()
    val uploadTransfer by viewModel.uploadTransfer.collectAsStateWithLifecycle()

    // State for the upload confirmation flow
    var pendingUpload by remember { mutableStateOf<SaveBackupEntry?>(null) }
    var showDeleteRomConfirm by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.importFromStorage(it) }
    }

    LaunchedEffect(Unit) {
        if (watchConnected) viewModel.refreshWatchSave()
    }

    val badgeColor = when (systemType) {
        SystemType.GB -> Color(0xFF306230)
        SystemType.GBC -> Color(0xFFDA70D6)
        SystemType.GBA -> Color(0xFF6A5ACD)
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── ROM header ──────────────────────────────────────────────
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(containerColor = badgeColor, contentColor = Color.White) {
                        Text(systemType.name, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = romId.substringBeforeLast('.'),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
            }

            // ── Current Save on Watch ───────────────────────────────────
            item {
                SectionHeader(
                    title = "Save on Watch",
                    action = {
                        IconButton(
                            onClick = { viewModel.refreshWatchSave() },
                            enabled = watchConnected && !isLoadingWatchSave,
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    },
                )
            }

            item {
                when {
                    isLoadingWatchSave -> Box(
                        Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                    watchSave != null -> WatchSaveCard(
                        entry = watchSave!!,
                        onBackup = { viewModel.backupToPhone() },
                        backupInProgress = backupTransfer.inProgress,
                        backupMessage = backupTransfer.message,
                        backupIsError = backupTransfer.isError,
                        watchConnected = watchConnected,
                    )
                    else -> Text(
                        text = if (watchConnected) "No save file found on watch" else "Connect watch to view save",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Phone Backups ───────────────────────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                SectionHeader(
                    title = "Backups on Phone",
                    action = {
                        IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Default.Add, contentDescription = "Import save from phone")
                        }
                    },
                )
            }

            if (backups.isEmpty()) {
                item {
                    Text(
                        "No backups yet. Tap \"Backup to Phone\" above to save a copy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(backups, key = { it.file.absolutePath }) { backup ->
                    BackupCard(
                        backup = backup,
                        onExport = { viewModel.exportBackup(backup, context) },
                        onDelete = { viewModel.deleteBackup(backup) },
                        onRename = { newName -> viewModel.renameBackup(backup, newName) },
                        onUpload = { pendingUpload = backup },
                        watchConnected = watchConnected,
                    )
                }
            }

            // ── Remove ROM ──────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDeleteRomConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Remove ROM from Watch")
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // ── Delete ROM confirmation overlay ────────────────────────────────────
    if (showDeleteRomConfirm) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showDeleteRomConfirm = false }) {
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Remove ROM",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "\"${romId.substringBeforeLast('.')}\" will be deleted from your watch. " +
                            "Your save backups on this phone are not affected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    SlideToConfirm(
                        text = "Slide to remove",
                        accentColor = MaterialTheme.colorScheme.error,
                        onConfirmed = {
                            showDeleteRomConfirm = false
                            viewModel.deleteRom(onDeleted = onRomDeleted)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { showDeleteRomConfirm = false },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Cancel") }
                }
            }
        }
    }

    // ── Upload confirmation overlay ─────────────────────────────────────
    if (pendingUpload != null) {
        val backup = pendingUpload!!
        val validation = viewModel.validateBackup(backup)
        val extraWarning = if (validation == SaveValidationResult.SIZE_MISMATCH)
            "\n\nNote: this file size is unusual for this game — it may not be compatible."
        else ""

        UploadConfirmOverlay(
            backupName = backup.displayName,
            extraWarning = extraWarning,
            uploadInProgress = uploadTransfer.inProgress,
            uploadMessage = uploadTransfer.message,
            uploadIsError = uploadTransfer.isError,
            onConfirmed = {
                viewModel.uploadToWatch(backup)
                pendingUpload = null
            },
            onDismiss = {
                pendingUpload = null
                viewModel.clearTransferMessages()
            },
        )
    }
}

// ── Sub-composables ─────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, action: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
private fun WatchSaveCard(
    entry: com.example.squintboyadvance.shared.model.SaveFileEntry,
    onBackup: () -> Unit,
    backupInProgress: Boolean,
    backupMessage: String?,
    backupIsError: Boolean,
    watchConnected: Boolean,
) {
    val context = LocalContext.current
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
                Text(entry.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(
                    Formatter.formatShortFileSize(context, entry.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(entry.lastModified)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedVisibility(
                    visible = backupMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut(tween(600)),
                ) {
                    Text(
                        backupMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (backupIsError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (backupInProgress) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onBackup, enabled = watchConnected) {
                    Icon(Icons.Default.CloudDownload, contentDescription = "Backup to phone")
                }
            }
        }
    }
}

@Composable
private fun BackupCard(
    backup: SaveBackupEntry,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onUpload: () -> Unit,
    watchConnected: Boolean,
) {
    val context = LocalContext.current
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var nameInput by remember(backup.displayName) { mutableStateOf(backup.displayName) }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename backup") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(nameInput)
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete backup?") },
            text = { Text("\"${backup.displayName}\" will be permanently deleted.") },
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Name row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = backup.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showRenameDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Rename",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                Formatter.formatShortFileSize(context, backup.file.length()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            // Action row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onExport, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Export", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onUpload,
                    enabled = watchConnected,
                ) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Upload to Watch")
                }
            }
        }
    }
}

@Composable
private fun UploadConfirmOverlay(
    backupName: String,
    extraWarning: String,
    uploadInProgress: Boolean,
    uploadMessage: String?,
    uploadIsError: Boolean,
    onConfirmed: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Upload Save to Watch",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Uploading \"$backupName\" will overwrite the current save on your watch. " +
                            "Close the game on your watch before continuing." +
                            extraWarning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                if (uploadInProgress) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uploadMessage != null) {
                    Text(
                        uploadMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uploadIsError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("Close")
                    }
                } else {
                    SlideToConfirm(
                        text = "Slide to upload",
                        onConfirmed = onConfirmed,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
