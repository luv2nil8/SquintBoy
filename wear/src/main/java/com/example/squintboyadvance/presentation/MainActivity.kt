package com.example.squintboyadvance.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.squintboyadvance.presentation.navigation.WearNavGraph
import com.example.squintboyadvance.presentation.theme.SquintBoyAdvanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            SquintBoyAdvanceTheme {
                WearNavGraph()
            }
        }
    }
}
