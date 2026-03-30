package com.anaglych.squintboyadvance.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ButtonPosition(
    val buttonId: ButtonId,
    val xPercent: Float,
    val yPercent: Float,
    val sizePercent: Float = 0.1f,
    val visible: Boolean = true
)

@Serializable
data class ControllerLayout(
    val name: String = "Default",
    val buttons: List<ButtonPosition> = defaultButtons(),
    val visible: Boolean = true,
    val buttonOpacity: Float = 0.3f,
    val pressedOpacity: Float = 0.3f,
    val labelOpacity: Float = 0.8f,
    val labelSize: Float = 13f,
    val hapticFeedback: Boolean = true,
    val layoutType: Int = 0
) {
    companion object {
        fun defaultButtons(): List<ButtonPosition> = listOf(
            ButtonPosition(ButtonId.A, 0.85f, 0.55f),
            ButtonPosition(ButtonId.B, 0.70f, 0.65f),
            ButtonPosition(ButtonId.DPAD_UP, 0.15f, 0.45f),
            ButtonPosition(ButtonId.DPAD_DOWN, 0.15f, 0.65f),
            ButtonPosition(ButtonId.DPAD_LEFT, 0.05f, 0.55f),
            ButtonPosition(ButtonId.DPAD_RIGHT, 0.25f, 0.55f),
            ButtonPosition(ButtonId.START, 0.55f, 0.85f),
            ButtonPosition(ButtonId.SELECT, 0.40f, 0.85f),
            ButtonPosition(ButtonId.L, 0.10f, 0.15f, visible = false),
            ButtonPosition(ButtonId.R, 0.90f, 0.15f, visible = false),
        )
    }
}
