package com.anaglych.squintboyadvance.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class GamepadMapping(
    val buttonKeyCodes: Map<String, Int> = defaultButtonKeyCodes(),
) {
    fun forButton(button: ButtonId): Int? = buttonKeyCodes[button.name]

    fun withButton(button: ButtonId, keyCode: Int) =
        copy(buttonKeyCodes = buttonKeyCodes + (button.name to keyCode))

    fun withoutButton(button: ButtonId) =
        copy(buttonKeyCodes = buttonKeyCodes - button.name)

    fun fromKeyCode(keyCode: Int): ButtonId? {
        // Check user mapping first
        val fromMap = buttonKeyCodes.entries.firstOrNull { it.value == keyCode }
            ?.key?.let { runCatching { ButtonId.valueOf(it) }.getOrNull() }
        if (fromMap != null) return fromMap
        // Fall back to system-remap extras (e.g. PS Circle → KEYCODE_BACK → B)
        return SYSTEM_REMAP_EXTRAS[keyCode]
            ?.let { runCatching { ButtonId.valueOf(it) }.getOrNull() }
    }

    companion object {
        // Keycodes are android.view.KeyEvent constants, inlined to avoid Android import.
        fun defaultButtonKeyCodes(): Map<String, Int> = mapOf(
            "A"          to 96,   // KEYCODE_BUTTON_A  (PS Cross / Xbox A)
            "B"          to 97,   // KEYCODE_BUTTON_B  (PS Circle / Xbox B)
            "START"      to 108,  // KEYCODE_BUTTON_START
            "SELECT"     to 109,  // KEYCODE_BUTTON_SELECT
            "DPAD_UP"    to 19,   // KEYCODE_DPAD_UP
            "DPAD_DOWN"  to 20,   // KEYCODE_DPAD_DOWN
            "DPAD_LEFT"  to 21,   // KEYCODE_DPAD_LEFT
            "DPAD_RIGHT" to 22,   // KEYCODE_DPAD_RIGHT
            "L"          to 102,  // KEYCODE_BUTTON_L1
            "R"          to 103,  // KEYCODE_BUTTON_R1
        )

        // Some controllers/drivers remap buttons to system keycodes before delivery.
        // These extras let handleGamepadKeyDown route them even if not in the main map.
        // KEYCODE_BACK (4)         → PS Circle remapped by Android HID driver
        // KEYCODE_DPAD_CENTER (23) → PS Cross / Xbox A on some drivers
        val SYSTEM_REMAP_EXTRAS: Map<Int, String> = mapOf(
            4  to "B",   // KEYCODE_BACK        → B
            23 to "A",   // KEYCODE_DPAD_CENTER → A
        )

        fun keyCodeLabel(keyCode: Int): String = when (keyCode) {
            96  -> "A"
            97  -> "B"
            99  -> "X"
            100 -> "Y"
            102 -> "L1"
            103 -> "R1"
            104 -> "L2"
            105 -> "R2"
            106 -> "L3"
            107 -> "R3"
            108 -> "Start"
            109 -> "Select"
            19  -> "D↑"
            20  -> "D↓"
            21  -> "D←"
            22  -> "D→"
            else -> "#$keyCode"
        }
    }
}
