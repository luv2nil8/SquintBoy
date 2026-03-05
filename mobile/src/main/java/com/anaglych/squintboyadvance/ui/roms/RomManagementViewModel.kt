package com.anaglych.squintboyadvance.ui.roms

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.serialization.encodeToString
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anaglych.squintboyadvance.shared.model.SaveFileEntry
import com.anaglych.squintboyadvance.shared.model.SaveFileType
import com.anaglych.squintboyadvance.shared.model.SaveListResponse
import com.anaglych.squintboyadvance.shared.protocol.WearMessageConstants
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import com.anaglych.squintboyadvance.shared.util.readLine
import com.anaglych.squintboyadvance.ui.sendWearableRequest
import java.io.BufferedInputStream
import java.io.File
import java.nio.ByteBuffer

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
        private const val WATCH_SAVE_PREFS = "watch_save_cache"
        private const val DISPLAY_NAMES_PREFS = "rom_display_names"

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
    private val watchSavePrefs = application.getSharedPreferences(WATCH_SAVE_PREFS, Context.MODE_PRIVATE)
    private val displayNamesPrefs = application.getSharedPreferences(DISPLAY_NAMES_PREFS, Context.MODE_PRIVATE)
    private val backupManager = SaveBackupManager(application, romId)

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
        refreshBackups()
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
            timeoutJob = sendWearableRequest(
                nodeClient, messageClient,
                path = WearMessageConstants.PATH_SAVE_LIST_REQUEST,
                scope = viewModelScope, tag = TAG,
                onNoNode = { _isLoadingWatchSave.value = false },
                onTimeout = { _isLoadingWatchSave.value = false },
                onError = { _isLoadingWatchSave.value = false },
            )
        }
    }

    fun backupToPhone() {
        viewModelScope.launch(Dispatchers.IO) {
            _backupTransfer.value = SaveTransferState(inProgress = true, message = "Pulling save...")
            try {
                val nodeId = nodeClient.connectedNodes.await().firstOrNull()?.id
                    ?: throw Exception("No watch connected")

                val channel: ChannelClient.Channel =
                    channelClient.openChannel(nodeId, WearMessageConstants.PATH_SAVE_PULL).await()

                try {
                    val outStream = channelClient.getOutputStream(channel).await()
                    val inStream = BufferedInputStream(channelClient.getInputStream(channel).await())

                    outStream.write("$romId\n".toByteArray(Charsets.UTF_8))
                    outStream.flush()

                    val manifestLine = readLine(inStream)
                        ?: throw Exception("Empty response from watch")
                    val manifest = json.decodeFromString(SaveListResponse.serializer(), manifestLine)
                    manifest.saves.firstOrNull { it.type == SaveFileType.SRAM_LIVE }
                        ?: throw Exception("No live SRAM save found on watch")

                    val sizeBuf = ByteArray(8)
                    var read = 0
                    while (read < 8) {
                        val n = inStream.read(sizeBuf, read, 8 - read)
                        if (n == -1) break
                        read += n
                    }
                    val fileSize = ByteBuffer.wrap(sizeBuf).getLong()

                    backupManager.createTimestampedBackup { outFile ->
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
                    }

                    _backupTransfer.value = SaveTransferState(message = "Backup saved")
                    refreshBackups()
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

    fun uploadToWatch(backup: SaveBackupEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            _uploadTransfer.value = SaveTransferState(inProgress = true, message = "Uploading...")
            try {
                val nodeId = nodeClient.connectedNodes.await().firstOrNull()?.id
                    ?: throw Exception("No watch connected")

                val romBaseName = romId.substringBeforeLast('.')
                val channel: ChannelClient.Channel =
                    channelClient.openChannel(nodeId, WearMessageConstants.PATH_SAVE_PUSH).await()

                try {
                    val outStream = channelClient.getOutputStream(channel).await()
                    outStream.use { out ->
                        out.write("$romId/$romBaseName.sav\n".toByteArray(Charsets.UTF_8))
                        backup.file.inputStream().use { it.copyTo(out) }
                    }
                } finally {
                    channelClient.close(channel).await()
                }

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

    fun exportBackup(backup: SaveBackupEntry, context: Context) =
        backupManager.export(backup, context)

    fun deleteBackup(backup: SaveBackupEntry) {
        backupManager.delete(backup)
        refreshBackups()
    }

    fun renameBackup(backup: SaveBackupEntry, newName: String) {
        backupManager.rename(backup, newName)
        refreshBackups()
    }

    fun validateBackup(backup: SaveBackupEntry): SaveValidationResult =
        backupManager.validate(backup)

    fun importFromStorage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            backupManager.importFromStorage(uri)
            refreshBackups()
        }
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
        viewModelScope.launch {
            try {
                val nodeId = nodeClient.connectedNodes.await().firstOrNull()?.id ?: return@launch
                val payload = "$romId\n$trimmed".toByteArray(Charsets.UTF_8)
                messageClient.sendMessage(nodeId, WearMessageConstants.PATH_ROM_RENAME, payload).await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync rename to watch", e)
            }
        }
    }

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

    fun clearTransferMessages() {
        _backupTransfer.value = SaveTransferState()
        _uploadTransfer.value = SaveTransferState()
    }

    // ── Internal ───────────────────────────────────────────────────────

    private fun refreshBackups() {
        _backups.value = backupManager.scanBackups()
    }

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
}
