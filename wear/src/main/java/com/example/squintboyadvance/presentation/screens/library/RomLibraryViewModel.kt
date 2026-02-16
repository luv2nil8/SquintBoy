package com.example.squintboyadvance.presentation.screens.library

import androidx.lifecycle.ViewModel
import com.example.squintboyadvance.shared.model.RomMetadata
import com.example.squintboyadvance.shared.model.SystemType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RomLibraryViewModel : ViewModel() {

    private val _roms = MutableStateFlow(stubRoms())
    val roms: StateFlow<List<RomMetadata>> = _roms.asStateFlow()

    private fun stubRoms(): List<RomMetadata> = listOf(
        RomMetadata(
            id = "1",
            title = "Pokemon Red",
            systemType = SystemType.GB,
            filePath = "/roms/pokemon_red.gb",
            fileSize = 1_048_576L,
            lastPlayed = System.currentTimeMillis() - 3_600_000,
            totalPlayTimeMs = 7_200_000L
        ),
        RomMetadata(
            id = "2",
            title = "Pokemon Crystal",
            systemType = SystemType.GBC,
            filePath = "/roms/pokemon_crystal.gbc",
            fileSize = 2_097_152L,
            lastPlayed = System.currentTimeMillis() - 86_400_000,
            totalPlayTimeMs = 14_400_000L
        ),
        RomMetadata(
            id = "3",
            title = "Pokemon Emerald",
            systemType = SystemType.GBA,
            filePath = "/roms/pokemon_emerald.gba",
            fileSize = 16_777_216L,
            totalPlayTimeMs = 0L
        ),
        RomMetadata(
            id = "4",
            title = "Zelda: Link's Awakening",
            systemType = SystemType.GB,
            filePath = "/roms/zelda_links_awakening.gb",
            fileSize = 524_288L,
            lastPlayed = System.currentTimeMillis() - 604_800_000,
            totalPlayTimeMs = 3_600_000L
        )
    )
}
