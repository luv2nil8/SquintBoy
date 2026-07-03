package com.anaglych.squintboyadvance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.anaglych.squintboyadvance.data.sync.SaveSyncSettingsRepository
import com.anaglych.squintboyadvance.ui.CompanionApp
import com.anaglych.squintboyadvance.ui.theme.SquintBoyTheme
import com.anaglych.squintboyadvance.work.RetentionWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (SaveSyncSettingsRepository.getInstance(this).settings.value.enabled) {
            RetentionWorker.schedulePeriodic(this)
        }
        setContent {
            SquintBoyTheme {
                CompanionApp()
            }
        }
    }
}
