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
            WearMessageConstants.PATH_ENTITLEMENT_PUSH -> {
                val isPro = String(event.data, Charsets.UTF_8) == "1"
                MobileEntitlementCache.update(this, isPro)
                Log.i(TAG, "Entitlement updated from watch: isPro=$isPro")
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
