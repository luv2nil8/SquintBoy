package com.example.squintboyadvance.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class WatchRomEntry(
    val romId: String,
    val systemType: SystemType,
    val fileSize: Long,
    val lastPlayed: Long? = null,
    val totalPlayTimeMs: Long = 0L
)

@Serializable
data class RomListResponse(
    val roms: List<WatchRomEntry>
)
