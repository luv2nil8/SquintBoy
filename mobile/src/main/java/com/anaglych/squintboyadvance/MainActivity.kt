package com.anaglych.squintboyadvance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.anaglych.squintboyadvance.data.sync.SaveSyncSettingsRepository
import com.anaglych.squintboyadvance.ui.CompanionApp
import com.anaglych.squintboyadvance.ui.theme.SquintBoyTheme
import com.anaglych.squintboyadvance.work.DriveSyncWorker
import com.anaglych.squintboyadvance.work.RetentionWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val syncSettings = SaveSyncSettingsRepository.getInstance(this).settings.value
        if (syncSettings.enabled) {
            RetentionWorker.schedulePeriodic(this)
            if (syncSettings.driveEnabled) DriveSyncWorker.enqueue(this)
        }
        setContent {
            SquintBoyTheme {
                CompanionApp()
            }
        }
    }
}
