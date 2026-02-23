package com.example.squintboyadvance.ui.roms

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.serialization.encodeToString
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.squintboyadvance.shared.model.SaveFileEntry
import com.example.squintboyadvance.shared.model.SaveFileType
import com.example.squintboyadvance.shared.model.SaveListResponse
import com.example.squintboyadvance.shared.protocol.WearMessageConstants
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SaveBackupEntry(
    val file: File,
    /** User-assigned display name, or a date string derived from file mtime. */
    val displayName: String,
)

enum class SaveValidationResult { VALID, EMPTY, SIZE_MISMATCH }

data class SaveTransferState(
    val inProgress: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

class RomManagementViewModel(
    application: Application,
    val romId: String,
) : AndroidViewModel(application), MessageClient.OnMessageReceivedListener {

    companion object {
        private const val TAG = "RomManagementVM"
        private const val TIMEOUT_MS = 5000L
        private const val BACKUP_NAMES_PREFS = "backup_names"
        private const val WATCH_SAVE_PREFS = "watch_save_cache"
        private const val DISPLAY_NAMES_PREFS = "rom_display_names"
        private const val FILE_PROVIDER_AUTHORITY = "com.luv2nil8.squintboyadvance.fileprovider"

        fun factory(application: Application, romId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RomManagementViewModel(application, romId) as T
            }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val messageClient = Wearable.getMessageClient(application)
    private val channelClient = Wearable.getChannelClient(application)
    private val nodeClient = Wearable.getNodeClient(application)
    private val namePrefs = application.getSharedPreferences(BACKUP_NAMES_PREFS, Context.MODE_PRIVATE)
    private val watchSavePrefs = application.getSharedPreferences(WATCH_SAVE_PREFS, Context.MODE_PRIVATE)
    private val displayNamesPrefs = application.getSharedPreferences(DISPLAY_NAMES_PREFS, Context.MODE_PRIVATE)

    // Where phone-side backup files live
    private val backupDir: File
        get() = File(getApplication<Application>().filesDir, "backups/$romId").also { it.mkdirs() }

    // ── Exposed state ──────────────────────────────────────────────────

    private val _displayName = MutableStateFlow(
        displayNamesPrefs.getString(romId, null) ?: romId.substringBeforeLast('.')
    )
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _watchSave = MutableStateFlow<SaveFileEntry?>(null)
    val watchSave: StateFlow<SaveFileEntry?> = _watchSave.asStateFlow()

    private val _isLoadingWatchSave = MutableStateFlow(false)
    val isLoadingWatchSave: StateFlow<Boolean> = _isLoadingWatchSave.asStateFlow()

    private val _backups = MutableStateFlow<List<SaveBackupEntry>>(emptyList())
    val backups: StateFlow<List<SaveBackupEntry>> = _backups.asStateFlow()

    private val _backupTransfer = MutableStateFlow(SaveTransferState())
    val backupTransfer: StateFlow<SaveTransferState> = _backupTransfer.asStateFlow()

    private val _uploadTransfer = MutableStateFlow(SaveTransferState())
    val uploadTransfer: StateFlow<SaveTransferState> = _uploadTransfer.asStateFlow()

    private var timeoutJob: Job? = null

    init {
        messageClient.addListener(this)
        loadWatchSaveCache()
        scanBackups()
    }

    override fun onCleared() {
        messageClient.removeListener(this)
        super.onCleared()
    }

    // ── MessageClient listener ──────────────────────────────────────────

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == WearMessageConstants.PATH_SAVE_LIST_RESPONSE) {
            timeoutJob?.cancel()
            try {
                val response = json.decodeFromString(SaveListResponse.serializer(), String(event.data))
                val liveSave = response.saves.firstOrNull {
                    it.romId == romId && it.type == SaveFileType.SRAM_LIVE
                }
                _watchSave.value = liveSave
                persistWatchSave(liveSave)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse save list", e)
            }
            _isLoadingWatchSave.value = false
        }
    }

    // ── Public API ──────────────────────────────────────────────────────

    fun refreshWatchSave() {
        viewModelScope.launch {
            _isLoadingWatchSave.value = true
            try {
                val nodeId = nodeClient.connectedNodes.await().firstOrNull()?.id ?: run {
                    _isLoadingWatchSave.value = false
                    return@launch
                }
                messageClient.sendMessage(
                    nodeId,
                    WearMessageConstants.PATH_SAVE_LIST_REQUEST,
                    byteArrayOf(),
                ).await()
                timeoutJob = viewModelScope.launch {
                    delay(TIMEOUT_MS)
                    _isLoadingWatchSave.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request save list", e)
                _isLoadingWatchSave.value = false
            }
        }
    }

    /**
     * Pulls the live .sav from the watch and saves it as a timestamped backup
     * under [backupDir].
     */
    fun backupToPhone() {
        viewModelScope.launch(Dispatchers.IO) {
            _backupTransfer.value = SaveTransferState(inProgress = true, message = "Pulling save…")
            try {
                val nodeId = nodeClient.connectedNodes.await().firstOrNull()?.id
                    ?: throw Exception("No watch connected")

                val channel: ChannelClient.Channel =
                    channelClient.openChannel(nodeId, WearMessageConstants.PATH_SAVE_PULL).await()

                try {
                    val outStream = channelClient.getOutputStream(channel).await()
                    val inStream = BufferedInputStream(channelClient.getInputStream(channel).await())

                    // Send romId header
                    outStream.write("$romId\n".toByteArray(Charsets.UTF_8))
                    outStream.flush()

                    // Read JSON manifest
                    val manifestLine = readLine(inStream)
                    val manifest = json.decodeFromString(SaveListResponse.serializer(), manifestLine)
                    val liveEntry = manifest.saves.firstOrNull { it.type == SaveFileType.SRAM_LIVE }
                        ?: throw Exception("No live SRAM save found on watch")

                    // Read file: 8-byte size + raw bytes
                    val sizeBuf = ByteArray(8)
                    var read = 0
                    while (read < 8) {
                        val n = inStream.read(sizeBuf, read, 8 - read)
                        if (n == -1) break
                        read += n
                    }
                    val fileSize = ByteBuffer.wrap(sizeBuf).getLong()

                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val outFile = File(backupDir, "backup_$timestamp.sav")
                    outFile.outputStream().use { out ->
                        var remaining = fileSize
                        val buffer = ByteArray(8192)
                        while (remaining > 0) {
                            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                            val n = inStream.read(buffer, 0, toRead)
                            if (n == -1) break
                            out.write(buffer, 0, n)
                            remaining -= n
                        }
                    }

                    _backupTransfer.value = SaveTransferState(message = "Backup saved")
                    scanBackups()
                } finally {
                    channelClient.close(channel).await()
                }
            } catch (e: Exception) {
                Log.e(TAG, "backupToPhone failed", e)
                _backupTransfer.value = SaveTransferState(
                    message = e.message ?: "Failed",
                    isError = true,
                )
            }
        }
    }

    /**
     * Uploads a local backup to the watch as the live .sav, then clears
     * the save-state and SRAM-backup stacks so the ROM starts fresh.
     */
    fun uploadToWatch(backup: SaveBackupEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            _uploadTransfer.value = SaveTransferState(inProgress = true, message = "Uploading…")
            try {
                val nodeId = nodeClient.connectedNodes.await().firstOrNull()?.id
                    ?: throw Exception("No watch connected")

                val romBaseName = romId.substringBeforeLast('.')
                val channel: ChannelClient.Channel =
                    channelClient.openChannel(nodeId, WearMessageConstants.PATH_SAVE_PUSH).await()

                try {
                    val outStream = channelClient.getOutputStream(channel).await()
                    outStream.use { out ->
                        // Header: "romId/{baseName}.sav\n"
                        out.write("$romId/$romBaseName.sav\n".toByteArray(Charsets.UTF_8))
                        backup.file.inputStream().use { it.copyTo(out) }
                    }
                } finally {
                    channelClient.close(channel).await()
                }

                // Clear save-state + SRAM-backup stacks on watch
                messageClient.sendMessage(
                    nodeId,
                    WearMessageConstants.PATH_SAVE_CLEAR_STACKS,
                    romId.toByteArray(Charsets.UTF_8),
                ).await()

                _uploadTransfer.value = SaveTransferState(message = "Upload complete")
            } catch (e: Exception) {
                Log.e(TAG, "uploadToWatch failed", e)
                _uploadTransfer.value = SaveTransferState(
                    message = e.message ?: "Failed",
                    isError = true,
                )
            }
        }
    }

    fun exportBackup(backup: SaveBackupEntry, context: Context) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                FILE_PROVIDER_AUTHORITY,
                backup.file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${backup.displayName}.sav")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export save backup"))
        } catch (e: Exception) {
            Log.e(TAG, "exportBackup failed", e)
        }
    }

    fun deleteBackup(backup: SaveBackupEntry) {
        backup.file.delete()
        namePrefs.edit().remove("$romId/${backup.file.name}").apply()
        scanBackups()
    }

    fun renameRom(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            displayNamesPrefs.edit().remove(romId).apply()
            _displayName.value = romId.substringBeforeLast('.')
        } else {
            displayNamesPrefs.edit().putString(romId, trimmed).apply()
            _displayName.value = trimmed
        }
    }

    fun renameBackup(backup: SaveBackupEntry, newName: String) {
        namePrefs.edit().putString("$romId/${backup.file.name}", newName.trim()).apply()
        scanBackups()
    }

    /** Returns [SaveValidationResult.VALID] unless the file is empty or an unexpected size. */
    fun validateBackup(backup: SaveBackupEntry): SaveValidationResult {
        val size = backup.file.length()
        if (size == 0L) return SaveValidationResult.EMPTY
        val ext = romId.substringAfterLast('.', "").lowercase()
        val validSizes: Set<Long> = when (ext) {
            "gba" -> setOf(512L, 8192L, 65536L, 131072L)
            "gb", "gbc" -> setOf(8192L, 32768L, 131072L)
            else -> return SaveValidationResult.VALID
        }
        return if (size in validSizes) SaveValidationResult.VALID else SaveValidationResult.SIZE_MISMATCH
    }

    /**
     * Sends a delete request to the watch for this ROM, then invokes [onDeleted].
     */
    fun deleteRom(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                val nodeId = nodeClient.connectedNodes.await().firstOrNull()?.id
                    ?: return@launch
                messageClient.sendMessage(
                    nodeId,
                    WearMessageConstants.PATH_ROM_DELETE,
                    romId.toByteArray(Charsets.UTF_8),
                ).await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete ROM $romId", e)
            } finally {
                onDeleted()
            }
        }
    }

    /**
     * Copies a .sav file chosen from the device's storage into [backupDir] as a new backup entry.
     */
    fun importFromStorage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val outFile = File(backupDir, "import_$timestamp.sav")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
                scanBackups()
            } catch (e: Exception) {
                Log.e(TAG, "importFromStorage failed", e)
            }
        }
    }

    fun clearTransferMessages() {
        _backupTransfer.value = SaveTransferState()
        _uploadTransfer.value = SaveTransferState()
    }

    // ── Internal ───────────────────────────────────────────────────────

    private fun loadWatchSaveCache() {
        val cached = watchSavePrefs.getString(romId, null) ?: return
        try {
            _watchSave.value = json.decodeFromString(SaveFileEntry.serializer(), cached)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load watch save cache for $romId", e)
        }
    }

    private fun persistWatchSave(entry: SaveFileEntry?) {
        try {
            if (entry == null) {
                watchSavePrefs.edit().remove(romId).apply()
            } else {
                watchSavePrefs.edit()
                    .putString(romId, json.encodeToString(SaveFileEntry.serializer(), entry))
                    .apply()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist watch save for $romId", e)
        }
    }

    private fun scanBackups() {
        val files = backupDir.listFiles()
            ?.filter { it.isFile && it.extension == "sav" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        _backups.value = files.map { f ->
            SaveBackupEntry(
                file = f,
                displayName = namePrefs.getString("$romId/${f.name}", null)
                    ?: formatDate(f.lastModified()),
            )
        }
    }

    private fun formatDate(epochMs: Long): String =
        SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.US).format(Date(epochMs))

    private fun readLine(input: BufferedInputStream): String {
        val bytes = mutableListOf<Byte>()
        while (true) {
            val b = input.read()
            if (b == -1 || b == '\n'.code) break
            bytes.add(b.toByte())
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }
}
