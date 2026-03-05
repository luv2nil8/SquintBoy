package com.anaglych.squintboyadvance.core

import android.os.Process

class EmulatorThread(
    private val emulator: MgbaEmulator,
    private val onFrame: () -> Unit,
    private val audioPlayer: AudioPlayer? = null,
    private val isRunning: () -> Boolean,
    private val frameskip: Int = -1
) {
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
                // Audio-driven loop: blocking AudioTrack writes pace the emulator
                var autoSkipNext = false
                var fixedSkipCounter = 0

                while (isRunning()) {
                    val frameStart = System.nanoTime()

                    val shouldSkipVideo = when {
                        // Auto: skip if previous frame was slow
                        frameskip == -1 -> autoSkipNext.also { autoSkipNext = false }
                        // Off: never skip
                        frameskip == 0 -> false
                        // Fixed: skip N frames between rendered frames
                        else -> {
                            if (fixedSkipCounter > 0) {
                                fixedSkipCounter--
                                true
                            } else {
                                fixedSkipCounter = frameskip
                                false
                            }
                        }
                    }

                    val framesRead = emulator.runFrameWithAudio(
                        audioBuffer, audioBuffer.size / 2
                    )

                    if (framesRead > 0) {
                        audioPlayer.writeSamples(audioBuffer, framesRead)
                    }

                    if (!shouldSkipVideo) {
                        onFrame()
                    }

                    // Auto frameskip: if this frame took >16.7ms, skip next frame's video
                    if (frameskip == -1) {
                        val elapsed = System.nanoTime() - frameStart
                        if (elapsed > TARGET_FRAME_TIME_NS) {
                            autoSkipNext = true
                        }
                    }
                }
            } else {
                // No audio: fall back to sleep-based pacing
                val targetFrameTimeNs = TARGET_FRAME_TIME_NS
                var autoSkipNext = false
                var fixedSkipCounter = 0

                while (isRunning()) {
                    val frameStart = System.nanoTime()

                    val shouldSkipVideo = when {
                        frameskip == -1 -> autoSkipNext.also { autoSkipNext = false }
                        frameskip == 0 -> false
                        else -> {
                            if (fixedSkipCounter > 0) {
                                fixedSkipCounter--
                                true
                            } else {
                                fixedSkipCounter = frameskip
                                false
                            }
                        }
                    }

                    emulator.runFrame()

                    if (!shouldSkipVideo) {
                        onFrame()
                    }

                    val elapsed = System.nanoTime() - frameStart

                    if (frameskip == -1 && elapsed > targetFrameTimeNs) {
                        autoSkipNext = true
                    }

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
