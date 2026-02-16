package com.example.squintboyadvance.presentation.screens.emulator

import androidx.lifecycle.ViewModel
import com.example.squintboyadvance.shared.emulator.EmulatorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EmulatorViewModel : ViewModel() {

    private val _state = MutableStateFlow(EmulatorState.IDLE)
    val state: StateFlow<EmulatorState> = _state.asStateFlow()

    private val _romTitle = MutableStateFlow("")
    val romTitle: StateFlow<String> = _romTitle.asStateFlow()

    fun loadRom(romId: String, romTitle: String) {
        _romTitle.value = romTitle
        _state.value = EmulatorState.RUNNING
    }

    fun pause() {
        if (_state.value == EmulatorState.RUNNING) {
            _state.value = EmulatorState.PAUSED
        }
    }

    fun resume() {
        if (_state.value == EmulatorState.PAUSED) {
            _state.value = EmulatorState.RUNNING
        }
    }

    fun stop() {
        _state.value = EmulatorState.IDLE
    }
}
