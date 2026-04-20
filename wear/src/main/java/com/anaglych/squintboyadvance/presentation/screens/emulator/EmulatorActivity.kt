package com.anaglych.squintboyadvance.presentation.screens.emulator

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import com.anaglych.squintboyadvance.shared.model.ButtonId
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

    private var prevHatX = 0f
    private var prevHatY = 0f

    private fun isGamepadSource(event: KeyEvent) =
        event.source and InputDevice.SOURCE_GAMEPAD != 0 ||
        event.source and InputDevice.SOURCE_JOYSTICK != 0

    // Intercept at the earliest point so the OS never acts on gamepad buttons.
    // PS Circle → KEYCODE_BACK, Cross → KEYCODE_BUTTON_A, etc. would otherwise
    // trigger system back/select before reaching onKeyDown.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isGamepadSource(event)) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> viewModel.handleGamepadKeyDown(event.keyCode)
                KeyEvent.ACTION_UP   -> viewModel.handleGamepadKeyUp(event.keyCode)
            }
            return true  // always consume — never let the OS touch gamepad events
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP   -> { viewModel.adjustVolume(0.1f); true }
            KeyEvent.KEYCODE_VOLUME_DOWN -> { viewModel.adjustVolume(-0.1f); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == 0 &&
            event.source and InputDevice.SOURCE_GAMEPAD == 0) return super.onGenericMotionEvent(event)

        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        if (hatX != prevHatX) {
            if (prevHatX < -0.5f) viewModel.handleGamepadKeyUp(KeyEvent.KEYCODE_DPAD_LEFT)
            if (prevHatX > 0.5f)  viewModel.handleGamepadKeyUp(KeyEvent.KEYCODE_DPAD_RIGHT)
            if (hatX < -0.5f)      viewModel.handleGamepadKeyDown(KeyEvent.KEYCODE_DPAD_LEFT)
            else if (hatX > 0.5f)  viewModel.handleGamepadKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT)
            prevHatX = hatX
        }
        if (hatY != prevHatY) {
            if (prevHatY < -0.5f) viewModel.handleGamepadKeyUp(KeyEvent.KEYCODE_DPAD_UP)
            if (prevHatY > 0.5f)  viewModel.handleGamepadKeyUp(KeyEvent.KEYCODE_DPAD_DOWN)
            if (hatY < -0.5f)      viewModel.handleGamepadKeyDown(KeyEvent.KEYCODE_DPAD_UP)
            else if (hatY > 0.5f)  viewModel.handleGamepadKeyDown(KeyEvent.KEYCODE_DPAD_DOWN)
            prevHatY = hatY
        }
        return true
    }

    private var wasRunningOnPause = false

    override fun onPause() {
        super.onPause()
        wasRunningOnPause = viewModel.state.value == EmulatorState.RUNNING
        viewModel.pause()
    }

    override fun onResume() {
        super.onResume()
        if (wasRunningOnPause) viewModel.resume()
    }

    override fun onDestroy() {
        viewModel.stop()
        super.onDestroy()
    }
}
