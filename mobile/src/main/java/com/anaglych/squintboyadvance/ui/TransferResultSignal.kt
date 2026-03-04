package com.anaglych.squintboyadvance.ui

import com.anaglych.squintboyadvance.shared.model.TransferResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object TransferResultSignal {
    private val _results = MutableSharedFlow<TransferResult>(extraBufferCapacity = 8)
    val results = _results.asSharedFlow()

    fun emit(result: TransferResult) { _results.tryEmit(result) }
}
