package com.example.squintboyadvance.ui.romupload

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.squintboyadvance.shared.model.RomMetadata
import com.example.squintboyadvance.shared.model.SystemType

class RomUploadViewModel : ViewModel() {

    private val _roms = MutableLiveData(stubRoms())
    val roms: LiveData<List<RomMetadata>> = _roms

    private fun stubRoms(): List<RomMetadata> = listOf(
        RomMetadata(
            id = "1",
            title = "Pokemon Red",
            systemType = SystemType.GB,
            filePath = "/storage/roms/pokemon_red.gb",
            fileSize = 1_048_576L
        ),
        RomMetadata(
            id = "2",
            title = "Pokemon Crystal",
            systemType = SystemType.GBC,
            filePath = "/storage/roms/pokemon_crystal.gbc",
            fileSize = 2_097_152L
        ),
        RomMetadata(
            id = "3",
            title = "Pokemon Emerald",
            systemType = SystemType.GBA,
            filePath = "/storage/roms/pokemon_emerald.gba",
            fileSize = 16_777_216L
        )
    )

    fun addRom(rom: RomMetadata) {
        _roms.value = _roms.value.orEmpty() + rom
    }
}
