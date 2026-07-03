package com.anaglych.squintboyadvance.shared.model

import kotlinx.serialization.Serializable

/**
 * Metadata line preceding each blob in a PATH_SAVE_ARCHIVE_PUSH drain.
 * The phone recomputes sha256 over the received bytes and only acks OK on match.
 */
@Serializable
data class SaveArchiveEntryMeta(
    /** ROM filename with extension, e.g. "Pokemon.gba". */
    val romId: String,
    /** Capture time on the watch (epoch ms). */
    val timestampMs: Long,
    val sizeBytes: Long,
    /** Lowercase hex SHA-256 of the blob. */
    val sha256: String,
)

/**
 * Phone-authoritative save-sync configuration pushed to the watch on
 * PATH_SAVE_SYNC_CONFIG. The watch persists it and archives silently when enabled.
 */
@Serializable
data class SaveSyncConfig(
    val enabled: Boolean = false,
)
