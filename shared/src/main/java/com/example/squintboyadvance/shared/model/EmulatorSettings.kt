package com.example.squintboyadvance.shared.model

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
    val colorPalette: GbPalette = GbPalette.DEFAULT,
    val frameskip: Int = -1,
    val showFps: Boolean = false,
    val preferredInput: InputDevice = InputDevice.TOUCH,
    val controllerLayout: ControllerLayout = ControllerLayout(),
    val autoSaveEnabled: Boolean = true,
    val autoSaveIntervalSec: Int = 60,
    val turboSpeed: Float = 2.0f
)

@Serializable
enum class ScaleMode(val displayName: String) {
    INTEGER("Integer 2x"),
    CUSTOM("Custom")
}

@Serializable
enum class GbPalette(val displayName: String) {
    DEFAULT("Default Green"),
    GRAYSCALE("Grayscale"),
    CLASSIC("Classic DMG"),
    CUSTOM("Custom")
}
