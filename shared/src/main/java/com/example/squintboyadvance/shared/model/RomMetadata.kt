package com.example.squintboyadvance.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class RomMetadata(
    val id: String,
    val title: String,
    val systemType: SystemType,
    val filePath: String,
    val fileSize: Long = 0L,
    val lastPlayed: Long? = null,
    val totalPlayTimeMs: Long = 0L,
    val thumbnailPath: String? = null,
    val addedTimestamp: Long = 0L
)
