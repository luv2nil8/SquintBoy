package com.anaglych.squintboyadvance.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.anaglych.squintboyadvance.presentation.navigation.WearNavGraph
import com.anaglych.squintboyadvance.presentation.theme.SquintBoyAdvanceTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        installDemoRom()

        setContent {
            SquintBoyAdvanceTheme {
                WearNavGraph()
            }
        }
    }

    private fun installDemoRom() {
        val prefs = getSharedPreferences("demo_rom", MODE_PRIVATE)
        if (prefs.getBoolean("installed", false)) return

        val romsDir = File(filesDir, "roms").apply { mkdirs() }
        val dest = File(romsDir, "Apotris.gba")
        if (!dest.exists()) {
            assets.open("demo/Apotris.gba").use { it.copyTo(dest.outputStream()) }
            RomMetadataStore.getInstance(this).update("Apotris.gba") {
                it.copy(displayName = "Apotris")
            }
        }
        prefs.edit().putBoolean("installed", true).apply()
    }
}
