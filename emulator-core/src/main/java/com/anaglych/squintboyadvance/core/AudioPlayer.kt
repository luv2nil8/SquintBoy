package com.anaglych.squintboyadvance.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

class AudioPlayer(val sampleRate: Int = 48000) {

    private var audioTrack: AudioTrack? = null

    init {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = minBuf.coerceAtLeast(4096)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
    }

    /**
     * Sets the audio volume using a perceptual (quadratic) curve.
     * [volume] is 0.0–1.0 where 0.5 sounds like "half as loud".
     */
    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        // Quadratic curve: perceived-linear slider → AudioTrack linear gain.
        // 50% slider → 25% gain ≈ –6 dB, which feels like half volume.
        val gain = clamped * clamped
        audioTrack?.setVolume(gain)
    }

    fun start() {
        audioTrack?.play()
    }

    fun writeSamples(buffer: ShortArray, frameCount: Int) {
        // Blocking write — naturally throttles the emulator to match audio output rate.
        // This is audio-driven frame pacing: the audio clock becomes the master clock.
        audioTrack?.write(buffer, 0, frameCount * 2, AudioTrack.WRITE_BLOCKING)
    }

    /** 2× sampleRate during fast-forward for chipmunk pacing; sampleRate restores normal. */
    fun setPlaybackRate(rate: Int) {
        audioTrack?.playbackRate = rate
    }

    fun pause() {
        audioTrack?.pause()
    }

    fun resume() {
        audioTrack?.play()
    }

    fun stop() {
        audioTrack?.stop()
    }

    fun release() {
        audioTrack?.release()
        audioTrack = null
    }
}
