package com.anaglych.squintboyadvance.shared.model

import kotlinx.serialization.Serializable

/**
 * Per-ROM display settings that override the global [EmulatorSettings].
 * Null means "inherit from global". Stored keyed by romId in [EmulatorSettings.romOverrides].
 */
@Serializable
data class RomOverrides(
    val gbaScaleMode: ScaleMode? = null,
    val gbaCustomScale: Float? = null,
    val gbScaleMode: ScaleMode? = null,
    val gbCustomScale: Float? = null,
    val gbaFilterEnabled: Boolean? = null,
    val gbFilterEnabled: Boolean? = null,
    val gbaFrameskip: Int? = null,
    val gbFrameskip: Int? = null,
    val gbPaletteIndex: Int? = null,
)
