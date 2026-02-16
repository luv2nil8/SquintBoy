package com.example.squintboyadvance.shared.emulator

import com.example.squintboyadvance.shared.model.ButtonId
import com.example.squintboyadvance.shared.model.RomMetadata
import com.example.squintboyadvance.shared.model.SaveState

/**
 * Stub interface for the emulator core.
 * A real implementation backed by mGBA via JNI will be provided in a future sprint.
 */
interface EmulatorInterface {
    val state: EmulatorState

    fun loadRom(rom: RomMetadata): Boolean
    fun start()
    fun pause()
    fun resume()
    fun stop()

    fun pressButton(button: ButtonId)
    fun releaseButton(button: ButtonId)

    fun saveState(slot: Int): SaveState?
    fun loadState(saveState: SaveState): Boolean

    fun getFrameBuffer(): IntArray?
    fun getAudioBuffer(): ShortArray?
}
