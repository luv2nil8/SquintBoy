package com.anaglych.squintboyadvance.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.anaglych.squintboyadvance.shared.model.SaveSyncConfig
import com.anaglych.squintboyadvance.shared.protocol.WearMessageConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.google.android.gms.wearable.Wearable

@Serializable
data class SaveSyncSettings(
    val enabled: Boolean = false,
    /** SAF tree URI of the user-chosen archive folder (persisted permission). */
    val treeUri: String? = null,
    /** Rolling retention: null = keep forever. */
    val maxAgeDays: Int? = null,
    val maxCountPerGame: Int? = 50,
    /** After this many days, keep at most one save per day. null = no thinning. */
    val thinAfterDays: Int? = 7,
    val driveEnabled: Boolean = false,
    val driveAccountEmail: String? = null,
)

/**
 * Phone-side source of truth for the save-sync feature. The enabled flag is
 * mirrored to the watch (which defaults to off and stays entitlement-unaware).
 */
class SaveSyncSettingsRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SaveSyncSettings"
        private const val PREFS_NAME = "save_sync_settings"
        private const val KEY_SETTINGS = "settings_json"

        @Volatile
        private var instance: SaveSyncSettingsRepository? = null

        fun getInstance(context: Context): SaveSyncSettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SaveSyncSettingsRepository(context.applicationContext)
                    .also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<SaveSyncSettings> = _settings.asStateFlow()

    /** Set when the archive folder can't be resolved; drives the "relink" banner. */
    val folderMissing = MutableStateFlow(false)

    private fun load(): SaveSyncSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return SaveSyncSettings()
        return try {
            json.decodeFromString(SaveSyncSettings.serializer(), raw)
        } catch (_: Exception) {
            SaveSyncSettings()
        }
    }

    fun update(transform: (SaveSyncSettings) -> SaveSyncSettings) {
        val old = _settings.value
        val updated = transform(old)
        _settings.value = updated
        prefs.edit()
            .putString(KEY_SETTINGS, json.encodeToString(SaveSyncSettings.serializer(), updated))
            .apply()
        if (old.enabled != updated.enabled) pushConfigToWatch()
    }

    /**
     * Asks the watch to drain its outbox now. Backup mechanism — the watch
     * pushes on its own when it can; this heals any missed window (e.g. the
     * phone app was dead while the watch tried).
     */
    fun requestWatchDrain() {
        if (!_settings.value.enabled) return
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                for (node in nodes) {
                    Wearable.getMessageClient(context).sendMessage(
                        node.id, WearMessageConstants.PATH_SAVE_ARCHIVE_DRAIN, byteArrayOf(),
                    ).await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Drain request failed: ${e.message}")
            }
        }
    }

    /** Pushes the current enabled state to all connected watches. */
    fun pushConfigToWatch() {
        scope.launch {
            try {
                val config = SaveSyncConfig(enabled = _settings.value.enabled)
                val payload = json.encodeToString(SaveSyncConfig.serializer(), config)
                    .toByteArray(Charsets.UTF_8)
                val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                for (node in nodes) {
                    Wearable.getMessageClient(context).sendMessage(
                        node.id, WearMessageConstants.PATH_SAVE_SYNC_CONFIG, payload,
                    ).await()
                }
                Log.i(TAG, "Pushed save-sync config (enabled=${config.enabled}) to ${nodes.size} node(s)")
            } catch (e: Exception) {
                Log.w(TAG, "Config push failed: ${e.message}")
            }
        }
    }
}
