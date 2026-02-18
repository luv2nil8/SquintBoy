package com.example.squintboyadvance.ui.saves

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.squintboyadvance.shared.model.SaveFileEntry
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
import java.io.OutputStream
import java.nio.ByteBuffer

enum class TransferState { IDLE, TRANSFERRING, DONE, ERROR }

data class TransferStatus(val state: TransferState = TransferState.IDLE, val message: String? = null)

class SaveSyncViewModel(application: Application) : AndroidViewModel(application),
    MessageClient.OnMessageReceivedListener {

    companion object {
        private const val TAG = "SaveSyncVM"
        private const val TIMEOUT_MS = 5000L
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val messageClient = Wearable.getMessageClient(application)
    private val channelClient = Wearable.getChannelClient(application)
    private val nodeClient = Wearable.getNodeClient(application)

    private val _savesByRom = MutableStateFlow<Map<String, List<SaveFileEntry>>>(emptyMap())
    val savesByRom: StateFlow<Map<String, List<SaveFileEntry>>> = _savesByRom.asStateFlow()

    private val _localSaves = MutableStateFlow<Map<String, List<File>>>(emptyMap())
    val localSaves: StateFlow<Map<String, List<File>>> = _localSaves.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _transferStatus = MutableStateFlow<Map<String, TransferStatus>>(emptyMap())
    val transferStatus: StateFlow<Map<String, TransferStatus>> = _transferStatus.asStateFlow()

    private var timeoutJob: Job? = null

    private val savesBaseDir: File
        get() = File(getApplication<Application>().getExternalFilesDir(null), "saves")

    init {
        messageClient.addListener(this)
        scanLocalSaves()
    }

    override fun onCleared() {
        messageClient.removeListener(this)
        super.onCleared()
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == WearMessageConstants.PATH_SAVE_LIST_RESPONSE) {
            timeoutJob?.cancel()
            try {
                val response = json.decodeFromString(SaveListResponse.serializer(), String(event.data))
                _savesByRom.value = response.saves.groupBy { it.romId }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse save list", e)
            }
            _isLoading.value = false
        }
    }

    fun requestSaveList() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val nodeId = nodeClient.connectedNodes.await().firstOrNull()?.id ?: run {
                    _isLoading.value = false
                    return@launch
                }
                messageClient.sendMessage(nodeId, WearMessageConstants.PATH_SAVE_LIST_REQUEST, byteArrayOf()).await()
                timeoutJob = viewModelScope.launch {
                    delay(TIMEOUT_MS)
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request save list", e)
                _isLoading.value = false
            }
        }
    }

    fun pullSaves(romId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            setTransferStatus(romId, TransferStatus(TransferState.TRANSFERRING, "Pulling saves..."))
            try {
                val nodeId = nodeClient.connectedNodes.await().firstOrNull()?.id
                    ?: throw Exception("No watch connected")

                val channel: ChannelClient.Channel =
                    channelClient.openChannel(nodeId, WearMessageConstants.PATH_SAVE_PULL).await()

                try {
                    val outputStream: OutputStream = channelClient.getOutputStream(channel).await()
                    val inputStream = BufferedInputStream(
                        channelClient.getInputStream(channel).await()
                    )

                    // Send romId header
                    outputStream.write("$romId\n".toByteArray(Charsets.UTF_8))
                    outputStream.flush()

                    // Read JSON manifest line
                    val manifestLine = readLine(inputStream)
                    val manifest = json.decodeFromString(SaveListResponse.serializer(), manifestLine)

                    val romSaveDir = File(savesBaseDir, romId).apply { mkdirs() }

                    // Read each file: 8-byte size + raw bytes
                    for (entry in manifest.saves) {
                        val sizeBytes = ByteArray(8)
                        var read = 0
                        while (read < 8) {
                            val n = inputStream.read(sizeBytes, read, 8 - read)
                            if (n == -1) break
                            read += n
                        }
                        val size = ByteBuffer.wrap(sizeBytes).getLong()

                        val outFile = File(romSaveDir, entry.fileName)
                        outFile.outputStream().use { out ->
                            var remaining = size
                            val buffer = ByteArray(8192)
                            while (remaining > 0) {
                                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                                val n = inputStream.read(buffer, 0, toRead)
                                if (n == -1) break
                                out.write(buffer, 0, n)
                                remaining -= n
                            }
                        }
                    }

                    setTransferStatus(romId, TransferStatus(TransferState.DONE, "${manifest.saves.size} files pulled"))
                } finally {
                    channelClient.close(channel).await()
                }

                scanLocalSaves()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pull saves for $romId", e)
                setTransferStatus(romId, TransferStatus(TransferState.ERROR, e.message))
            }
        }
    }

    fun pushSave(romId: String, file: File) {
        val key = "$romId/${file.name}"
        viewModelScope.launch(Dispatchers.IO) {
            setTransferStatus(key, TransferStatus(TransferState.TRANSFERRING, "Pushing ${file.name}..."))
            try {
                val nodeId = nodeClient.connectedNodes.await().firstOrNull()?.id
                    ?: throw Exception("No watch connected")

                val channel: ChannelClient.Channel =
                    channelClient.openChannel(nodeId, WearMessageConstants.PATH_SAVE_PUSH).await()

                try {
                    val outputStream: OutputStream = channelClient.getOutputStream(channel).await()
                    outputStream.use { out ->
                        // Header: "romId/filename\n"
                        out.write("$romId/${file.name}\n".toByteArray(Charsets.UTF_8))
                        // File bytes
                        file.inputStream().use { it.copyTo(out) }
                    }
                } finally {
                    channelClient.close(channel).await()
                }

                setTransferStatus(key, TransferStatus(TransferState.DONE, "Pushed ${file.name}"))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push save $key", e)
                setTransferStatus(key, TransferStatus(TransferState.ERROR, e.message))
            }
        }
    }

    private fun scanLocalSaves() {
        val result = mutableMapOf<String, List<File>>()
        if (savesBaseDir.isDirectory) {
            for (romDir in savesBaseDir.listFiles().orEmpty().filter { it.isDirectory }) {
                val files = romDir.listFiles()?.toList().orEmpty()
                if (files.isNotEmpty()) {
                    result[romDir.name] = files
                }
            }
        }
        _localSaves.value = result
    }

    private fun setTransferStatus(key: String, status: TransferStatus) {
        _transferStatus.value = _transferStatus.value + (key to status)
    }

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
