package com.anaglych.squintboyadvance.core

import android.os.Process

class EmulatorThread(
    private val emulator: MgbaEmulator,
    private val onFrame: () -> Unit,
    private val audioPlayer: AudioPlayer? = null,
    private val isRunning: () -> Boolean,
    private val frameskip: Int = -1,
) {
    /** Fast-forward speed multiplier: 0 = off, 2/3/4 = speed. */
    @Volatile var ffSpeed: Int = 0
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
                // Audio-driven loop. FF runs extra silent frames per tick for Nx speed.
                // AudioTrack drain rate is raised to match so pacing stays correct.
                var autoSkipNext = false
                var fixedSkipCounter = 0
                var prevSpeed = -1 // force sync on first frame
                var ffFrameToggle = false

                while (isRunning()) {
                    val frameStart = System.nanoTime()
                    val speed = ffSpeed
                    val ff = speed >= 2

                    // On speed change: adjust scheduler priority and AudioTrack drain rate.
                    if (speed != prevSpeed) {
                        Process.setThreadPriority(
                            if (ff) Process.THREAD_PRIORITY_DISPLAY
                            else    Process.THREAD_PRIORITY_URGENT_AUDIO
                        )
                        audioPlayer.setPlaybackRate(
                            if (ff) audioPlayer.sampleRate * speed else audioPlayer.sampleRate
                        )
                        if (!ff) ffFrameToggle = false
                        prevSpeed = speed
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
                        // Run (speed-1) extra silent frames, then one with audio.
                        repeat(speed - 1) { emulator.runFrame() }
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
                // No audio: sleep-based pacing. FF runs extra frames and shortens sleep target.
                var autoSkipNext = false
                var fixedSkipCounter = 0
                var prevSpeed = 0
                var ffFrameToggle = false

                while (isRunning()) {
                    val frameStart = System.nanoTime()
                    val speed = ffSpeed
                    val ff = speed >= 2

                    if (speed != prevSpeed) {
                        Process.setThreadPriority(
                            if (ff) Process.THREAD_PRIORITY_DISPLAY
                            else    Process.THREAD_PRIORITY_URGENT_AUDIO
                        )
                        if (!ff) ffFrameToggle = false
                        prevSpeed = speed
                    }

                    val shouldSkipVideo = when {
                        frameskip == -1 -> autoSkipNext.also { autoSkipNext = false }
                        frameskip == 0  -> false
                        else -> {
                            if (fixedSkipCounter > 0) { fixedSkipCounter--; true }
                            else { fixedSkipCounter = frameskip; false }
                        }
                    }

                    if (ff) repeat(speed - 1) { emulator.runFrame() }
                    emulator.runFrame()
                    if (!shouldSkipVideo && (!ff || ffFrameToggle)) onFrame()
                    if (ff) ffFrameToggle = !ffFrameToggle

                    val elapsed = System.nanoTime() - frameStart
                    val frameTarget = if (ff) TARGET_FRAME_TIME_NS / speed.toLong() else TARGET_FRAME_TIME_NS
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
