package com.example.squintboyadvance.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class SaveState(
    val id: String,
    val romId: String,
    val slotNumber: Int,
    val timestamp: Long,
    val filePath: String,
    val screenshotPath: String? = null,
    val isAutoSave: Boolean = false,
    val isBackedUp: Boolean = false
)
