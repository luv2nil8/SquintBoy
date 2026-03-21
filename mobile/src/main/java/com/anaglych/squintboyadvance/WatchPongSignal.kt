package com.anaglych.squintboyadvance

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object WatchPongSignal {
    private val _pongs = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val pongs = _pongs.asSharedFlow()

    fun emit() { _pongs.tryEmit(Unit) }
}
