package com.example.squintboyadvance.ui.savemanager

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.squintboyadvance.shared.model.RomMetadata
import com.example.squintboyadvance.shared.model.SaveState
import com.example.squintboyadvance.shared.model.SystemType

class SaveManagerViewModel : ViewModel() {

    private val _roms = MutableLiveData(stubRoms())
    val roms: LiveData<List<RomMetadata>> = _roms

    private val _saves = MutableLiveData<List<SaveState>>(emptyList())
    val saves: LiveData<List<SaveState>> = _saves

    fun selectRom(romId: String) {
        _saves.value = stubSaves(romId)
    }

    private fun stubRoms(): List<RomMetadata> = listOf(
        RomMetadata("1", "Pokemon Red", SystemType.GB, "/roms/pokemon_red.gb"),
        RomMetadata("2", "Pokemon Crystal", SystemType.GBC, "/roms/pokemon_crystal.gbc"),
        RomMetadata("3", "Pokemon Emerald", SystemType.GBA, "/roms/pokemon_emerald.gba")
    )

    private fun stubSaves(romId: String): List<SaveState> = List(3) { i ->
        SaveState(
            id = "${romId}_save_$i",
            romId = romId,
            slotNumber = i + 1,
            timestamp = System.currentTimeMillis() - (i * 86_400_000L),
            filePath = "/saves/$romId/slot_${i + 1}.sav",
            isAutoSave = i == 0
        )
    }
}
