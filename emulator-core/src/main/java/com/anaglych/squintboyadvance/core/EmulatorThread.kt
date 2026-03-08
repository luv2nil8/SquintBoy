package com.anaglych.squintboyadvance.core

import android.os.Process

class EmulatorThread(
    private val emulator: MgbaEmulator,
    private val onFrame: () -> Unit,
    private val audioPlayer: AudioPlayer? = null,
    private val isRunning: () -> Boolean,
    private val frameskip: Int = -1,
) {
    /** Set to true at any time to run an extra silent frame per audio write (2× speed). */
    @Volatile var fastForward: Boolean = false
    companion object {
        /** ~60 fps target (nanoseconds per frame). */
        private const val TARGET_FRAME_TIME_NS = 16_666_667L
        /** Audio buffer size: ~2 frames at 48 kHz (~1600 samples/frame stereo). */
        private const val AUDIO_BUFFER_SIZE = 4096
    }

    private var thread: Thread? = null
    private val audioBuffer = ShortArray(AUDIO_BUFFER_SIZE)

    fun start() {
        if (thread?.isAlive == true) return

        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            if (audioPlayer != null) {
                // Audio-driven loop at 1×. FF skips the AudioTrack write, uses sleep-based
                // pacing, drops thread priority off the big core, and renders every other
                // iteration (GPU sees 60fps worth of work regardless of game speed).
                var autoSkipNext = false
                var fixedSkipCounter = 0
                // Force-sync on first frame: if a previous thread exited in FF mode the
                // AudioTrack rate may be stale. !fastForward guarantees a rate update fires.
                var prevFf = !fastForward
                var ffFrameToggle = false

                while (isRunning()) {
                    val frameStart = System.nanoTime()
                    val ff = fastForward

                    // On FF state change: adjust scheduler priority and AudioTrack drain rate.
                    // 2× playback rate makes the write block for the same wall time even
                    // with 2 frames of audio, so pacing stays correct and audio plays at
                    // 2× pitch. Muted sessions use the no-audio loop so this never fires.
                    if (ff != prevFf) {
                        Process.setThreadPriority(
                            if (ff) Process.THREAD_PRIORITY_DISPLAY
                            else    Process.THREAD_PRIORITY_URGENT_AUDIO
                        )
                        audioPlayer.setPlaybackRate(
                            if (ff) audioPlayer.sampleRate * 2 else audioPlayer.sampleRate
                        )
                        if (!ff) ffFrameToggle = false
                        prevFf = ff
                    }

                    val shouldSkipVideo = when {
                        frameskip == -1 -> autoSkipNext.also { autoSkipNext = false }
                        frameskip == 0  -> false
                        else -> {
                            if (fixedSkipCounter > 0) { fixedSkipCounter--; true }
                            else { fixedSkipCounter = frameskip; false }
                        }
                    }

                    if (ff) {
                        // Extra silent frame fills ring buffer with 2 frames of audio.
                        // runFrameWithAudio drains both; at 2× drain rate the write still
                        // blocks ~16.7 ms → 2 frames per tick = 2× speed with audio.
                        emulator.runFrame()
                        val framesRead = emulator.runFrameWithAudio(audioBuffer, audioBuffer.size / 2)
                        if (framesRead > 0) audioPlayer.writeSamples(audioBuffer, framesRead)
                        if (!shouldSkipVideo && ffFrameToggle) onFrame()
                        ffFrameToggle = !ffFrameToggle
                    } else {
                        val framesRead = emulator.runFrameWithAudio(audioBuffer, audioBuffer.size / 2)
                        if (framesRead > 0) audioPlayer.writeSamples(audioBuffer, framesRead)
                        if (!shouldSkipVideo) onFrame()
                        if (frameskip == -1) {
                            val elapsed = System.nanoTime() - frameStart
                            if (elapsed > TARGET_FRAME_TIME_NS) autoSkipNext = true
                        }
                    }
                }
            } else {
                // No audio: sleep-based pacing. FF halves the sleep target, drops priority,
                // and renders every other iteration.
                var autoSkipNext = false
                var fixedSkipCounter = 0
                var prevFf = false
                var ffFrameToggle = false

                while (isRunning()) {
                    val frameStart = System.nanoTime()
                    val ff = fastForward

                    if (ff != prevFf) {
                        Process.setThreadPriority(
                            if (ff) Process.THREAD_PRIORITY_DISPLAY
                            else    Process.THREAD_PRIORITY_URGENT_AUDIO
                        )
                        if (!ff) ffFrameToggle = false
                        prevFf = ff
                    }

                    val shouldSkipVideo = when {
                        frameskip == -1 -> autoSkipNext.also { autoSkipNext = false }
                        frameskip == 0  -> false
                        else -> {
                            if (fixedSkipCounter > 0) { fixedSkipCounter--; true }
                            else { fixedSkipCounter = frameskip; false }
                        }
                    }

                    if (ff) emulator.runFrame()
                    emulator.runFrame()
                    if (!shouldSkipVideo && (!ff || ffFrameToggle)) onFrame()
                    if (ff) ffFrameToggle = !ffFrameToggle

                    val elapsed = System.nanoTime() - frameStart
                    val frameTarget = if (ff) TARGET_FRAME_TIME_NS / 2 else TARGET_FRAME_TIME_NS
                    if (frameskip == -1 && elapsed > frameTarget) autoSkipNext = true
                    val sleepNs = frameTarget - elapsed
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
