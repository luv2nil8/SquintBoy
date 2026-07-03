package com.anaglych.squintboyadvance.data.retention

import android.content.Context
import android.util.Log
import com.anaglych.squintboyadvance.data.archive.SaveArchiveStore
import com.anaglych.squintboyadvance.data.db.ArchivedSaveEntity
import com.anaglych.squintboyadvance.data.db.DriveState
import com.anaglych.squintboyadvance.data.db.LocalState
import com.anaglych.squintboyadvance.data.db.SaveSyncDatabase
import com.anaglych.squintboyadvance.data.sync.SaveSyncSettingsRepository

/**
 * Applies the retention policy: local file + manifest entry are removed
 * immediately; rows already on Drive become tombstones (localState=DELETED,
 * driveState=DELETE_PENDING) that the Drive worker propagates. Local-first with
 * a tombstone means a crash can never resurrect a deleted save.
 */
object RetentionRunner {

    private const val TAG = "RetentionRunner"

    suspend fun runFullPass(context: Context) {
        val settingsRepo = SaveSyncSettingsRepository.getInstance(context)
        val settings = settingsRepo.settings.value
        if (!settings.enabled) return

        val policy = RetentionPolicy(
            maxAgeDays = settings.maxAgeDays,
            maxCountPerGame = settings.maxCountPerGame,
            thinAfterDays = settings.thinAfterDays,
        )
        val dao = SaveSyncDatabase.getInstance(context).archivedSaveDao()
        val deletions = RetentionEngine.selectDeletions(
            dao.allPresent(), policy, System.currentTimeMillis()
        )
        if (deletions.isEmpty()) return

        val store = SaveArchiveStore(context)
        if (!store.isAvailable()) return // never tombstone rows we couldn't delete locally

        for ((gameDirName, group) in deletions.groupBy { it.gameDirName }) {
            deleteFromGameDir(store, dao, gameDirName, group)
        }
        Log.i(TAG, "Retention removed ${deletions.size} save(s)")

        // Propagate tombstoned deletions to Drive.
        if (deletions.any { it.driveFileId != null }) {
            com.anaglych.squintboyadvance.work.DriveSyncWorker.enqueue(context)
        }
    }

    private suspend fun deleteFromGameDir(
        store: SaveArchiveStore,
        dao: com.anaglych.squintboyadvance.data.db.ArchivedSaveDao,
        gameDirName: String,
        group: List<ArchivedSaveEntity>,
    ) {
        val gameDir = store.findGameDir(gameDirName)
        val deletedNames = mutableSetOf<String>()

        for (save in group) {
            if (store.deleteSave(gameDirName, save.fileName)) {
                deletedNames.add(save.fileName)
                if (save.driveFileId == null) {
                    dao.deleteById(save.id)
                } else {
                    dao.update(
                        save.copy(
                            localState = LocalState.DELETED,
                            driveState = DriveState.DELETE_PENDING,
                        )
                    )
                }
            }
        }

        // One manifest rewrite per game dir, not per save.
        if (gameDir != null && deletedNames.isNotEmpty()) {
            store.readManifest(gameDir)?.let { manifest ->
                store.writeManifest(
                    gameDir,
                    manifest.copy(entries = manifest.entries.filter { it.fileName !in deletedNames }),
                )
            }
        }
    }
}
