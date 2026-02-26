package com.anaglych.squintboyadvance.ui.roms

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anaglych.squintboyadvance.shared.model.RomListResponse
import com.anaglych.squintboyadvance.shared.model.WatchRomEntry
import com.anaglych.squintboyadvance.shared.protocol.WearMessageConstants
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WatchRomListViewModel(application: Application) : AndroidViewModel(application),
    MessageClient.OnMessageReceivedListener {

    companion object {
        private const val TAG = "WatchRomListVM"
        private const val TIMEOUT_MS = 5000L
        private const val PREFS_NAME = "rom_list_cache"
        private const val KEY_ROM_LIST = "rom_list_json"
        private const val DISPLAY_NAMES_PREFS = "rom_display_names"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val messageClient = Wearable.getMessageClient(application)
    private val nodeClient = Wearable.getNodeClient(application)
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val displayNamesPrefs = application.getSharedPreferences(DISPLAY_NAMES_PREFS, Context.MODE_PRIVATE)

    private val _watchRoms = MutableStateFlow<List<WatchRomEntry>>(emptyList())
    val watchRoms: StateFlow<List<WatchRomEntry>> = _watchRoms.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _displayNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val displayNames: StateFlow<Map<String, String>> = _displayNames.asStateFlow()

    private var timeoutJob: Job? = null

    init {
        messageClient.addListener(this)
        loadCache()
        loadDisplayNames()
        requestRomList()
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
                saveCache(response.roms)
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

    fun loadDisplayNames() {
        _displayNames.value = displayNamesPrefs.all
            .mapNotNull { (k, v) -> if (v is String) k to v else null }
            .toMap()
    }

    fun setDisplayName(romId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            displayNamesPrefs.edit().remove(romId).apply()
        } else {
            displayNamesPrefs.edit().putString(romId, trimmed).apply()
        }
        loadDisplayNames()
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

    /** Removes a ROM from the in-memory list and cache without sending a message to the watch. */
    fun removeRomLocally(romId: String) {
        val updated = _watchRoms.value.filter { it.romId != romId }
        _watchRoms.value = updated
        saveCache(updated)
    }

    fun deleteRom(romId: String) {
        viewModelScope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                val nodeId = nodes.firstOrNull()?.id ?: return@launch
                messageClient.sendMessage(nodeId, WearMessageConstants.PATH_ROM_DELETE, romId.toByteArray()).await()
                // Optimistically remove from list and cache
                val updated = _watchRoms.value.filter { it.romId != romId }
                _watchRoms.value = updated
                saveCache(updated)
                // Then refresh to confirm
                delay(500)
                requestRomList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete ROM", e)
            }
        }
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private fun loadCache() {
        val cached = prefs.getString(KEY_ROM_LIST, null) ?: return
        try {
            val response = json.decodeFromString(RomListResponse.serializer(), cached)
            _watchRoms.value = response.roms
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load ROM list cache", e)
        }
    }

    private fun saveCache(roms: List<WatchRomEntry>) {
        try {
            val encoded = json.encodeToString(RomListResponse(roms))
            prefs.edit().putString(KEY_ROM_LIST, encoded).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save ROM list cache", e)
        }
    }
}
