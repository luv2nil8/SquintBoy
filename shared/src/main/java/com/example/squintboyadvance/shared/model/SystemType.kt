package com.example.squintboyadvance.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class SystemType(val displayName: String, val fileExtensions: List<String>) {
    GB("Game Boy", listOf("gb")),
    GBC("Game Boy Color", listOf("gbc")),
    GBA("Game Boy Advance", listOf("gba"));

    companion object {
        fun fromExtension(ext: String): SystemType? =
            entries.firstOrNull { ext.lowercase() in it.fileExtensions }
    }
}
