package com.example.squintboyadvance.presentation.screens.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.squintboyadvance.presentation.RomMetadataStore
import com.example.squintboyadvance.shared.model.RomMetadata
import com.example.squintboyadvance.shared.model.SystemType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class RomLibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val _roms = MutableStateFlow<List<RomMetadata>>(emptyList())
    val roms: StateFlow<List<RomMetadata>> = _roms.asStateFlow()

    private val romsDir = File(application.filesDir, "roms")
    private val metadataStore = RomMetadataStore.getInstance(application)

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
}
