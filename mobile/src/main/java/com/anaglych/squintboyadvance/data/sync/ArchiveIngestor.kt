package com.anaglych.squintboyadvance.data.sync

import android.content.Context
import android.util.Log
import com.anaglych.squintboyadvance.data.archive.GameArchiveManifest
import com.anaglych.squintboyadvance.data.archive.GameDirNaming
import com.anaglych.squintboyadvance.data.archive.ManifestEntry
import com.anaglych.squintboyadvance.data.archive.SaveArchiveStore
import com.anaglych.squintboyadvance.data.db.ArchivedSaveEntity
import com.anaglych.squintboyadvance.data.db.DriveState
import com.anaglych.squintboyadvance.data.db.SaveSyncDatabase
import com.anaglych.squintboyadvance.shared.model.SaveArchiveEntryMeta
import com.anaglych.squintboyadvance.shared.model.SystemType
import com.anaglych.squintboyadvance.shared.util.HashUtils
import com.anaglych.squintboyadvance.shared.util.SaveSizeCheck
import com.anaglych.squintboyadvance.shared.util.SaveValidation

sealed class IngestAck(val wire: String) {
    data object Ok : IngestAck("OK")
    data class Reject(val reason: String) : IngestAck("REJECT $reason")
    data class Retry(val reason: String) : IngestAck("RETRY $reason")
}

/**
 * Ingests one save snapshot from the watch. THE CONTRACT: the watch deletes its
 * outbox copy when it hears OK, so the durable SAF write (verified length) must
 * strictly precede the OK ack. Anything transient is RETRY (watch keeps the
 * entry); only definitively-bad payloads are REJECT.
 */
class ArchiveIngestor(private val context: Context) {

    companion object {
        private const val TAG = "ArchiveIngestor"
    }

    private val store = SaveArchiveStore(context)
    private val dao = SaveSyncDatabase.getInstance(context).archivedSaveDao()
    private val settingsRepo = SaveSyncSettingsRepository.getInstance(context)

    suspend fun ingest(meta: SaveArchiveEntryMeta, bytes: ByteArray): IngestAck {
        // Transfer integrity: recompute; a mismatch is corruption in transit.
        if (HashUtils.sha256Hex(bytes) != meta.sha256) return IngestAck.Retry("hash_mismatch")

        when (SaveValidation.check(bytes.size.toLong(), meta.romId)) {
            SaveSizeCheck.EMPTY -> return IngestAck.Reject("empty")
            SaveSizeCheck.SIZE_MISMATCH ->
                // Odd-but-real sizes exist (homebrew); warn, never discard user data.
                Log.w(TAG, "Nonstandard save size ${bytes.size} for ${meta.romId}")
            SaveSizeCheck.VALID -> {}
        }

        // Idempotent replay after a lost ack: already archived → just re-ack.
        if (dao.findDuplicate(meta.romId, meta.sha256, meta.timestampMs) != null) {
            return IngestAck.Ok
        }

        if (!store.isAvailable()) return IngestAck.Retry("folder_unavailable")
        val gameDirName = GameDirNaming.gameDirName(meta.romId)
        val gameDir = store.ensureGameDir(gameDirName)
            ?: return IngestAck.Retry("folder_unavailable")

        val desiredName = GameDirNaming.saveFileName(meta.timestampMs, meta.sha256)
        val actualName = store.writeSave(gameDir, desiredName, bytes)
            ?: return IngestAck.Retry("write_failed")

        val driveEnabled = settingsRepo.settings.value.driveEnabled
        dao.insert(
            ArchivedSaveEntity(
                romId = meta.romId,
                gameDirName = gameDirName,
                fileName = actualName,
                timestampMs = meta.timestampMs,
                dayKey = GameDirNaming.dayKey(meta.timestampMs),
                sizeBytes = bytes.size.toLong(),
                sha256 = meta.sha256,
                driveState = if (driveEnabled) DriveState.PENDING else DriveState.NOT_SYNCED,
            )
        )

        val ext = meta.romId.substringAfterLast('.', "").lowercase()
        store.updateManifest(
            gameDir,
            create = {
                GameArchiveManifest(
                    romId = meta.romId,
                    baseName = meta.romId.substringBeforeLast('.'),
                    systemType = (SystemType.fromExtension(ext) ?: SystemType.GB).name,
                )
            },
            transform = { manifest ->
                manifest.copy(
                    entries = manifest.entries.filter { it.fileName != actualName } +
                        ManifestEntry(
                            fileName = actualName,
                            timestampMs = meta.timestampMs,
                            sizeBytes = bytes.size.toLong(),
                            sha256 = meta.sha256,
                        )
                )
            },
        )

        Log.i(TAG, "Archived ${meta.romId} → $gameDirName/$actualName")
        // Post-ack work (Drive upload, retention) is kicked off by the caller so
        // the ack is never delayed by it.
        return IngestAck.Ok
    }
}
