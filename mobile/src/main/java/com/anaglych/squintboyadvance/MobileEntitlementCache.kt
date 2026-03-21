package com.anaglych.squintboyadvance

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MobileEntitlementCache {
    private const val PREFS_NAME = "entitlement"
    private const val KEY_IS_PRO = "is_pro"

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    fun init(context: Context) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isPro.value = prefs.getBoolean(KEY_IS_PRO, false)
    }

    fun update(context: Context, isPro: Boolean) {
        _isPro.value = isPro
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_PRO, isPro)
            .apply()
    }
}
