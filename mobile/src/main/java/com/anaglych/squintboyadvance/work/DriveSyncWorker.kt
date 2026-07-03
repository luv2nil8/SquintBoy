package com.anaglych.squintboyadvance.work

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anaglych.squintboyadvance.data.archive.SaveArchiveStore
import com.anaglych.squintboyadvance.data.cloud.CloudResult
import com.anaglych.squintboyadvance.data.cloud.DriveAuthManager
import com.anaglych.squintboyadvance.data.cloud.GoogleDriveProvider
import com.anaglych.squintboyadvance.data.db.ArchivedSaveEntity
import com.anaglych.squintboyadvance.data.db.DriveState
import com.anaglych.squintboyadvance.data.db.SaveSyncDatabase
import com.anaglych.squintboyadvance.data.sync.SaveSyncSettingsRepository
import java.util.concurrent.TimeUnit

/**
 * Single queue-drain worker for all Drive work: uploads PENDING rows, deletes
 * DELETE_PENDING tombstones. Unique-KEEP means concurrent kicks coalesce into
 * one run; anything transient left over triggers a backoff retry.
 */
class DriveSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DriveSyncWorker"
        private const val WORK_NAME = "drive_sync"
        private const val MAX_ATTEMPTS = 7

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<DriveSyncWorker>()
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build(),
            )
        }
    }

    override suspend fun doWork(): Result {
        val settingsRepo = SaveSyncSettingsRepository.getInstance(applicationContext)
        if (!settingsRepo.settings.value.driveEnabled) return Result.success()

        val dao = SaveSyncDatabase.getInstance(applicationContext).archivedSaveDao()
        val pending = dao.pendingDriveWork()
        if (pending.isEmpty()) return Result.success()

        val auth = DriveAuthManager(applicationContext)
        if (auth.getAccessTokenSilent() == null) {
            // Consent revoked or account gone: surface via row state + banner,
            // never retry-loop and never launch UI from a Worker.
            Log.w(TAG, "Silent Drive auth failed; marking ${pending.size} row(s) AUTH")
            for (row in pending) {
                dao.update(row.copy(driveState = DriveState.ERROR, driveError = "AUTH"))
            }
            return Result.failure()
        }
        val provider = GoogleDriveProvider(applicationContext, auth::getAccessTokenSilent)
        val store = SaveArchiveStore(applicationContext)

        var transientFailures = 0
        for (row in pending) {
            when (row.driveState) {
                DriveState.DELETE_PENDING -> when (val result = provider.delete(row.driveFileId ?: "")) {
                    is CloudResult.Ok -> dao.deleteById(row.id) // tombstone fully resolved
                    is CloudResult.Retryable -> transientFailures++
                    is CloudResult.NotConnected -> transientFailures++
                    is CloudResult.QuotaExceeded -> transientFailures++ // deletes free quota; retry
                    is CloudResult.Permanent -> {
                        Log.w(TAG, "Drive delete failed permanently: ${result.message}")
                        dao.deleteById(row.id) // don't strand the tombstone forever
                    }
                }
                DriveState.PENDING -> when (val outcome = uploadRow(provider, store, dao, row)) {
                    UploadOutcome.OK -> {}
                    UploadOutcome.TRANSIENT -> transientFailures++
                    UploadOutcome.QUOTA -> {
                        // Everything after this would fail the same way; stop and banner.
                        markRemainingQuota(dao, pending, row)
                        return Result.success()
                    }
                }
                else -> {}
            }
        }

        return when {
            transientFailures == 0 -> Result.success()
            runAttemptCount >= MAX_ATTEMPTS -> {
                Log.w(TAG, "Giving up after $runAttemptCount attempts; rows stay PENDING")
                Result.failure() // rows remain PENDING; next kick starts a fresh chain
            }
            else -> Result.retry()
        }
    }

    private enum class UploadOutcome { OK, TRANSIENT, QUOTA }

    private suspend fun uploadRow(
        provider: GoogleDriveProvider,
        store: SaveArchiveStore,
        dao: com.anaglych.squintboyadvance.data.db.ArchivedSaveDao,
        row: ArchivedSaveEntity,
    ): UploadOutcome {
        if (row.driveFileId != null) { // crashed after upload, before state flip
            dao.update(row.copy(driveState = DriveState.UPLOADED, driveError = null))
            return UploadOutcome.OK
        }
        val bytes = store.openSave(row.gameDirName, row.fileName)?.use { it.readBytes() }
        if (bytes == null) {
            // Local file vanished (user shuffled the folder): nothing to upload.
            dao.update(row.copy(driveState = DriveState.ERROR, driveError = "FILE_MISSING"))
            return UploadOutcome.OK
        }

        val dirId = when (val dir = provider.ensureGameDir(row.gameDirName)) {
            is CloudResult.Ok -> dir.value
            is CloudResult.QuotaExceeded -> return UploadOutcome.QUOTA
            is CloudResult.Retryable, CloudResult.NotConnected -> return UploadOutcome.TRANSIENT
            is CloudResult.Permanent -> {
                dao.update(row.copy(driveState = DriveState.ERROR, driveError = dir.message))
                return UploadOutcome.OK
            }
        }

        return when (val result =
            provider.upload(dirId, row.fileName, bytes, row.sha256, row.romId)) {
            is CloudResult.Ok -> {
                dao.update(
                    row.copy(
                        driveState = DriveState.UPLOADED,
                        driveFileId = result.value,
                        driveError = null,
                    )
                )
                updateManifestDriveId(store, row, result.value)
                UploadOutcome.OK
            }
            is CloudResult.QuotaExceeded -> UploadOutcome.QUOTA
            is CloudResult.Retryable, CloudResult.NotConnected -> UploadOutcome.TRANSIENT
            is CloudResult.Permanent -> {
                dao.update(row.copy(driveState = DriveState.ERROR, driveError = result.message))
                UploadOutcome.OK
            }
        }
    }

    private suspend fun markRemainingQuota(
        dao: com.anaglych.squintboyadvance.data.db.ArchivedSaveDao,
        pending: List<ArchivedSaveEntity>,
        from: ArchivedSaveEntity,
    ) {
        for (row in pending.dropWhile { it.id != from.id }) {
            if (row.driveState == DriveState.PENDING) {
                dao.update(row.copy(driveState = DriveState.ERROR, driveError = "QUOTA"))
            }
        }
    }

    /** Persist the Drive id into the folder manifest so rescan can restore it. */
    private fun updateManifestDriveId(
        store: SaveArchiveStore,
        row: ArchivedSaveEntity,
        driveFileId: String,
    ) {
        val gameDir = store.findGameDir(row.gameDirName) ?: return
        val manifest = store.readManifest(gameDir) ?: return
        store.writeManifest(
            gameDir,
            manifest.copy(
                entries = manifest.entries.map {
                    if (it.fileName == row.fileName) it.copy(driveFileId = driveFileId) else it
                }
            ),
        )
    }
}
