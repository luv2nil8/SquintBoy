package com.anaglych.squintboyadvance

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide signal emitted by [MobileListenerService] when the watch
 * requests a purchase via PATH_PURCHASE_ON_PHONE.
 * Collected by CompanionApp to show the upgrade overlay and launch billing.
 */
object PurchaseRequestSignal {
    private val _requests = MutableSharedFlow<Unit>(replay = 1)
    val requests = _requests.asSharedFlow()

    fun emit() { _requests.tryEmit(Unit) }
    @OptIn(ExperimentalCoroutinesApi::class)
    fun consume() { _requests.resetReplayCache() }
}
