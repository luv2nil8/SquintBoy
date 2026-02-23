package com.anaglych.squintboyadvance.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class SaveFileType {
    SRAM_LIVE, SRAM_BACKUP, STATE
}

@Serializable
data class SaveFileEntry(
    val romId: String,
    val fileName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val type: SaveFileType
)

@Serializable
data class SaveListResponse(
    val saves: List<SaveFileEntry>
)
