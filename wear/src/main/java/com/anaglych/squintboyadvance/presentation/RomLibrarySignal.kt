package com.anaglych.squintboyadvance.presentation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class TransferEvent(
    val filename: String,
    val success: Boolean,
    val errorMessage: String? = null,
)

/**
 * Process-wide signal emitted by [RomReceiverService] when a ROM is added or
 * deleted, so [RomLibraryViewModel] can rescan even if the screen is already
 * in the foreground.
 */
object RomLibrarySignal {
    private val _romChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val romChanged = _romChanged.asSharedFlow()

    private val _transferEvent = MutableSharedFlow<TransferEvent>(extraBufferCapacity = 8)
    val transferEvent = _transferEvent.asSharedFlow()

    fun emit() { _romChanged.tryEmit(Unit) }

    fun emitTransfer(filename: String, success: Boolean, errorMessage: String? = null) {
        _transferEvent.tryEmit(TransferEvent(filename, success, errorMessage))
    }
}
