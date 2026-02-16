package com.example.squintboyadvance.presentation.screens.emulator

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.squintboyadvance.presentation.theme.SquintBoyAdvanceTheme

class EmulatorActivity : ComponentActivity() {

    private val viewModel: EmulatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen immersive mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val romId = intent.getStringExtra("rom_id") ?: run { finish(); return }
        val romTitle = intent.getStringExtra("rom_title") ?: "Unknown"

        setContent {
            SquintBoyAdvanceTheme {
                EmulatorScreen(
                    romId = romId,
                    romTitle = romTitle,
                    onExit = { finish() },
                    viewModel = viewModel
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.pause()
    }

    override fun onResume() {
        super.onResume()
        viewModel.resume()
    }

    override fun onDestroy() {
        viewModel.stop()
        super.onDestroy()
    }
}
