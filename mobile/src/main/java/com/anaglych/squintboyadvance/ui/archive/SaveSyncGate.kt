package com.anaglych.squintboyadvance.ui.archive

import android.content.Context
import com.anaglych.squintboyadvance.MobileBillingManager
import kotlinx.coroutines.flow.StateFlow

/**
 * Availability gate for the save-sync feature. Every UI gate (promo card, setup
 * route, archive section) reads only this.
 *
 * Monetize: gated on the Pro entitlement. On BASE this returns an always-true
 * flow — this file is the sole intended divergence between the branches for
 * this feature.
 */
object SaveSyncGate {
    fun isAvailable(context: Context): StateFlow<Boolean> =
        MobileBillingManager.getInstance(context.applicationContext).isPro
}
