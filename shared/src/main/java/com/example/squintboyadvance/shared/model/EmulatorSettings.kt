package com.example.squintboyadvance.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class EmulatorSettings(
    val audioEnabled: Boolean = false,
    val audioVolume: Float = 0.5f,
    val videoScaling: VideoScaling = VideoScaling.FIT,
    val colorPalette: GbPalette = GbPalette.DEFAULT,
    val frameskip: Int = 0,
    val showFps: Boolean = false,
    val preferredInput: InputDevice = InputDevice.TOUCH,
    val controllerLayout: ControllerLayout = ControllerLayout(),
    val autoSaveEnabled: Boolean = true,
    val autoSaveIntervalSec: Int = 60,
    val turboSpeed: Float = 2.0f
)

@Serializable
enum class VideoScaling(val displayName: String) {
    FIT("Fit to Screen"),
    STRETCH("Stretch"),
    INTEGER("Integer Scale"),
    ORIGINAL("Original Size")
}

@Serializable
enum class GbPalette(val displayName: String) {
    DEFAULT("Default Green"),
    GRAYSCALE("Grayscale"),
    CLASSIC("Classic DMG"),
    CUSTOM("Custom")
}
