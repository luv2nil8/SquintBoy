package com.anaglych.squintboyadvance.presentation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide signal emitted by [RomReceiverService] when the phone replaces a
 * ROM's live .sav (manual upload or archive restore). A live emulator session
 * for that ROM predates the new file: left alone, its stale in-memory state
 * would be re-persisted over the restored save (auto-state push on focus loss,
 * SRAM flush on destroy). EmulatorViewModel listens and resets the core — the
 * .sav stays mmap'd through the in-place write, so a reset boots the game
 * straight into the restored save.
 */
object SaveRestoreSignal {
    private val _saveRestored = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Emits the romId whose live .sav was just replaced. */
    val saveRestored = _saveRestored.asSharedFlow()

    fun emit(romId: String) { _saveRestored.tryEmit(romId) }
}
