package com.anaglych.squintboyadvance.presentation

import android.content.Context
import android.util.Log
import com.anaglych.squintboyadvance.shared.protocol.WearMessageConstants
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class EntitlementRepository private constructor(context: Context) {

    companion object {
        private const val TAG = "EntitlementRepo"
        private const val PREFS_NAME = "entitlement"
        private const val KEY_IS_PRO = "is_pro"
        private const val MAX_REQUEST_RETRIES = 3

        @Volatile
        private var instance: EntitlementRepository? = null

        fun getInstance(context: Context): EntitlementRepository =
            instance ?: synchronized(this) {
                instance ?: EntitlementRepository(context.applicationContext).also { instance = it }
            }
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isPro = MutableStateFlow(prefs.getBoolean(KEY_IS_PRO, false))
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    val isDemo: Boolean get() = !_isPro.value

    init {
        requestEntitlementFromPhone()
    }

    /**
     * Ask the phone for current entitlement state. Called on startup so the watch
     * doesn't rely solely on cached SharedPreferences or waiting for a phone push.
     * Retries with backoff if the phone isn't reachable yet.
     */
    private fun requestEntitlementFromPhone() {
        scope.launch {
            var attempt = 0
            while (attempt < MAX_REQUEST_RETRIES) {
                try {
                    val nodes = Wearable.getNodeClient(appContext).connectedNodes.await()
                    if (nodes.isEmpty()) {
                        Log.d(TAG, "No phone nodes connected, skipping entitlement request")
                        return@launch
                    }
                    for (node in nodes) {
                        Wearable.getMessageClient(appContext).sendMessage(
                            node.id, WearMessageConstants.PATH_ENTITLEMENT_REQUEST,
                            byteArrayOf()
                        ).await()
                    }
                    Log.d(TAG, "Requested entitlement from ${nodes.size} phone nodes")
                    return@launch
                } catch (e: Exception) {
                    attempt++
                    if (attempt >= MAX_REQUEST_RETRIES) {
                        Log.w(TAG, "Failed to request entitlement after $MAX_REQUEST_RETRIES attempts", e)
                    } else {
                        val backoff = 1000L * (1 shl attempt)
                        Log.d(TAG, "Entitlement request failed, retry $attempt in ${backoff}ms")
                        delay(backoff)
                    }
                }
            }
        }
    }

    /** Called when the phone pushes an entitlement update via PATH_ENTITLEMENT_PUSH. */
    fun handleEntitlementPush(isPro: Boolean) {
        Log.i(TAG, "Entitlement push from phone: isPro=$isPro")
        _isPro.value = isPro
        prefs.edit().putBoolean(KEY_IS_PRO, isPro).apply()
    }

    /** Sends a message to the phone to launch the IAP purchase flow. */
    fun requestPurchaseOnPhone() {
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(appContext).connectedNodes.await()
                for (node in nodes) {
                    Wearable.getMessageClient(appContext).sendMessage(
                        node.id, WearMessageConstants.PATH_PURCHASE_ON_PHONE,
                        byteArrayOf()
                    ).await()
                }
                Log.d(TAG, "Purchase request sent to ${nodes.size} phone nodes")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send purchase request to phone", e)
            }
        }
    }
}
