package com.anaglych.squintboyadvance.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class ButtonId {
    A, B, START, SELECT,
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
    L, R
}
