package com.anaglych.squintboyadvance.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class TransferResult(
    val filename: String,
    val success: Boolean,
    val receivedBytes: Long,
    val expectedBytes: Long,
    val errorMessage: String? = null,
)
