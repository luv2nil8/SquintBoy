package com.anaglych.squintboyadvance.presentation.sync

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists the last archived SRAM hash per ROM so relaunching an unchanged game
 * archives nothing across process restarts.
 */
class ArchiveHashStore private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: ArchiveHashStore? = null

        fun getInstance(context: Context): ArchiveHashStore {
            return instance ?: synchronized(this) {
                instance ?: ArchiveHashStore(context.applicationContext).also { instance = it }
            }
        }

        private const val PREFS_NAME = "save_sync_hashes"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(romId: String): String? = prefs.getString(romId, null)

    fun put(romId: String, hash: String) {
        prefs.edit().putString(romId, hash).apply()
    }
}
