package com.anaglych.squintboyadvance.presentation.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.anaglych.squintboyadvance.presentation.SettingsRepository
import com.anaglych.squintboyadvance.shared.model.EmulatorSettings
import com.anaglych.squintboyadvance.shared.model.InputDevice
import com.anaglych.squintboyadvance.shared.model.ScaleMode
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

    fun setGbaScaleMode(mode: ScaleMode) {
        repo.update { it.copy(gbaScaleMode = mode) }
    }

    fun setGbaCustomScale(scale: Float) {
        repo.update { it.copy(gbaCustomScale = scale) }
    }

    fun setGbScaleMode(mode: ScaleMode) {
        repo.update { it.copy(gbScaleMode = mode) }
    }

    fun setGbCustomScale(scale: Float) {
        repo.update { it.copy(gbCustomScale = scale) }
    }

    fun setGbaFilter(enabled: Boolean) {
        repo.update { it.copy(gbaFilterEnabled = enabled) }
    }

    fun setGbFilter(enabled: Boolean) {
        repo.update { it.copy(gbFilterEnabled = enabled) }
    }

    fun setGbaFrameskip(frameskip: Int) {
        repo.update { it.copy(gbaFrameskip = frameskip) }
    }

    fun setGbFrameskip(frameskip: Int) {
        repo.update { it.copy(gbFrameskip = frameskip) }
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

    fun setGbPaletteIndex(index: Int) {
        repo.update { it.copy(gbPaletteIndex = index) }
    }

}
