package com.example.squintboyadvance.presentation.screens.settings

import androidx.lifecycle.ViewModel
import com.example.squintboyadvance.shared.model.EmulatorSettings
import com.example.squintboyadvance.shared.model.GbPalette
import com.example.squintboyadvance.shared.model.InputDevice
import com.example.squintboyadvance.shared.model.VideoScaling
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsViewModel : ViewModel() {

    private val _settings = MutableStateFlow(EmulatorSettings())
    val settings: StateFlow<EmulatorSettings> = _settings.asStateFlow()

    fun setAudioEnabled(enabled: Boolean) {
        _settings.update { it.copy(audioEnabled = enabled) }
    }

    fun setAudioVolume(volume: Float) {
        _settings.update { it.copy(audioVolume = volume) }
    }

    fun setVideoScaling(scaling: VideoScaling) {
        _settings.update { it.copy(videoScaling = scaling) }
    }

    fun setFrameskip(frameskip: Int) {
        _settings.update { it.copy(frameskip = frameskip) }
    }

    fun setShowFps(show: Boolean) {
        _settings.update { it.copy(showFps = show) }
    }

    fun setPreferredInput(input: InputDevice) {
        _settings.update { it.copy(preferredInput = input) }
    }

    fun setOverlayAlpha(alpha: Float) {
        _settings.update {
            it.copy(controllerLayout = it.controllerLayout.copy(overlayAlpha = alpha))
        }
    }

    fun setHapticFeedback(enabled: Boolean) {
        _settings.update {
            it.copy(controllerLayout = it.controllerLayout.copy(hapticFeedback = enabled))
        }
    }

    fun setColorPalette(palette: GbPalette) {
        _settings.update { it.copy(colorPalette = palette) }
    }

    fun setAutoSaveEnabled(enabled: Boolean) {
        _settings.update { it.copy(autoSaveEnabled = enabled) }
    }

    fun setAutoSaveInterval(seconds: Int) {
        _settings.update { it.copy(autoSaveIntervalSec = seconds) }
    }
}
