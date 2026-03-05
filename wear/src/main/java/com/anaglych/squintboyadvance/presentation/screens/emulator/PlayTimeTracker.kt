package com.anaglych.squintboyadvance.presentation.screens.emulator

import com.anaglych.squintboyadvance.presentation.RomMetadataStore

/**
 * Tracks cumulative play time for a ROM across pause/resume cycles.
 */
class PlayTimeTracker(private val metadataStore: RomMetadataStore) {

    private var romId: String? = null
    private var sessionStartTime: Long = 0L

    fun start(romId: String) {
        this.romId = romId
        sessionStartTime = System.currentTimeMillis()
    }

    /** Flushes elapsed time to metadata and resets the session clock. */
    fun flush() {
        val id = romId ?: return
        if (sessionStartTime == 0L) return

        val sessionMs = System.currentTimeMillis() - sessionStartTime
        metadataStore.update(id) { meta ->
            meta.copy(
                lastPlayed = System.currentTimeMillis(),
                totalPlayTimeMs = meta.totalPlayTimeMs + sessionMs,
            )
        }
        sessionStartTime = System.currentTimeMillis()
    }

    fun stop() {
        flush()
        romId = null
        sessionStartTime = 0L
    }
}
