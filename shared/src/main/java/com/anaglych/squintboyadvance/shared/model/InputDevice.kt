package com.anaglych.squintboyadvance.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class InputDevice(val displayName: String) {
    TOUCH("Touchscreen"),
    BLUETOOTH_GAMEPAD("Bluetooth Gamepad")
}
