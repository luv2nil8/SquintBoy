package com.example.squintboyadvance.core

import android.os.Process

class EmulatorThread(
    private val emulator: MgbaEmulator,
    private val onFrame: () -> Unit,
    private val audioPlayer: AudioPlayer? = null,
    private val isRunning: () -> Boolean
) {

    private var thread: Thread? = null
    // Enough for ~2 frames at 48000 Hz (~1600 samples/frame stereo)
    private val audioBuffer = ShortArray(4096)

    fun start() {
        if (thread?.isAlive == true) return

        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            if (audioPlayer != null) {
                // Audio-driven loop: blocking AudioTrack writes pace the emulator
                while (isRunning()) {
                    val framesRead = emulator.runFrameWithAudio(
                        audioBuffer, audioBuffer.size / 2
                    )

                    if (framesRead > 0) {
                        // Write audio BEFORE posting video — audio enters the
                        // AudioTrack pipeline first so it has time to work through
                        // the buffer while Compose renders the frame on next vsync.
                        // Blocking write IS the frame pacer.
                        audioPlayer.writeSamples(audioBuffer, framesRead)
                    }
                    onFrame()
                }
            } else {
                // No audio: fall back to sleep-based pacing
                val targetFrameTimeNs = 16_666_667L // ~60fps
                while (isRunning()) {
                    val frameStart = System.nanoTime()
                    emulator.runFrame()
                    onFrame()

                    val elapsed = System.nanoTime() - frameStart
                    val sleepNs = targetFrameTimeNs - elapsed
                    if (sleepNs > 1_000_000) {
                        Thread.sleep(sleepNs / 1_000_000, (sleepNs % 1_000_000).toInt())
                    }
                }
            }
        }, "EmulatorThread").also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        // isRunning() will return false, causing the loop to exit
        thread?.join(2000)
        thread = null
    }
}
