package com.anaglych.squintboyadvance.presentation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide signal emitted by [RomReceiverService] when a ROM is added or
 * deleted, so [RomLibraryViewModel] can rescan even if the screen is already
 * in the foreground.
 */
object RomLibrarySignal {
    private val _romChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val romChanged = _romChanged.asSharedFlow()

    fun emit() { _romChanged.tryEmit(Unit) }
}
