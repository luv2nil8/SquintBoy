package com.example.squintboyadvance.ui.roms

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.squintboyadvance.shared.model.RomListResponse
import com.example.squintboyadvance.shared.model.WatchRomEntry
import com.example.squintboyadvance.shared.protocol.WearMessageConstants
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

class WatchRomListViewModel(application: Application) : AndroidViewModel(application),
    MessageClient.OnMessageReceivedListener {

    companion object {
        private const val TAG = "WatchRomListVM"
        private const val TIMEOUT_MS = 5000L
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val messageClient = Wearable.getMessageClient(application)
    private val nodeClient = Wearable.getNodeClient(application)

    private val _watchRoms = MutableStateFlow<List<WatchRomEntry>>(emptyList())
    val watchRoms: StateFlow<List<WatchRomEntry>> = _watchRoms.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var timeoutJob: Job? = null

    init {
        messageClient.addListener(this)
    }

    override fun onCleared() {
        messageClient.removeListener(this)
        super.onCleared()
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == WearMessageConstants.PATH_ROM_LIST_RESPONSE) {
            timeoutJob?.cancel()
            try {
                val response = json.decodeFromString(RomListResponse.serializer(), String(event.data))
                _watchRoms.value = response.roms
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse ROM list", e)
            }
            _isLoading.value = false
        }
    }

    fun requestRomList() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val nodes = nodeClient.connectedNodes.await()
                val nodeId = nodes.firstOrNull()?.id ?: run {
                    _isLoading.value = false
                    return@launch
                }
                messageClient.sendMessage(nodeId, WearMessageConstants.PATH_ROM_LIST_REQUEST, byteArrayOf()).await()
                timeoutJob = viewModelScope.launch {
                    delay(TIMEOUT_MS)
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request ROM list", e)
                _isLoading.value = false
            }
        }
    }

    fun deleteRom(romId: String) {
        viewModelScope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                val nodeId = nodes.firstOrNull()?.id ?: return@launch
                messageClient.sendMessage(nodeId, WearMessageConstants.PATH_ROM_DELETE, romId.toByteArray()).await()
                // Optimistically remove from list
                _watchRoms.value = _watchRoms.value.filter { it.romId != romId }
                // Then refresh to confirm
                delay(500)
                requestRomList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete ROM", e)
            }
        }
    }
}
