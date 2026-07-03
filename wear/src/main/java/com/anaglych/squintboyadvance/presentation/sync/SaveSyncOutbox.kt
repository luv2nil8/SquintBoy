package com.anaglych.squintboyadvance.presentation.sync

import android.content.Context
import android.util.Log
import com.anaglych.squintboyadvance.shared.model.SaveArchiveEntryMeta
import java.io.File
import kotlinx.serialization.json.Json

data class OutboxEntry(
    val metaFile: File,
    val blobFile: File,
    val meta: SaveArchiveEntryMeta,
)

/**
 * Persistent queue of SRAM snapshots awaiting delivery to the phone.
 *
 * Each entry is a pair of files: "{timestampMs}_{sanitizedRomId}.blob" and the
 * matching ".json" meta. The meta is written last as a commit marker — a blob
 * without meta is an aborted write and is ignored (and swept). Plain files give
 * reboot survival for free. Entries are deleted only after the phone acks a
 * durable write (or rejects the entry as poison).
 */
class SaveSyncOutbox(private val dir: File) {

    companion object {
        private const val TAG = "SaveSyncOutbox"
        private const val MAX_PER_ROM = 10
        private const val MAX_TOTAL = 50
        private const val MAX_BLOB_BYTES = 512 * 1024L

        @Volatile
        private var instance: SaveSyncOutbox? = null

        fun getInstance(context: Context): SaveSyncOutbox {
            return instance ?: synchronized(this) {
                instance
                    ?: SaveSyncOutbox(File(context.applicationContext.filesDir, "sync_outbox"))
                        .also { instance = it }
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun enqueue(romId: String, bytes: ByteArray, sha256: String, timestampMs: Long): Boolean {
        if (bytes.isEmpty() || bytes.size > MAX_BLOB_BYTES) return false
        dir.mkdirs()

        val existing = list()
        // Dedupe: skip if the newest queued entry for this ROM already has this content.
        val newestForRom = existing.filter { it.meta.romId == romId }
            .maxByOrNull { it.meta.timestampMs }
        if (newestForRom?.meta?.sha256 == sha256) return false

        val base = "${timestampMs}_${sanitize(romId)}"
        val blobFile = File(dir, "$base.blob")
        val metaFile = File(dir, "$base.json")
        val meta = SaveArchiveEntryMeta(
            romId = romId,
            timestampMs = timestampMs,
            sizeBytes = bytes.size.toLong(),
            sha256 = sha256,
        )
        try {
            blobFile.writeBytes(bytes)
            metaFile.writeText(json.encodeToString(SaveArchiveEntryMeta.serializer(), meta))
        } catch (e: Exception) {
            Log.e(TAG, "enqueue failed for $romId", e)
            blobFile.delete()
            metaFile.delete()
            return false
        }

        enforceCaps()
        return true
    }

    /** All committed entries, oldest first. Sweeps aborted writes as a side effect. */
    @Synchronized
    fun list(): List<OutboxEntry> {
        val files = dir.listFiles().orEmpty()
        val entries = mutableListOf<OutboxEntry>()
        for (metaFile in files.filter { it.name.endsWith(".json") }) {
            val blobFile = File(dir, metaFile.name.removeSuffix(".json") + ".blob")
            if (!blobFile.exists()) {
                metaFile.delete()
                continue
            }
            val meta = try {
                json.decodeFromString(SaveArchiveEntryMeta.serializer(), metaFile.readText())
            } catch (_: Exception) {
                metaFile.delete()
                blobFile.delete()
                continue
            }
            entries.add(OutboxEntry(metaFile, blobFile, meta))
        }
        // Sweep orphaned blobs (crash between blob and meta writes).
        for (blob in files.filter { it.name.endsWith(".blob") }) {
            if (!File(dir, blob.name.removeSuffix(".blob") + ".json").exists()) blob.delete()
        }
        return entries.sortedBy { it.meta.timestampMs }
    }

    @Synchronized
    fun delete(entry: OutboxEntry) {
        entry.blobFile.delete()
        entry.metaFile.delete()
    }

    @Synchronized
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    fun isEmpty(): Boolean = list().isEmpty()

    /**
     * When the phone is unreachable for days: keep the newest MAX_PER_ROM per game,
     * then trim globally-oldest down to MAX_TOTAL.
     */
    private fun enforceCaps() {
        val entries = list()
        val toDrop = mutableListOf<OutboxEntry>()
        for ((_, romEntries) in entries.groupBy { it.meta.romId }) {
            val sorted = romEntries.sortedByDescending { it.meta.timestampMs }
            toDrop += sorted.drop(MAX_PER_ROM)
        }
        toDrop.forEach { delete(it) }

        val remaining = entries.filter { it !in toDrop }
        if (remaining.size > MAX_TOTAL) {
            remaining.sortedBy { it.meta.timestampMs }
                .take(remaining.size - MAX_TOTAL)
                .forEach { delete(it) }
        }
    }

    private fun sanitize(romId: String): String =
        romId.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
