package com.anaglych.squintboyadvance.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class EmulatorSettings(
    val audioEnabled: Boolean = false,
    val audioVolume: Float = 0.5f,
    val gbaScaleMode: ScaleMode = ScaleMode.INTEGER,
    val gbaCustomScale: Float = 1.0f,
    val gbScaleMode: ScaleMode = ScaleMode.INTEGER,
    val gbCustomScale: Float = 1.0f,
    val gbaFilterEnabled: Boolean = false,
    val gbFilterEnabled: Boolean = false,
    val gbPaletteIndex: Int = GbColorPalette.DEFAULT_INDEX,
    val gbaFrameskip: Int = 0,
    val gbFrameskip: Int = 0,
    val controllerLayout: ControllerLayout = ControllerLayout(),
)

@Serializable
enum class ScaleMode(val displayName: String) {
    INTEGER("Integer 2x"),
    CUSTOM("Custom")
}

