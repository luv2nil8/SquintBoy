package com.anaglych.squintboyadvance.ui.roms

import android.app.Application
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anaglych.squintboyadvance.MobileEntitlementCache
import com.anaglych.squintboyadvance.shared.model.ControllerLayout
import com.anaglych.squintboyadvance.shared.model.DemoLimits
import com.anaglych.squintboyadvance.shared.model.EmulatorSettings
import com.anaglych.squintboyadvance.shared.model.GbColorPalette
import com.anaglych.squintboyadvance.shared.model.RomOverrides
import com.anaglych.squintboyadvance.shared.model.ScaleMode
import com.anaglych.squintboyadvance.shared.model.SystemType
import com.anaglych.squintboyadvance.ui.components.SlideToConfirm
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun RomManagementScreen(
    romId: String,
    systemType: SystemType,
    watchConnected: Boolean,
    onRomDeleted: () -> Unit = {},
    onRenamed: (String) -> Unit = {},
    onOpenLicenses: () -> Unit = {},
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: RomManagementViewModel = viewModel(
        factory = RomManagementViewModel.factory(application, romId, systemType),
        key = romId,
    )

    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val watchSave by viewModel.watchSave.collectAsStateWithLifecycle()
    val isLoadingWatchSave by viewModel.isLoadingWatchSave.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()
    val backupTransfer by viewModel.backupTransfer.collectAsStateWithLifecycle()
    val uploadTransfer by viewModel.uploadTransfer.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isLoadingSettings by viewModel.isLoadingSettings.collectAsStateWithLifecycle()
    val isRomMode by viewModel.isRomMode.collectAsStateWithLifecycle()
    val hasRomDifferences by viewModel.hasRomDifferences.collectAsStateWithLifecycle()
    val watchScreenWidthPx by viewModel.watchScreenWidthPx.collectAsStateWithLifecycle()
    val isPro by MobileEntitlementCache.isPro.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var pendingUpload by remember { mutableStateOf<SaveBackupEntry?>(null) }
    var showDeleteRomConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BottomTabItem(
                    icon = Icons.Default.Save,
                    label = "Saves",
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f),
                )
                BottomTabItem(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── ROM header ──────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Badge(containerColor = badgeColor, contentColor = Color.White) {
                    Text(systemType.name, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { showRenameDialog = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Rename",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // ── Tab content ─────────────────────────────────────────────
            when (selectedTab) {
                0 -> SavesTabContent(
                    viewModel = viewModel,
                    watchConnected = watchConnected,
                    watchSave = watchSave,
                    isLoadingWatchSave = isLoadingWatchSave,
                    backups = backups,
                    backupTransfer = backupTransfer,
                    importLauncher = { importLauncher.launch(arrayOf("*/*")) },
                    onUpload = { pendingUpload = it },
                    onShowDeleteRom = { showDeleteRomConfirm = true },
                    isPro = isPro,
                )
                1 -> {
                    // Re-request screen info when settings tab is opened
                    LaunchedEffect(Unit) {
                        if (watchScreenWidthPx == null) viewModel.retryScreenInfo()
                    }
                    SettingsTabContent(
                        viewModel = viewModel,
                        settings = settings,
                        isLoadingSettings = isLoadingSettings,
                        watchConnected = watchConnected,
                        isRomMode = isRomMode,
                        hasRomDifferences = hasRomDifferences,
                        systemType = systemType,
                        watchScreenWidthPx = watchScreenWidthPx,
                        onOpenLicenses = onOpenLicenses,
                        isPro = isPro,
                    )
                }
            }
        }
    }

    // ── Rename ROM dialog ───────────────────────────────────────────────────
    if (showRenameDialog) {
        var renameText by remember { mutableStateOf(displayName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Display name") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameRom(renameText)
                    val resolved = renameText.trim().ifEmpty { romId.substringBeforeLast('.') }
                    onRenamed(resolved)
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            },
        )
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
                        "\"$displayName\" will be deleted from your watch. " +
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

// ── Saves tab ────────────────────────────────────────────────────────────────

@Composable
private fun SavesTabContent(
    viewModel: RomManagementViewModel,
    watchConnected: Boolean,
    watchSave: com.anaglych.squintboyadvance.shared.model.SaveFileEntry?,
    isLoadingWatchSave: Boolean,
    backups: List<SaveBackupEntry>,
    backupTransfer: SaveTransferState,
    importLauncher: () -> Unit,
    onUpload: (SaveBackupEntry) -> Unit,
    onShowDeleteRom: () -> Unit,
    isPro: Boolean = false,
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
                    entry = watchSave,
                    onBackup = { if (isPro) viewModel.backupToPhone() },
                    backupInProgress = backupTransfer.inProgress,
                    backupMessage = backupTransfer.message,
                    backupEnabled = isPro,
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
                    IconButton(onClick = importLauncher) {
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
                    onUpload = { onUpload(backup) },
                    watchConnected = watchConnected,
                )
            }
        }

        // ── Remove ROM ──────────────────────────────────────────────
        item {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onShowDeleteRom,
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

// ── Settings tab ─────────────────────────────────────────────────────────────

private const val GBA_FRAME_W = 240
private const val GB_FRAME_W = 160

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTabContent(
    viewModel: RomManagementViewModel,
    settings: EmulatorSettings?,
    isLoadingSettings: Boolean,
    watchConnected: Boolean,
    isRomMode: Boolean,
    hasRomDifferences: Boolean,
    systemType: SystemType,
    watchScreenWidthPx: Int?,
    onOpenLicenses: () -> Unit,
    isPro: Boolean = false,
) {
    if (settings == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isLoadingSettings) {
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
                        enabled = watchConnected && !isLoadingSettings,
                    ) {
                        Text("Load from Watch")
                    }
                }
            }
        }
        return
    }

    val s = settings
    val isGba = viewModel.isGba
    val romId = viewModel.romId
    val ov = s.romOverrides[romId]
    val haptic = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current
    val crimson = Color(0xFFEC1358)

    // Effective values (respecting ROM mode)
    val effectiveScaleMode = if (isRomMode && isGba) ov?.gbaScaleMode ?: s.gbaScaleMode
        else if (isRomMode) ov?.gbScaleMode ?: s.gbScaleMode
        else if (isGba) s.gbaScaleMode else s.gbScaleMode

    val effectiveCustomScale = if (isRomMode && isGba) ov?.gbaCustomScale ?: s.gbaCustomScale
        else if (isRomMode) ov?.gbCustomScale ?: s.gbCustomScale
        else if (isGba) s.gbaCustomScale else s.gbCustomScale

    val effectiveFilterEnabled = if (isRomMode && isGba) ov?.gbaFilterEnabled ?: s.gbaFilterEnabled
        else if (isRomMode) ov?.gbFilterEnabled ?: s.gbFilterEnabled
        else if (isGba) s.gbaFilterEnabled else s.gbFilterEnabled

    val effectiveFrameskip = if (isRomMode && isGba) ov?.gbaFrameskip ?: s.gbaFrameskip
        else if (isRomMode) ov?.gbFrameskip ?: s.gbFrameskip
        else if (isGba) s.gbaFrameskip else s.gbFrameskip

    val effectivePaletteIndex = if (isRomMode) ov?.gbPaletteIndex ?: s.gbPaletteIndex
        else s.gbPaletteIndex

    // Scale range — dynamically computed from watch screen dimensions
    val frameW = if (isGba) GBA_FRAME_W else GB_FRAME_W
    val hasScreenInfo = watchScreenWidthPx != null
    val maxScale = if (hasScreenInfo) watchScreenWidthPx!!.toFloat() / frameW else 3f
    val isIntegerScale = effectiveScaleMode == ScaleMode.INTEGER
    val maxInt = maxScale.toInt().coerceAtLeast(1)
    fun snapScale(v: Float): Float = if (isIntegerScale) {
        v.roundToInt().toFloat().coerceIn(1f, maxInt.toFloat())
    } else v

    var showResetControlsConfirm by remember { mutableStateOf(false) }
    var showSaveToGlobal by remember { mutableStateOf(false) }
    var showResetToGlobal by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Per-ROM toggle ──────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRomMode) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Settings Override",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (isRomMode) "ROM-specific settings" else "Editing global settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = isRomMode,
                        onCheckedChange = { viewModel.setRomMode(it) },
                    )
                }
            }
        }

        // ── Save / Reset to Global (ROM mode only) ──────────────────
        if (isRomMode) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showSaveToGlobal = true },
                        enabled = hasRomDifferences,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(
                            1.dp,
                            if (hasRomDifferences) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        ),
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save to Global")
                    }
                    OutlinedButton(
                        onClick = { showResetToGlobal = true },
                        enabled = hasRomDifferences,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(
                            1.dp,
                            if (hasRomDifferences) crimson else crimson.copy(alpha = 0.3f),
                        ),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = crimson,
                            disabledContentColor = crimson.copy(alpha = 0.3f),
                        ),
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset to Global")
                    }
                }
            }
        }

        // ── Audio ──────────────────────────────────────────────────
        item { SectionHeader("Audio") }
        item {
            SettingsCard {
                SwitchSetting("Audio Enabled", s.audioEnabled) {
                    viewModel.updateSettings(
                        globalTransform = { it.copy(audioEnabled = !it.audioEnabled) },
                    )
                }
                Spacer(Modifier.height(4.dp))
                SliderSetting(
                    "Volume", "${(s.audioVolume * 100).toInt()}%",
                    s.audioVolume, 0f..1f, steps = 9,
                ) { v ->
                    viewModel.updateSettings(
                        globalTransform = { it.copy(audioVolume = v) },
                    )
                }
            }
        }

        // ── Display ────────────────────────────────────────────────
        item { SectionHeader("Display") }
        item {
            SettingsCard {
                SwitchSetting(
                    if (!isPro && isIntegerScale) "Integer Scale (custom: full version)"
                    else "Integer Scale",
                    isIntegerScale,
                ) {
                    if (!isPro && isIntegerScale) return@SwitchSetting // block switching to Custom in demo
                    val newMode = if (isIntegerScale) ScaleMode.CUSTOM else ScaleMode.INTEGER
                    viewModel.updateSettings(
                        romTransform = { ro ->
                            if (isGba) ro.copy(gbaScaleMode = newMode) else ro.copy(gbScaleMode = newMode)
                        },
                        globalTransform = {
                            if (isGba) it.copy(gbaScaleMode = newMode) else it.copy(gbScaleMode = newMode)
                        },
                    )
                    // Snap current scale value when enabling integer mode
                    if (newMode == ScaleMode.INTEGER) {
                        val snapped = snapScale(effectiveCustomScale)
                        if (snapped != effectiveCustomScale) {
                            viewModel.updateSettings(
                                romTransform = { ro ->
                                    if (isGba) ro.copy(gbaCustomScale = snapped) else ro.copy(gbCustomScale = snapped)
                                },
                                globalTransform = {
                                    if (isGba) it.copy(gbaCustomScale = snapped) else it.copy(gbCustomScale = snapped)
                                },
                            )
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                )
                if (isIntegerScale) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Scale", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..maxInt).forEach { v ->
                                FilterChip(
                                    selected = effectiveCustomScale.roundToInt() == v,
                                    onClick = {
                                        viewModel.updateSettings(
                                            romTransform = { ro ->
                                                if (isGba) ro.copy(gbaCustomScale = v.toFloat())
                                                else ro.copy(gbCustomScale = v.toFloat())
                                            },
                                            globalTransform = {
                                                if (isGba) it.copy(gbaCustomScale = v.toFloat())
                                                else it.copy(gbCustomScale = v.toFloat())
                                            },
                                        )
                                    },
                                    label = { Text("${v}x") },
                                )
                            }
                        }
                    }
                } else {
                    SliderSetting(
                        "Scale",
                        "%.2fx".format(effectiveCustomScale) +
                            if (hasScreenInfo) " (max %.1fx)".format(maxScale) else "",
                        effectiveCustomScale.coerceIn(1f, maxScale), 1f..maxScale,
                    ) { v ->
                        viewModel.updateSettings(
                            romTransform = { ro ->
                                if (isGba) ro.copy(gbaCustomScale = v) else ro.copy(gbCustomScale = v)
                            },
                            globalTransform = {
                                if (isGba) it.copy(gbaCustomScale = v) else it.copy(gbCustomScale = v)
                            },
                        )
                    }
                    if (!hasScreenInfo && !watchConnected) {
                        Text(
                            "Connect watch to get exact scale range",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                )
                SwitchSetting("Bilinear Filter", effectiveFilterEnabled) {
                    viewModel.updateSettings(
                        romTransform = { ro ->
                            if (isGba) ro.copy(gbaFilterEnabled = !(ro.gbaFilterEnabled ?: s.gbaFilterEnabled))
                            else ro.copy(gbFilterEnabled = !(ro.gbFilterEnabled ?: s.gbFilterEnabled))
                        },
                        globalTransform = {
                            if (isGba) it.copy(gbaFilterEnabled = !it.gbaFilterEnabled)
                            else it.copy(gbFilterEnabled = !it.gbFilterEnabled)
                        },
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                )
                FrameskipSetting("Frameskip", effectiveFrameskip) { value ->
                    viewModel.updateSettings(
                        romTransform = { ro ->
                            if (isGba) ro.copy(gbaFrameskip = value) else ro.copy(gbFrameskip = value)
                        },
                        globalTransform = {
                            if (isGba) it.copy(gbaFrameskip = value) else it.copy(gbFrameskip = value)
                        },
                    )
                }
            }
        }

        // ── GB Palette (GB/GBC only) ────────────────────────────────
        if (systemType == SystemType.GB) {
            item { SectionHeader("Palette") }
            item {
                SettingsCard {
                    PaletteSetting(
                        selectedIndex = effectivePaletteIndex,
                        onSelect = { index ->
                            viewModel.updateSettings(
                                romTransform = { ro -> ro.copy(gbPaletteIndex = index) },
                                globalTransform = { it.copy(gbPaletteIndex = index) },
                            )
                        },
                        isPro = isPro,
                    )
                }
            }
        }

        // ── Controls (always global) ────────────────────────────────
        item { SectionHeader("Controls") }
        item {
            SettingsCard {
                SwitchSetting("Overlay Visible", s.controllerLayout.visible) {
                    viewModel.updateSettings(
                        globalTransform = {
                            it.copy(controllerLayout = it.controllerLayout.copy(visible = !it.controllerLayout.visible))
                        },
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                )
                SliderSetting(
                    "Outline Opacity", "${(s.controllerLayout.buttonOpacity * 100).toInt()}%",
                    s.controllerLayout.buttonOpacity, 0f..1f, steps = 19,
                ) { v ->
                    viewModel.updateSettings(
                        globalTransform = {
                            it.copy(controllerLayout = it.controllerLayout.copy(buttonOpacity = v))
                        },
                    )
                }
                SliderSetting(
                    "Pressed Opacity", "${(s.controllerLayout.pressedOpacity * 100).toInt()}%",
                    s.controllerLayout.pressedOpacity, 0f..0.5f, steps = 9,
                ) { v ->
                    viewModel.updateSettings(
                        globalTransform = {
                            it.copy(controllerLayout = it.controllerLayout.copy(pressedOpacity = v))
                        },
                    )
                }
                SliderSetting(
                    "Label Opacity", "${(s.controllerLayout.labelOpacity * 100).toInt()}%",
                    s.controllerLayout.labelOpacity, 0f..1f, steps = 19,
                ) { v ->
                    viewModel.updateSettings(
                        globalTransform = {
                            it.copy(controllerLayout = it.controllerLayout.copy(labelOpacity = v))
                        },
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                )
                val labelSizeName = when (s.controllerLayout.labelSize.toInt()) {
                    9 -> "Tiny"; 11 -> "Small"; 13 -> "Medium"; 15 -> "Large"; 17 -> "Extra Large"
                    else -> "${s.controllerLayout.labelSize.toInt()}sp"
                }
                SliderSetting(
                    "Label Size", labelSizeName,
                    s.controllerLayout.labelSize, 9f..17f, steps = 3,
                ) { v ->
                    viewModel.updateSettings(
                        globalTransform = {
                            it.copy(controllerLayout = it.controllerLayout.copy(labelSize = v))
                        },
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                )
                SwitchSetting("Haptic Feedback", s.controllerLayout.hapticFeedback) {
                    val newValue = !s.controllerLayout.hapticFeedback
                    viewModel.updateSettings(
                        globalTransform = {
                            it.copy(controllerLayout = it.controllerLayout.copy(hapticFeedback = newValue))
                        },
                    )
                    if (newValue) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { showResetControlsConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, crimson),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = crimson,
                ),
            ) {
                Text("Reset Controls to Defaults")
            }
        }

        // ── Links ───────────────────────────────────────────────────
        item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }
        if (systemType == SystemType.GB) {
            item {
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
                    Text(
                        "\u203A", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                Text(
                    "\u203A", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        uriHandler.openUri("https://docs.google.com/forms/d/1nY4DsiVs9hfFLY7JJ7xZCM9PgVq8unQpAni8HP652gE")
                    }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Send Feedback", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Bug reports, feature requests & suggestions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "\u203A", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    // ── Save to Global confirmation ──────────────────────────────────
    if (showSaveToGlobal) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showSaveToGlobal = false }) {
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Save to Global",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This will overwrite global settings with this ROM's values.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    SlideToConfirm(
                        text = "Slide to save",
                        accentColor = MaterialTheme.colorScheme.primary,
                        onConfirmed = {
                            showSaveToGlobal = false
                            viewModel.saveRomToGlobal()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { showSaveToGlobal = false },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Cancel") }
                }
            }
        }
    }

    // ── Reset to Global confirmation ─────────────────────────────────
    if (showResetToGlobal) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showResetToGlobal = false }) {
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Reset to Global",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = crimson,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This will discard all ROM-specific settings and revert to global values.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    SlideToConfirm(
                        text = "Slide to reset",
                        accentColor = crimson,
                        onConfirmed = {
                            showResetToGlobal = false
                            viewModel.resetRomToGlobal()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { showResetToGlobal = false },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Cancel") }
                }
            }
        }
    }

    // ── Reset controls confirmation ─────────────────────────────────
    if (showResetControlsConfirm) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showResetControlsConfirm = false }) {
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Reset Controls",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = crimson,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This will reset all controller settings to defaults.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    SlideToConfirm(
                        text = "Slide to reset",
                        accentColor = crimson,
                        onConfirmed = {
                            showResetControlsConfirm = false
                            viewModel.resetControllerLayout()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { showResetControlsConfirm = false },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Cancel") }
                }
            }
        }
    }
}

// ── Bottom tab ───────────────────────────────────────────────────────────────

@Composable
private fun BottomTabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else Color.Transparent

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

// ── Shared sub-composables ───────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, action: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            content()
        }
    }
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
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun FrameskipSetting(label: String, current: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
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
    isPro: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val availablePalettes = if (isPro) GbColorPalette.ALL
        else GbColorPalette.ALL.take(DemoLimits.PALETTE_COUNT)
    val selected = availablePalettes.getOrNull(selectedIndex)
        ?: availablePalettes[0]

    Column(modifier = Modifier.fillMaxWidth()) {
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
                    .fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true,
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                availablePalettes.forEachIndexed { index, palette ->
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
    val c0 = Color(palette.c0)
    val c1 = Color(palette.c1)
    val c2 = Color(palette.c2)
    val c3 = Color(palette.c3)
    Canvas(modifier = Modifier.size(size)) {
        drawArc(color = c0, startAngle = 180f, sweepAngle = 90f, useCenter = true)
        drawArc(color = c1, startAngle = 270f, sweepAngle = 90f, useCenter = true)
        drawArc(color = c2, startAngle = 90f, sweepAngle = 90f, useCenter = true)
        drawArc(color = c3, startAngle = 0f, sweepAngle = 90f, useCenter = true)
    }
}

// ── Saves sub-composables ────────────────────────────────────────────────────

@Composable
private fun WatchSaveCard(
    entry: com.anaglych.squintboyadvance.shared.model.SaveFileEntry,
    onBackup: () -> Unit,
    backupInProgress: Boolean,
    backupMessage: String?,
    backupIsError: Boolean,
    watchConnected: Boolean,
    backupEnabled: Boolean = true,
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
                IconButton(onClick = onBackup, enabled = watchConnected && backupEnabled) {
                    Icon(
                        if (backupEnabled) Icons.Default.CloudDownload else Icons.Default.Lock,
                        contentDescription = if (backupEnabled) "Backup to phone" else "Full version only",
                    )
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
