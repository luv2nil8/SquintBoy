package com.example.squintboyadvance.core

import com.example.squintboyadvance.shared.model.ButtonId

class MgbaEmulator {

    // GBA key bitmask mapping (matches GBAKey enum in mGBA)
    // GB keys use the same bit positions (0-7)
    private val keyMap = mapOf(
        ButtonId.A to 0,
        ButtonId.B to 1,
        ButtonId.SELECT to 2,
        ButtonId.START to 3,
        ButtonId.DPAD_RIGHT to 4,
        ButtonId.DPAD_LEFT to 5,
        ButtonId.DPAD_UP to 6,
        ButtonId.DPAD_DOWN to 7,
        ButtonId.R to 8,
        ButtonId.L to 9
    )

    @Volatile
    private var keysBitmask: Int = 0

    val width: Int get() = NativeBridge.nativeGetWidth()
    val height: Int get() = NativeBridge.nativeGetHeight()
    val audioSampleRate: Int get() = NativeBridge.nativeGetAudioSampleRate()

    fun loadRom(path: String): Boolean {
        return NativeBridge.nativeLoadRom(path)
    }

    fun initAudio(outputSampleRate: Int) {
        NativeBridge.nativeInitAudio(outputSampleRate)
    }

    fun runFrame() {
        NativeBridge.nativeSetKeys(keysBitmask)
        NativeBridge.nativeRunFrame()
    }

    fun runFrameWithAudio(buffer: ShortArray, maxFrames: Int): Int {
        NativeBridge.nativeSetKeys(keysBitmask)
        return NativeBridge.nativeRunFrameWithAudio(buffer, maxFrames)
    }

    fun getVideoBuffer(): IntArray? = NativeBridge.nativeGetVideoBuffer()

    fun getVideoBufferInto(buffer: IntArray) = NativeBridge.nativeGetVideoBufferInto(buffer)

    fun pressButton(button: ButtonId) {
        val bit = keyMap[button] ?: return
        keysBitmask = keysBitmask or (1 shl bit)
    }

    fun releaseButton(button: ButtonId) {
        val bit = keyMap[button] ?: return
        keysBitmask = keysBitmask and (1 shl bit).inv()
    }

    fun saveState(path: String): Boolean {
        // SAVESTATE_SAVEDATA | SAVESTATE_CHEATS | SAVESTATE_RTC | SAVESTATE_METADATA
        // (no SAVESTATE_SCREENSHOT — requires USE_PNG which isn't available in NDK build)
        return NativeBridge.nativeSaveState(path, 30)
    }

    fun loadState(path: String): Boolean {
        return NativeBridge.nativeLoadState(path, 30)
    }

    fun captureScreenshot(): IntArray? = NativeBridge.nativeCaptureScreenshot()

    fun setSaveDir(path: String) = NativeBridge.nativeSetSaveDir(path)

    fun loadSaveFile(path: String): Boolean = NativeBridge.nativeLoadSaveFile(path)

    fun reset() = NativeBridge.nativeReset()

    fun destroy() {
        NativeBridge.nativeDestroy()
    }
}
