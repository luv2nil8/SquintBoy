package com.example.squintboyadvance.presentation.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.squintboyadvance.presentation.SettingsRepository
import com.example.squintboyadvance.shared.model.EmulatorSettings
import com.example.squintboyadvance.shared.model.GbPalette
import com.example.squintboyadvance.shared.model.InputDevice
import com.example.squintboyadvance.shared.model.VideoScaling
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SettingsRepository.getInstance(application)

    val settings: StateFlow<EmulatorSettings> = repo.settings

    fun setAudioEnabled(enabled: Boolean) {
        repo.update { it.copy(audioEnabled = enabled) }
    }

    fun setAudioVolume(volume: Float) {
        repo.update { it.copy(audioVolume = volume) }
    }

    fun setVideoScaling(scaling: VideoScaling) {
        repo.update { it.copy(videoScaling = scaling) }
    }

    fun setFrameskip(frameskip: Int) {
        repo.update { it.copy(frameskip = frameskip) }
    }

    fun setShowFps(show: Boolean) {
        repo.update { it.copy(showFps = show) }
    }

    fun setPreferredInput(input: InputDevice) {
        repo.update { it.copy(preferredInput = input) }
    }

    fun setOverlayAlpha(alpha: Float) {
        repo.update {
            it.copy(controllerLayout = it.controllerLayout.copy(overlayAlpha = alpha))
        }
    }

    fun setHapticFeedback(enabled: Boolean) {
        repo.update {
            it.copy(controllerLayout = it.controllerLayout.copy(hapticFeedback = enabled))
        }
    }

    fun setColorPalette(palette: GbPalette) {
        repo.update { it.copy(colorPalette = palette) }
    }

    fun setAutoSaveEnabled(enabled: Boolean) {
        repo.update { it.copy(autoSaveEnabled = enabled) }
    }

    fun setAutoSaveInterval(seconds: Int) {
        repo.update { it.copy(autoSaveIntervalSec = seconds) }
    }
}
