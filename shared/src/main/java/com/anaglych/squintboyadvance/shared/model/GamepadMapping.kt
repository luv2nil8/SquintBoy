package com.anaglych.squintboyadvance.shared.model

import kotlinx.serialization.Serializable

/**
 * Maps each [BindableAction] to a list of combo-sets.  Each combo-set is a set of
 * simultaneously-held keycodes (up to 3) that triggers the action.  A single action
 * can have multiple independent combo-sets (e.g. both L1 and LB fire SAVE_STATE).
 *
 * The JSON key is BindableAction.name so the field name "bindings" (vs the old
 * "buttonKeyCodes") causes silent migration to defaults on first launch after upgrade.
 */
@Serializable
data class GamepadMapping(
    val bindings: Map<String, List<Set<Int>>> = defaultBindings(),
) {
    // ── Primary API ────────────────────────────────────────────────────────────

    fun forAction(action: BindableAction): List<Set<Int>> =
        bindings[action.name] ?: emptyList()

    fun withCombo(action: BindableAction, combo: Set<Int>): GamepadMapping {
        val m = HashMap(bindings)
        val appended = ArrayList<Set<Int>>(m[action.name] ?: emptyList<Set<Int>>()).also { it.add(combo) }
        m[action.name] = appended
        return copy(bindings = m)
    }

    fun withoutComboAt(action: BindableAction, index: Int): GamepadMapping {
        val list = forAction(action).toMutableList().also { it.removeAt(index) }
        val m = HashMap(bindings)
        if (list.isEmpty()) m.remove(action.name) else m[action.name] = list
        return copy(bindings = m)
    }

    fun withCombosForAction(action: BindableAction, combos: List<Set<Int>>): GamepadMapping {
        val m = HashMap(bindings)
        if (combos.isEmpty()) m.remove(action.name) else m[action.name] = combos
        return copy(bindings = m)
    }

    /**
     * Returns the action whose stored combo exactly matches [held], or null.
     * Checks registered bindings first, then [SYSTEM_REMAP_EXTRAS] for single-key fallbacks.
     */
    fun fromHeldKeys(held: Set<Int>): BindableAction? {
        if (held.isEmpty()) return null
        for (action in BindableAction.values()) {
            if (forAction(action).any { combo -> combo == held }) return action
        }
        if (held.size == 1) {
            val name = SYSTEM_REMAP_EXTRAS[held.first()] ?: return null
            return runCatching { BindableAction.valueOf(name) }.getOrNull()
        }
        return null
    }

    // ── Backward-compat wrappers (used by mobile BtControllerExpand) ───────────

    fun forButton(button: ButtonId): Int? {
        val action = BindableAction.valueOf(button.name)
        return forAction(action).firstOrNull()?.firstOrNull()
    }

    fun withButton(button: ButtonId, keyCode: Int): GamepadMapping {
        val action = BindableAction.valueOf(button.name)
        val existing = forAction(action)
        val updated = listOf(setOf(keyCode)) + if (existing.isEmpty()) emptyList() else existing.drop(1)
        return copy(bindings = bindings + (action.name to updated))
    }

    fun withoutButton(button: ButtonId): GamepadMapping {
        val action = BindableAction.valueOf(button.name)
        return copy(bindings = bindings - action.name)
    }

    fun fromKeyCode(keyCode: Int): ButtonId? =
        fromHeldKeys(setOf(keyCode))?.buttonId

    // ── Companion ──────────────────────────────────────────────────────────────

    companion object {
        // Synthetic keycodes for analog stick axes — outside real Android keycode range.
        // HAT D-pad uses KEYCODE_DPAD_* (19–22); these are for AXIS_X/Y only.
        const val AXIS_UP    = 1001
        const val AXIS_DOWN  = 1002
        const val AXIS_LEFT  = 1003
        const val AXIS_RIGHT = 1004

        fun defaultBindings(): Map<String, List<Set<Int>>> = mapOf(
            "A"          to listOf(setOf(96)),    // KEYCODE_BUTTON_A
            "B"          to listOf(setOf(97)),    // KEYCODE_BUTTON_B
            "START"      to listOf(setOf(108)),   // KEYCODE_BUTTON_START
            "SELECT"     to listOf(setOf(109)),   // KEYCODE_BUTTON_SELECT
            "DPAD_UP"    to listOf(setOf(19)),    // KEYCODE_DPAD_UP
            "DPAD_DOWN"  to listOf(setOf(20)),    // KEYCODE_DPAD_DOWN
            "DPAD_LEFT"  to listOf(setOf(21)),    // KEYCODE_DPAD_LEFT
            "DPAD_RIGHT" to listOf(setOf(22)),    // KEYCODE_DPAD_RIGHT
            "L"          to listOf(setOf(102)),   // KEYCODE_BUTTON_L1
            "R"          to listOf(setOf(103)),   // KEYCODE_BUTTON_R1
        )

        // Some drivers remap buttons to system keycodes before delivery.
        // These let fromHeldKeys route them even when not in the main map.
        val SYSTEM_REMAP_EXTRAS: Map<Int, String> = mapOf(
            4  to "B",   // KEYCODE_BACK        → B
            23 to "A",   // KEYCODE_DPAD_CENTER → A
        )

        fun keyCodeLabel(keyCode: Int): String = when (keyCode) {
            96   -> "A"
            97   -> "B"
            99   -> "X"
            100  -> "Y"
            102  -> "L1"
            103  -> "R1"
            104  -> "L2"
            105  -> "R2"
            106  -> "L3"
            107  -> "R3"
            108  -> "Start"
            109  -> "Select"
            19   -> "D↑"
            20   -> "D↓"
            21   -> "D←"
            22   -> "D→"
            AXIS_UP    -> "L↑"
            AXIS_DOWN  -> "L↓"
            AXIS_LEFT  -> "L←"
            AXIS_RIGHT -> "L→"
            else -> "#$keyCode"
        }

        fun comboLabel(combo: Set<Int>): String =
            combo.sorted().joinToString("+") { keyCodeLabel(it) }
    }
}
