package com.anaglych.squintboyadvance.core

import android.os.Process

class EmulatorThread(
    private val emulator: MgbaEmulator,
    private val onFrame: () -> Unit,
    audioPlayer: AudioPlayer? = null,
    private val isRunning: () -> Boolean,
    private val frameskip: Int = -1,
) {
    /** Fast-forward speed multiplier: 0 = off, 2/3/4 = speed. */
    @Volatile var ffSpeed: Int = 0

    /** Hot-swappable audio player. Set to non-null to switch to audio-driven pacing. */
    @Volatile var audioPlayer: AudioPlayer? = audioPlayer

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

            var autoSkipNext = false
            var fixedSkipCounter = 0
            var prevSpeed = -1
            var ffFrameToggle = true

            while (isRunning()) {
                val frameStart = System.nanoTime()
                val speed = ffSpeed
                val ff = speed >= 2
                val player = audioPlayer

                // On speed change: adjust scheduler priority and AudioTrack drain rate.
                if (speed != prevSpeed) {
                    Process.setThreadPriority(
                        if (ff) Process.THREAD_PRIORITY_DISPLAY
                        else    Process.THREAD_PRIORITY_URGENT_AUDIO
                    )
                    if (player != null) {
                        player.setPlaybackRate(
                            if (ff) player.sampleRate * speed else player.sampleRate
                        )
                    }
                    if (!ff) ffFrameToggle = true
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

                if (player != null) {
                    // Audio-driven pacing
                    if (ff) {
                        repeat(speed - 1) { emulator.runFrame() }
                        val framesRead = emulator.runFrameWithAudio(audioBuffer, audioBuffer.size / 2)
                        if (framesRead > 0) player.writeSamples(audioBuffer, framesRead)
                        if (!shouldSkipVideo && ffFrameToggle) onFrame()
                        ffFrameToggle = !ffFrameToggle
                    } else {
                        val framesRead = emulator.runFrameWithAudio(audioBuffer, audioBuffer.size / 2)
                        if (framesRead > 0) player.writeSamples(audioBuffer, framesRead)
                        if (!shouldSkipVideo) onFrame()
                        if (frameskip == -1) {
                            val elapsed = System.nanoTime() - frameStart
                            if (elapsed > TARGET_FRAME_TIME_NS) autoSkipNext = true
                        }
                    }
                } else {
                    // Sleep-based pacing (no audio)
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
