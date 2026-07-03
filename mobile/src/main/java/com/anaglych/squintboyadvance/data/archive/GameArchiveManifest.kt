package com.anaglych.squintboyadvance.data.archive

import kotlinx.serialization.Serializable

/**
 * Per-game "manifest.json" stored beside the save files, making the archive
 * folder self-describing: a fresh install (or a wiped Room DB) can rebuild the
 * full index — including Drive file ids — by rescanning the folder.
 */
@Serializable
data class GameArchiveManifest(
    val version: Int = 1,
    val romId: String,
    val baseName: String,
    val systemType: String,
    val entries: List<ManifestEntry> = emptyList(),
)

@Serializable
data class ManifestEntry(
    val fileName: String,
    val timestampMs: Long,
    val sizeBytes: Long,
    val sha256: String,
    val pinned: Boolean = false,
    val note: String? = null,
    val driveFileId: String? = null,
)
