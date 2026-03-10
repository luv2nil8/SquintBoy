package com.anaglych.squintboyadvance.presentation.screens.emulator

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.anaglych.squintboyadvance.presentation.theme.SquintBoyAdvanceTheme
import com.anaglych.squintboyadvance.shared.emulator.EmulatorState
import kotlinx.coroutines.launch

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

        // Drop wake lock while paused so the screen can time out
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    if (state == EmulatorState.RUNNING) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }

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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> { viewModel.adjustVolume(0.1f); true }
            KeyEvent.KEYCODE_VOLUME_DOWN -> { viewModel.adjustVolume(-0.1f); true }
            else -> super.onKeyDown(keyCode, event)
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
