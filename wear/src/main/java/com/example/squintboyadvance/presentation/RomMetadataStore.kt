package com.example.squintboyadvance.presentation

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Persists per-ROM metadata (play time, last played, screenshot path).
 * Keyed by ROM file name (the romId).
 */
class RomMetadataStore private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: RomMetadataStore? = null

        fun getInstance(context: Context): RomMetadataStore {
            return instance ?: synchronized(this) {
                instance ?: RomMetadataStore(context.applicationContext).also { instance = it }
            }
        }

        private const val PREFS_NAME = "rom_metadata"
    }

    @Serializable
    data class PersistedRomMeta(
        val lastPlayed: Long? = null,
        val totalPlayTimeMs: Long = 0L,
        val thumbnailPath: String? = null
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    fun getAll(): Map<String, PersistedRomMeta> {
        return prefs.all.mapNotNull { (key, value) ->
            val raw = value as? String ?: return@mapNotNull null
            try {
                key to json.decodeFromString(PersistedRomMeta.serializer(), raw)
            } catch (_: Exception) {
                null
            }
        }.toMap()
    }

    fun get(romId: String): PersistedRomMeta {
        val raw = prefs.getString(romId, null) ?: return PersistedRomMeta()
        return try {
            json.decodeFromString(PersistedRomMeta.serializer(), raw)
        } catch (_: Exception) {
            PersistedRomMeta()
        }
    }

    fun remove(romId: String) {
        prefs.edit().remove(romId).apply()
    }

    fun update(romId: String, transform: (PersistedRomMeta) -> PersistedRomMeta) {
        val current = get(romId)
        val updated = transform(current)
        prefs.edit()
            .putString(romId, json.encodeToString(PersistedRomMeta.serializer(), updated))
            .apply()
    }
}
