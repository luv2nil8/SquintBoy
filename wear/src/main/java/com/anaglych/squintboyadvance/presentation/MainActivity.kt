package com.anaglych.squintboyadvance.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.anaglych.squintboyadvance.presentation.navigation.WearNavGraph
import com.anaglych.squintboyadvance.presentation.sync.SaveSyncConfigRepository
import com.anaglych.squintboyadvance.presentation.theme.SquintBoyAdvanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        // Warm the save-sync config so the phone refresh fires before any game session.
        SaveSyncConfigRepository.getInstance(this)

        setContent {
            SquintBoyAdvanceTheme {
                WearNavGraph()
            }
        }
    }
}
