package com.anaglych.squintboyadvance.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import com.anaglych.squintboyadvance.shared.model.SystemType
import com.anaglych.squintboyadvance.shared.protocol.WearMessageConstants
import java.io.OutputStream

enum class TransferStatus { PENDING, SENDING, COMPLETE, ERROR }

data class RomTransferItem(
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val systemType: SystemType?,
    val status: TransferStatus = TransferStatus.PENDING,
    val progress: Float = 0f,
    val errorMessage: String? = null,
)

class RomTransferViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "RomTransferVM"
        private const val CONFIRMATION_TIMEOUT_MS = 30_000L
        private val ROM_EXTENSIONS = setOf("gb", "gbc", "gba")
    }

    private val _roms = MutableStateFlow<List<RomTransferItem>>(emptyList())
    val roms: StateFlow<List<RomTransferItem>> = _roms.asStateFlow()

    private val _watchConnected = MutableStateFlow(false)
    val watchConnected: StateFlow<Boolean> = _watchConnected.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val nodeClient = Wearable.getNodeClient(application)
    private val channelClient = Wearable.getChannelClient(application)

    // Track pending confirmation timeouts by filename
    private val pendingTimeouts = ConcurrentHashMap<String, Job>()

    init {
        refreshConnectionStatus()
        viewModelScope.launch {
            TransferResultSignal.results.collect { result ->
                pendingTimeouts.remove(result.filename)?.cancel()
                if (result.success) {
                    updateRomStatusByName(result.filename, TransferStatus.COMPLETE, progress = 1f)
                } else {
                    updateRomStatusByName(
                        result.filename, TransferStatus.ERROR,
                        errorMessage = result.errorMessage ?: "Transfer failed on watch"
                    )
                }
            }
        }
    }

    fun refreshConnectionStatus() {
        viewModelScope.launch {
            _watchConnected.value = try {
                val nodes = nodeClient.connectedNodes.await()
                nodes.isNotEmpty()
            } catch (_: Exception) {
                false
            }
        }
    }

    fun addRoms(uris: List<Uri>) {
        val resolver = getApplication<Application>().contentResolver
        val existing = _roms.value.map { it.uri }.toSet()
        val newItems = uris
            .filter { it !in existing }
            .mapNotNull { uri ->
                val cursor = resolver.query(uri, null, null, null, null) ?: return@mapNotNull null
                cursor.use {
                    if (!it.moveToFirst()) return@mapNotNull null
                    val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else uri.lastPathSegment ?: "unknown"
                    val size = if (sizeIdx >= 0) it.getLong(sizeIdx) else 0L
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext !in ROM_EXTENSIONS) return@mapNotNull null
                    RomTransferItem(
                        uri = uri,
                        displayName = name,
                        size = size,
                        systemType = SystemType.fromExtension(ext),
                    )
                }
            }
        _roms.update { it + newItems }
    }

    fun removeRom(item: RomTransferItem) {
        _roms.update { list -> list.filter { it.uri != item.uri } }
    }

    fun sendRom(item: RomTransferItem) {
        viewModelScope.launch(Dispatchers.IO) {
            transferRom(item)
        }
    }

    fun sendAll() {
        val pending = _roms.value.filter { it.status == TransferStatus.PENDING || it.status == TransferStatus.ERROR }
        if (pending.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _sending.value = true
            for (rom in pending) {
                transferRom(rom)
            }
            _sending.value = false
        }
    }

    private suspend fun transferRom(item: RomTransferItem) {
        updateRomStatus(item.uri, TransferStatus.SENDING, progress = 0f)

        try {
            val nodes = nodeClient.connectedNodes.await()
            val node = nodes.firstOrNull()
                ?: throw Exception("No watch connected")

            val channel: ChannelClient.Channel =
                channelClient.openChannel(node.id, WearMessageConstants.PATH_ROM_TRANSFER).await()

            try {
                val outputStream: OutputStream =
                    channelClient.getOutputStream(channel).await()

                outputStream.use { out ->
                    // Write filename + filesize headers
                    out.write("${item.displayName}\n".toByteArray(Charsets.UTF_8))
                    out.write("${item.size}\n".toByteArray(Charsets.UTF_8))

                    // Stream ROM bytes
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openInputStream(item.uri)?.use { input ->
                        val buffer = ByteArray(8192)
                        var totalWritten = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            totalWritten += read
                            if (item.size > 0) {
                                updateRomStatus(
                                    item.uri,
                                    TransferStatus.SENDING,
                                    progress = totalWritten.toFloat() / item.size
                                )
                            }
                        }
                    } ?: throw Exception("Cannot read file")
                }
            } finally {
                channelClient.close(channel).await()
            }

            // Data sent — keep SENDING at 100% until watch confirms
            updateRomStatus(item.uri, TransferStatus.SENDING, progress = 1f)

            // Start a timeout: if no confirmation arrives, mark as error
            val timeoutJob = viewModelScope.launch {
                delay(CONFIRMATION_TIMEOUT_MS)
                val current = _roms.value.find { it.displayName == item.displayName }
                if (current?.status == TransferStatus.SENDING && current.progress >= 1f) {
                    Log.w(TAG, "No confirmation from watch for ${item.displayName}")
                    updateRomStatus(
                        item.uri, TransferStatus.ERROR,
                        errorMessage = "No confirmation from watch"
                    )
                }
            }
            pendingTimeouts[item.displayName] = timeoutJob
        } catch (e: Exception) {
            updateRomStatus(item.uri, TransferStatus.ERROR, errorMessage = e.message)
        }
    }

    private fun updateRomStatus(
        uri: Uri,
        status: TransferStatus,
        progress: Float = 0f,
        errorMessage: String? = null,
    ) {
        _roms.update { list ->
            list.map {
                if (it.uri == uri) it.copy(status = status, progress = progress, errorMessage = errorMessage)
                else it
            }
        }
    }

    private fun updateRomStatusByName(
        displayName: String,
        status: TransferStatus,
        progress: Float = 0f,
        errorMessage: String? = null,
    ) {
        _roms.update { list ->
            list.map {
                if (it.displayName == displayName) it.copy(status = status, progress = progress, errorMessage = errorMessage)
                else it
            }
        }
    }
}
