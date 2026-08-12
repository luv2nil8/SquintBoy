package com.anaglych.squintboyadvance.presentation.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.anaglych.squintboyadvance.shared.model.SaveSyncConfig
import com.anaglych.squintboyadvance.shared.protocol.WearMessageConstants
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Watch-side save-sync configuration. The phone is authoritative: it pushes
 * SaveSyncConfig on PATH_SAVE_SYNC_CONFIG whenever the user toggles the feature,
 * and replies to PATH_SAVE_SYNC_CONFIG_REQUEST on startup. Default is disabled,
 * so a watch that never hears from the phone behaves exactly as before.
 */
class SaveSyncConfigRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SaveSyncConfigRepo"
        private const val PREFS_NAME = "save_sync_config"
        private const val KEY_ENABLED = "enabled"
        private const val MAX_REQUEST_RETRIES = 3
        private const val RETRY_BACKOFF_MS = 2_000L

        @Volatile
        private var instance: SaveSyncConfigRepository? = null

        fun getInstance(context: Context): SaveSyncConfigRepository {
            return instance ?: synchronized(this) {
                instance ?: SaveSyncConfigRepository(context.applicationContext)
                    .also { it.requestConfigFromPhone() }
                    .also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun handleConfigPush(config: SaveSyncConfig) {
        val wasEnabled = _enabled.value
        _enabled.value = config.enabled
        prefs.edit().putBoolean(KEY_ENABLED, config.enabled).apply()
        if (wasEnabled && !config.enabled) {
            // Disabled: the system goes inert. Drop queued snapshots — the live
            // .sav on the watch is untouched, and re-enabling re-archives on the
            // next change (per-ROM hashes are kept so unchanged SRAM stays deduped).
            SaveSyncOutbox.getInstance(context).clear()
        }
        Log.i(TAG, "Save sync config: enabled=${config.enabled}")
    }

    /** Best-effort refresh from the phone; the persisted value stands until a reply lands. */
    private fun requestConfigFromPhone() {
        scope.launch {
            repeat(MAX_REQUEST_RETRIES) { attempt ->
                try {
                    val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                    if (nodes.isNotEmpty()) {
                        for (node in nodes) {
                            Wearable.getMessageClient(context).sendMessage(
                                node.id,
                                WearMessageConstants.PATH_SAVE_SYNC_CONFIG_REQUEST,
                                byteArrayOf(),
                            ).await()
                        }
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Config request attempt ${attempt + 1} failed: ${e.message}")
                }
                delay(RETRY_BACKOFF_MS * (attempt + 1))
            }
        }
    }
}
