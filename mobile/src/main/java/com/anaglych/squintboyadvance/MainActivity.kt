package com.anaglych.squintboyadvance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.anaglych.squintboyadvance.ui.CompanionApp
import com.anaglych.squintboyadvance.ui.theme.SquintBoyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileEntitlementCache.init(this)
        enableEdgeToEdge()
        setContent {
            SquintBoyTheme {
                CompanionApp()
            }
        }
    }
}
