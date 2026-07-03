package com.anaglych.squintboyadvance.ui

import kotlinx.coroutines.flow.MutableStateFlow

/** Fired when saves land in the archive so open screens can refresh eagerly. */
object ArchiveChangedSignal {
    val counter = MutableStateFlow(0)

    fun emit() {
        counter.value += 1
    }
}
