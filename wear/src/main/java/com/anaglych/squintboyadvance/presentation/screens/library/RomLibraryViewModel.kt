package com.anaglych.squintboyadvance.presentation.screens.library

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anaglych.squintboyadvance.presentation.RomMetadataStore
import com.anaglych.squintboyadvance.shared.model.RomMetadata
import com.anaglych.squintboyadvance.shared.model.SystemType
import com.anaglych.squintboyadvance.shared.protocol.WearMessageConstants
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

enum class RomPickerState { IDLE, SENDING, WAITING, ERROR }

class RomLibraryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "RomLibraryVM"
        private const val OVERLAY_TIMEOUT_MS = 25_000L
    }

    private val _roms = MutableStateFlow<List<RomMetadata>>(emptyList())
    val roms: StateFlow<List<RomMetadata>> = _roms.asStateFlow()

    private val _pickerState = MutableStateFlow(RomPickerState.IDLE)
    val pickerState: StateFlow<RomPickerState> = _pickerState.asStateFlow()

    private val romsDir = File(application.filesDir, "roms")
    private val metadataStore = RomMetadataStore.getInstance(application)
    private val messageClient = Wearable.getMessageClient(application)
    private val nodeClient = Wearable.getNodeClient(application)

    init {
        scanRoms()
    }

    fun scanRoms() {
        if (!romsDir.exists()) {
            _roms.value = emptyList()
            return
        }

        val validExtensions = SystemType.entries.flatMap { it.fileExtensions }.toSet()

        _roms.value = romsDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in validExtensions }
            ?.map { file ->
                val systemType = SystemType.fromExtension(file.extension) ?: SystemType.GB
                val meta = metadataStore.get(file.name)
                RomMetadata(
                    id = file.name,
                    title = file.nameWithoutExtension,
                    systemType = systemType,
                    filePath = file.absolutePath,
                    fileSize = file.length(),
                    addedTimestamp = file.lastModified(),
                    lastPlayed = meta.lastPlayed,
                    totalPlayTimeMs = meta.totalPlayTimeMs,
                    thumbnailPath = meta.thumbnailPath
                )
            }
            ?.sortedByDescending { it.lastPlayed ?: it.addedTimestamp }
            ?: emptyList()
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
