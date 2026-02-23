package com.anaglych.squintboyadvance

import android.content.Intent
import com.anaglych.squintboyadvance.shared.protocol.WearMessageConstants
import com.anaglych.squintboyadvance.ui.RomPickerTrigger
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class MobileListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == WearMessageConstants.PATH_OPEN_ROM_PICKER) {
            // Signal the composable to open the file picker
            RomPickerTrigger.fire()
            // Bring the companion app to the foreground so the picker is visible
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }
    }
}
