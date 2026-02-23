package com.anaglych.squintboyadvance.core

object NativeBridge {
    init {
        System.loadLibrary("mgba_jni")
    }

    external fun nativeLoadRom(path: String): Boolean
    external fun nativeInitAudio(outputSampleRate: Int)
    external fun nativeRunFrame()
    external fun nativeRunFrameWithAudio(buffer: ShortArray, maxFrames: Int): Int
    external fun nativeGetVideoBuffer(): IntArray?
    external fun nativeGetVideoBufferInto(outBuffer: IntArray)
    external fun nativeGetWidth(): Int
    external fun nativeGetHeight(): Int
    external fun nativeSetKeys(keys: Int)
    external fun nativeGetAudioSampleRate(): Int
    external fun nativeSaveState(path: String, flags: Int): Boolean
    external fun nativeLoadState(path: String, flags: Int): Boolean
    external fun nativeCaptureScreenshot(): IntArray?
    external fun nativeSetSaveDir(path: String)
    external fun nativeLoadSaveFile(path: String): Boolean
    // colors: 4 ints in mGBA order [lightest→darkest], each 0xFFRRGGBB Android ARGB
    external fun nativeSetGbPalette(colors: IntArray)
    external fun nativeReset()
    external fun nativeDestroy()
}
