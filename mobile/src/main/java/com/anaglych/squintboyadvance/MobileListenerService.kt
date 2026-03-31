package com.anaglych.squintboyadvance

import android.content.Intent
import android.util.Log
import com.anaglych.squintboyadvance.shared.model.TransferResult
import com.anaglych.squintboyadvance.shared.protocol.WearMessageConstants
import com.anaglych.squintboyadvance.ui.RomPickerTrigger
import com.anaglych.squintboyadvance.ui.TransferResultSignal
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.serialization.json.Json

class MobileListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "MobileListenerService"
    }

    private val json = Json { ignoreUnknownKeys = true }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearMessageConstants.PATH_OPEN_ROM_PICKER -> {
                RomPickerTrigger.fire()
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            }
            WearMessageConstants.PATH_PHONE_PING -> {
                try {
                    Wearable.getMessageClient(this).sendMessage(
                        event.sourceNodeId,
                        WearMessageConstants.PATH_PHONE_PONG,
                        byteArrayOf(),
                    )
                    Log.d(TAG, "Replied pong to ${event.sourceNodeId}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reply pong", e)
                }
            }
            WearMessageConstants.PATH_WATCH_PONG -> {
                WatchPongSignal.emit()
                Log.d(TAG, "Received watch pong from ${event.sourceNodeId}")
            }
            WearMessageConstants.PATH_PURCHASE_ON_PHONE -> {
                PurchaseRequestSignal.emit()
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                Log.i(TAG, "Purchase requested from watch — launching activity")
            }
            WearMessageConstants.PATH_TRIGGER_REVIEW -> {
                ReviewRequestSignal.emit()
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                Log.i(TAG, "Review requested from watch — launching activity")
            }
            WearMessageConstants.PATH_ENTITLEMENT_REQUEST -> {
                // Watch asks for current entitlement status (startup sync)
                val billing = MobileBillingManager.getInstance(this)
                billing.handleEntitlementRequest(event.sourceNodeId)
                Log.i(TAG, "Entitlement request from watch ${event.sourceNodeId}")
            }
            WearMessageConstants.PATH_ROM_TRANSFER_RESULT -> {
                try {
                    val payload = String(event.data, Charsets.UTF_8)
                    val result = json.decodeFromString(TransferResult.serializer(), payload)
                    TransferResultSignal.emit(result)
                    Log.i(TAG, "Transfer result for ${result.filename}: success=${result.success}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode transfer result", e)
                }
            }
        }
    }
}
