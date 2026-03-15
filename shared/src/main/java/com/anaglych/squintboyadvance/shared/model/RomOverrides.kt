package com.anaglych.squintboyadvance.shared.model

import kotlinx.serialization.Serializable

/**
 * Per-ROM display settings that override the global [EmulatorSettings].
 *
 * [active] controls which bank is in use: `true` → reads/writes go to this override,
 * `false` → globals are used but the override values are preserved for next time.
 * Null fields mean "inherit from global".
 *
 * Stored keyed by romId in [EmulatorSettings.romOverrides].
 */
@Serializable
data class RomOverrides(
    val active: Boolean = false,
    val gbaScaleMode: ScaleMode? = null,
    val gbaCustomScale: Float? = null,
    val gbScaleMode: ScaleMode? = null,
    val gbCustomScale: Float? = null,
    val gbaFilterEnabled: Boolean? = null,
    val gbFilterEnabled: Boolean? = null,
    val gbaFrameskip: Int? = null,
    val gbFrameskip: Int? = null,
    val gbPaletteIndex: Int? = null,
) {
    /**
     * Returns `true` if any override field relevant to [isGba] differs from the
     * corresponding global value. The [active] flag is ignored — this purely
     * compares stored values.
     */
    fun differsFrom(global: EmulatorSettings, isGba: Boolean): Boolean {
        return if (isGba) {
            (gbaScaleMode != null && gbaScaleMode != global.gbaScaleMode) ||
            (gbaCustomScale != null && gbaCustomScale != global.gbaCustomScale) ||
            (gbaFilterEnabled != null && gbaFilterEnabled != global.gbaFilterEnabled) ||
            (gbaFrameskip != null && gbaFrameskip != global.gbaFrameskip)
        } else {
            (gbScaleMode != null && gbScaleMode != global.gbScaleMode) ||
            (gbCustomScale != null && gbCustomScale != global.gbCustomScale) ||
            (gbFilterEnabled != null && gbFilterEnabled != global.gbFilterEnabled) ||
            (gbFrameskip != null && gbFrameskip != global.gbFrameskip) ||
            (gbPaletteIndex != null && gbPaletteIndex != global.gbPaletteIndex)
        }
    }
}
