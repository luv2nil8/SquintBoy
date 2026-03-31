package com.anaglych.squintboyadvance

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ReviewRequestSignal {
    private val _requests = MutableSharedFlow<Unit>(replay = 1)
    val requests = _requests.asSharedFlow()

    fun emit() { _requests.tryEmit(Unit) }
    @OptIn(ExperimentalCoroutinesApi::class)
    fun consume() { _requests.resetReplayCache() }
}
