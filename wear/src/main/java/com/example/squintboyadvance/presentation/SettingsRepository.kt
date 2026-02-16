package com.example.squintboyadvance.presentation

import android.content.Context
import android.content.SharedPreferences
import com.example.squintboyadvance.shared.model.EmulatorSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class SettingsRepository private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
        }

        private const val PREFS_NAME = "emulator_settings"
        private const val KEY_SETTINGS = "settings_json"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<EmulatorSettings> = _settings.asStateFlow()

    private fun load(): EmulatorSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return EmulatorSettings()
        return try {
            json.decodeFromString(EmulatorSettings.serializer(), raw)
        } catch (_: Exception) {
            EmulatorSettings()
        }
    }

    fun update(transform: (EmulatorSettings) -> EmulatorSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        prefs.edit()
            .putString(KEY_SETTINGS, json.encodeToString(EmulatorSettings.serializer(), updated))
            .apply()
    }
}
