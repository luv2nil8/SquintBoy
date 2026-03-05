package com.anaglych.squintboyadvance.ui.settings

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anaglych.squintboyadvance.shared.model.EmulatorSettings
import com.anaglych.squintboyadvance.shared.protocol.WearMessageConstants
import com.anaglych.squintboyadvance.ui.sendWearableRequest
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

class WatchSettingsViewModel(application: Application) : AndroidViewModel(application),
    MessageClient.OnMessageReceivedListener {

    companion object {
        private const val TAG = "WatchSettingsVM"
        private const val PUSH_DEBOUNCE_MS = 400L
        private const val PREFS_NAME = "watch_settings_cache"
        private const val KEY_SETTINGS = "settings_json"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val messageClient = Wearable.getMessageClient(application)
    private val nodeClient = Wearable.getNodeClient(application)
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow<EmulatorSettings?>(null)
    val settings: StateFlow<EmulatorSettings?> = _settings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var timeoutJob: Job? = null
    private var pushJob: Job? = null

    init {
        messageClient.addListener(this)
        loadCache()
        loadSettings()
    }

    override fun onCleared() {
        messageClient.removeListener(this)
        super.onCleared()
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == WearMessageConstants.PATH_SETTINGS_RESPONSE) {
            timeoutJob?.cancel()
            try {
                val s = json.decodeFromString(EmulatorSettings.serializer(), String(event.data))
                // Don't clobber unsaved local edits from a pending push
                if (pushJob == null || !pushJob!!.isActive) {
                    _settings.value = s
                }
                saveCache(s)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse settings", e)
            }
            _isLoading.value = false
        }
    }

    fun loadSettings() {
        viewModelScope.launch {
            _isLoading.value = true
            timeoutJob = sendWearableRequest(
                nodeClient, messageClient,
                path = WearMessageConstants.PATH_SETTINGS_REQUEST,
                scope = viewModelScope, tag = TAG,
                onNoNode = { _isLoading.value = false },
                onTimeout = { _isLoading.value = false },
                onError = { _isLoading.value = false },
            )
        }
    }

    fun updateLocal(transform: (EmulatorSettings) -> EmulatorSettings) {
        val current = _settings.value ?: return
        _settings.value = transform(current)
        pushJob?.cancel()
        pushJob = viewModelScope.launch {
            delay(PUSH_DEBOUNCE_MS)
            pushToWatch()
        }
    }

    private fun pushToWatch() {
        val current = _settings.value ?: return
        viewModelScope.launch {
            try {
                val nodeId = nodeClient.connectedNodes.await().firstOrNull()?.id ?: return@launch
                val payload = json.encodeToString(EmulatorSettings.serializer(), current).toByteArray()
                messageClient.sendMessage(nodeId, WearMessageConstants.PATH_SETTINGS_SYNC, payload).await()
                saveCache(current)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push settings", e)
            }
        }
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private fun loadCache() {
        val cached = prefs.getString(KEY_SETTINGS, null) ?: return
        try {
            val s = json.decodeFromString(EmulatorSettings.serializer(), cached)
            _settings.value = s
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings cache", e)
        }
    }

    private fun saveCache(settings: EmulatorSettings) {
        try {
            val encoded = json.encodeToString(EmulatorSettings.serializer(), settings)
            prefs.edit().putString(KEY_SETTINGS, encoded).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save settings cache", e)
        }
    }
}
