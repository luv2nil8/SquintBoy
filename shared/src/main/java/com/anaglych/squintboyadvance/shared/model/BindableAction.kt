package com.anaglych.squintboyadvance.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class BindableAction {
    A, B, START, SELECT,
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
    L, R,
    FF_TOGGLE, FF_SPEED,
    SAVE_STATE, LOAD_STATE;

    val buttonId: ButtonId? get() = when (this) {
        A          -> ButtonId.A
        B          -> ButtonId.B
        START      -> ButtonId.START
        SELECT     -> ButtonId.SELECT
        DPAD_UP    -> ButtonId.DPAD_UP
        DPAD_DOWN  -> ButtonId.DPAD_DOWN
        DPAD_LEFT  -> ButtonId.DPAD_LEFT
        DPAD_RIGHT -> ButtonId.DPAD_RIGHT
        L          -> ButtonId.L
        R          -> ButtonId.R
        else       -> null
    }

    val displayLabel: String get() = when (this) {
        A          -> "A"
        B          -> "B"
        START      -> "Start"
        SELECT     -> "Select"
        DPAD_UP    -> "D↑"
        DPAD_DOWN  -> "D↓"
        DPAD_LEFT  -> "D←"
        DPAD_RIGHT -> "D→"
        L          -> "L"
        R          -> "R"
        FF_TOGGLE  -> "FF"
        FF_SPEED   -> "Speed"
        SAVE_STATE -> "Save"
        LOAD_STATE -> "Load"
    }

    val useIcon: Boolean get() = when (this) {
        DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
        FF_TOGGLE, FF_SPEED, SAVE_STATE, LOAD_STATE -> true
        else -> false
    }
}
