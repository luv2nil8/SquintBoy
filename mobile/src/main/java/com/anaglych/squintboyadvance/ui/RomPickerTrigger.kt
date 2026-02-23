package com.anaglych.squintboyadvance.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton trigger used to signal the ROM file picker from outside the composition
 * (e.g. from MobileListenerService when the watch requests it).
 */
object RomPickerTrigger {
    private val _shouldOpen = MutableStateFlow(false)
    val shouldOpen: StateFlow<Boolean> = _shouldOpen.asStateFlow()

    fun fire() { _shouldOpen.value = true }
    fun consume() { _shouldOpen.value = false }
}
