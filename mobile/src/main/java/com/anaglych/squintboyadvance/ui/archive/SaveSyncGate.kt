package com.anaglych.squintboyadvance.ui.archive

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Availability gate for the save-sync feature. Every UI gate (promo card, setup
 * route, archive section) reads only this.
 *
 * BASE: always available. The Monetize branch replaces the body of
 * [isAvailable] with the Pro entitlement check — this file is the sole
 * intended divergence between the branches for this feature.
 */
object SaveSyncGate {
    private val always = MutableStateFlow(true)

    @Suppress("UNUSED_PARAMETER")
    fun isAvailable(context: Context): StateFlow<Boolean> = always
}
