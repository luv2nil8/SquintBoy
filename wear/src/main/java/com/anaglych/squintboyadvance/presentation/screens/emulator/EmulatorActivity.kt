package com.anaglych.squintboyadvance.presentation.screens.emulator

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import com.anaglych.squintboyadvance.shared.model.ButtonId
import com.anaglych.squintboyadvance.shared.model.GamepadMapping
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

    private var prevHatX  = 0f
    private var prevHatY  = 0f
    private var prevAxisX = 0f
    private var prevAxisY = 0f
    // Separate recording latches for HAT and AXIS so each clears independently.
    private var hatRecordingLocked  = false
    private var axisRecordingLocked = false
    // Which synthetic keycodes were pressed when the latch engaged — used to emit keyUp on release.
    private var lockedHatKeys:  Set<Int> = emptySet()
    private var lockedAxisKeys: Set<Int> = emptySet()

    private fun isGamepadSource(event: KeyEvent) =
        event.source and InputDevice.SOURCE_GAMEPAD != 0 ||
        event.source and InputDevice.SOURCE_JOYSTICK != 0 ||
        event.source and InputDevice.SOURCE_DPAD != 0

    // Intercept at the earliest point so the OS never acts on gamepad buttons.
    // PS Circle → KEYCODE_BACK, Cross → KEYCODE_BUTTON_A, etc. would otherwise
    // trigger system back/select before reaching onKeyDown.
    // During recording we accept any key from any source so exotic controllers
    // (microcontrollers, SOURCE_DPAD-only devices) can still be mapped.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isRecording = viewModel.isRecording()
        if (isGamepadSource(event) || isRecording) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> viewModel.handleGamepadKeyDown(event.keyCode)
                KeyEvent.ACTION_UP   -> viewModel.handleGamepadKeyUp(event.keyCode)
            }
            return true
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
        if (!viewModel.isRecording() &&
            event.source and InputDevice.SOURCE_JOYSTICK == 0 &&
            event.source and InputDevice.SOURCE_GAMEPAD == 0 &&
            event.source and InputDevice.SOURCE_DPAD == 0) return super.onGenericMotionEvent(event)

        val recording = viewModel.isRecording()
        if (!recording) { hatRecordingLocked = false; axisRecordingLocked = false }

        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        // ── HAT D-pad → KEYCODE_DPAD_* (play + recording) ───────────────────
        if (recording && hatRecordingLocked) {
            if (kotlin.math.abs(hatX) < 0.3f && kotlin.math.abs(hatY) < 0.3f) {
                hatRecordingLocked = false
                lockedHatKeys.forEach { viewModel.handleGamepadKeyUp(it) }
                lockedHatKeys = emptySet()
                prevHatX = hatX; prevHatY = hatY
            }
        } else {
            if (hatX != prevHatX) {
                if (prevHatX < -0.5f) viewModel.handleGamepadKeyUp(KeyEvent.KEYCODE_DPAD_LEFT)
                if (prevHatX >  0.5f) viewModel.handleGamepadKeyUp(KeyEvent.KEYCODE_DPAD_RIGHT)
                if (hatX < -0.5f) {
                    viewModel.handleGamepadKeyDown(KeyEvent.KEYCODE_DPAD_LEFT)
                    if (recording) { hatRecordingLocked = true; lockedHatKeys = lockedHatKeys + KeyEvent.KEYCODE_DPAD_LEFT }
                } else if (hatX > 0.5f) {
                    viewModel.handleGamepadKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT)
                    if (recording) { hatRecordingLocked = true; lockedHatKeys = lockedHatKeys + KeyEvent.KEYCODE_DPAD_RIGHT }
                }
                prevHatX = hatX
            }
            if (hatY != prevHatY) {
                if (prevHatY < -0.5f) viewModel.handleGamepadKeyUp(KeyEvent.KEYCODE_DPAD_UP)
                if (prevHatY >  0.5f) viewModel.handleGamepadKeyUp(KeyEvent.KEYCODE_DPAD_DOWN)
                if (hatY < -0.5f) {
                    viewModel.handleGamepadKeyDown(KeyEvent.KEYCODE_DPAD_UP)
                    if (recording) { hatRecordingLocked = true; lockedHatKeys = lockedHatKeys + KeyEvent.KEYCODE_DPAD_UP }
                } else if (hatY > 0.5f) {
                    viewModel.handleGamepadKeyDown(KeyEvent.KEYCODE_DPAD_DOWN)
                    if (recording) { hatRecordingLocked = true; lockedHatKeys = lockedHatKeys + KeyEvent.KEYCODE_DPAD_DOWN }
                }
                prevHatY = hatY
            }
        }

        // ── Analog stick AXIS_X/Y ────────────────────────────────────────────
        // During recording: latch prevents re-triggering until stick returns to
        // neutral, then emits key-up so the combo commits cleanly.
        // During play: routes through handleAxisKeyDown/Up which bypasses
        // _heldPhysicalKeys so stick events never poison the button combo matcher.
        val axisX = event.getAxisValue(MotionEvent.AXIS_X)
        val axisY = event.getAxisValue(MotionEvent.AXIS_Y)
        if (recording && axisRecordingLocked) {
            if (kotlin.math.abs(axisX) < 0.3f && kotlin.math.abs(axisY) < 0.3f) {
                axisRecordingLocked = false
                lockedAxisKeys.forEach { viewModel.handleAxisKeyUp(it) }
                lockedAxisKeys = emptySet()
                prevAxisX = axisX; prevAxisY = axisY
            }
        } else {
            if (axisX != prevAxisX) {
                if (prevAxisX < -0.5f) viewModel.handleAxisKeyUp(GamepadMapping.AXIS_LEFT)
                if (prevAxisX >  0.5f) viewModel.handleAxisKeyUp(GamepadMapping.AXIS_RIGHT)
                if (axisX < -0.5f) {
                    viewModel.handleAxisKeyDown(GamepadMapping.AXIS_LEFT)
                    if (recording) { axisRecordingLocked = true; lockedAxisKeys = lockedAxisKeys + GamepadMapping.AXIS_LEFT }
                } else if (axisX > 0.5f) {
                    viewModel.handleAxisKeyDown(GamepadMapping.AXIS_RIGHT)
                    if (recording) { axisRecordingLocked = true; lockedAxisKeys = lockedAxisKeys + GamepadMapping.AXIS_RIGHT }
                }
                prevAxisX = axisX
            }
            if (axisY != prevAxisY) {
                if (prevAxisY < -0.5f) viewModel.handleAxisKeyUp(GamepadMapping.AXIS_UP)
                if (prevAxisY >  0.5f) viewModel.handleAxisKeyUp(GamepadMapping.AXIS_DOWN)
                if (axisY < -0.5f) {
                    viewModel.handleAxisKeyDown(GamepadMapping.AXIS_UP)
                    if (recording) { axisRecordingLocked = true; lockedAxisKeys = lockedAxisKeys + GamepadMapping.AXIS_UP }
                } else if (axisY > 0.5f) {
                    viewModel.handleAxisKeyDown(GamepadMapping.AXIS_DOWN)
                    if (recording) { axisRecordingLocked = true; lockedAxisKeys = lockedAxisKeys + GamepadMapping.AXIS_DOWN }
                }
                prevAxisY = axisY
            }
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
