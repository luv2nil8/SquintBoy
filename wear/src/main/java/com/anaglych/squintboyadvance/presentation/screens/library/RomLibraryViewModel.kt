package com.anaglych.squintboyadvance.presentation.screens.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.anaglych.squintboyadvance.presentation.RomLibrarySignal
import com.anaglych.squintboyadvance.presentation.RomMetadataStore
import com.anaglych.squintboyadvance.shared.model.RomMetadata
import com.anaglych.squintboyadvance.shared.model.SystemType
import com.anaglych.squintboyadvance.shared.protocol.WearMessageConstants
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

enum class RomPickerState { IDLE, SENDING, WAITING, ERROR }

class RomLibraryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "RomLibraryVM"
        private const val OVERLAY_TIMEOUT_MS = 25_000L
    }

    private val _roms = MutableStateFlow<List<RomMetadata>>(emptyList())
    val roms: StateFlow<List<RomMetadata>> = _roms.asStateFlow()

    private val _transferringNames = MutableStateFlow<List<String>>(emptyList())
    val transferringNames: StateFlow<List<String>> = _transferringNames.asStateFlow()

    private val _pickerState = MutableStateFlow(RomPickerState.IDLE)
    val pickerState: StateFlow<RomPickerState> = _pickerState.asStateFlow()

    private val _phoneAppInstalled = MutableStateFlow(true)
    val phoneAppInstalled: StateFlow<Boolean> = _phoneAppInstalled.asStateFlow()

    private val romsDir = File(application.filesDir, "roms")
    private val metadataStore = RomMetadataStore.getInstance(application)
    private val messageClient = Wearable.getMessageClient(application)
    private val nodeClient = Wearable.getNodeClient(application)
    private val remoteActivityHelper = RemoteActivityHelper(application)

    @Volatile private var pongReceived = false

    init {
        scanRoms()
        pingPhone()
        viewModelScope.launch {
            RomLibrarySignal.romChanged.collect { scanRoms() }
        }
        viewModelScope.launch {
            RomLibrarySignal.phonePong.collect {
                pongReceived = true
                _phoneAppInstalled.value = true
            }
        }
    }

    fun pingPhone() {
        pongReceived = false
        viewModelScope.launch {
            try {
                val nodeId = nodeClient.connectedNodes.await().firstOrNull()?.id ?: run {
                    _phoneAppInstalled.value = false
                    return@launch
                }
                messageClient.sendMessage(
                    nodeId,
                    WearMessageConstants.PATH_PHONE_PING,
                    byteArrayOf(),
                ).await()
                delay(3000)
                if (!pongReceived) _phoneAppInstalled.value = false
            } catch (e: Exception) {
                Log.d(TAG, "Ping failed: ${e.message}")
                _phoneAppInstalled.value = false
            }
        }
    }

    fun openPhonePlayStore() {
        viewModelScope.launch {
            try {
                val nodeId = nodeClient.connectedNodes.await().firstOrNull()?.id ?: return@launch
                withContext(Dispatchers.IO) {
                    remoteActivityHelper.startRemoteActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setData(Uri.parse(WearMessageConstants.PLAY_STORE_URI))
                            .addCategory(Intent.CATEGORY_BROWSABLE),
                        nodeId,
                    ).get()
                }
            } catch (_: Exception) { /* best-effort */ }
        }
    }

    fun scanRoms() {
        if (!romsDir.exists()) {
            _roms.value = emptyList()
            _transferringNames.value = emptyList()
            return
        }

        val allFiles = romsDir.listFiles()?.filter { it.isFile } ?: emptyList()
        val validExtensions = SystemType.entries.flatMap { it.fileExtensions }.toSet()

        // Collect in-progress transfer filenames (strip ".transferring_" prefix)
        _transferringNames.value = allFiles
            .filter { it.name.startsWith(".transferring_") }
            .map { it.name.removePrefix(".transferring_") }

        _roms.value = allFiles
            .filter { !it.name.startsWith(".transferring_") && it.extension.lowercase() in validExtensions }
            .map { file ->
                val systemType = SystemType.fromExtension(file.extension) ?: SystemType.GB
                val meta = metadataStore.get(file.name)
                RomMetadata(
                    id = file.name,
                    title = meta.displayName ?: file.nameWithoutExtension,
                    systemType = systemType,
                    filePath = file.absolutePath,
                    fileSize = file.length(),
                    addedTimestamp = file.lastModified(),
                    lastPlayed = meta.lastPlayed,
                    totalPlayTimeMs = meta.totalPlayTimeMs,
                    thumbnailPath = meta.thumbnailPath
                )
            }
            .sortedByDescending { it.lastPlayed ?: it.addedTimestamp }
    }

    fun sendOpenRomPicker() {
        viewModelScope.launch {
            _pickerState.value = RomPickerState.SENDING
            try {
                val nodeId = nodeClient.connectedNodes.await().firstOrNull()?.id ?: run {
                    _pickerState.value = RomPickerState.ERROR
                    scheduleOverlayDismiss()
                    return@launch
                }
                messageClient.sendMessage(
                    nodeId,
                    WearMessageConstants.PATH_OPEN_ROM_PICKER,
                    byteArrayOf(),
                ).await()
                _pickerState.value = RomPickerState.WAITING
                scheduleOverlayDismiss()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send open-picker request", e)
                _pickerState.value = RomPickerState.ERROR
                scheduleOverlayDismiss()
            }
        }
    }

    fun dismissPickerOverlay() {
        _pickerState.value = RomPickerState.IDLE
    }

    private fun scheduleOverlayDismiss() {
        viewModelScope.launch {
            delay(OVERLAY_TIMEOUT_MS)
            if (_pickerState.value != RomPickerState.IDLE) {
                _pickerState.value = RomPickerState.IDLE
            }
        }
    }
}
