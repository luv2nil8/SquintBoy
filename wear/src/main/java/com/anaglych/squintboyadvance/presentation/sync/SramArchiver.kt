package com.anaglych.squintboyadvance.presentation.sync

import android.util.Log
import com.anaglych.squintboyadvance.shared.util.HashUtils
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Detects SRAM changes during gameplay by hashing the live .sav on a slow poll.
 *
 * The poll is a plain file read of the .sav that mGBA writes through an mmap'd
 * VFile — no JNI call is made, so the emulator mutex is irrelevant. A concurrent
 * write can at worst produce a torn read: a hash that won't repeat on the next
 * tick, which the StabilityTracker's two-consecutive-equal-hashes rule filters.
 * The pause/stop snapshot ([snapshotNow]) is authoritative because the emulator
 * thread is already halted there.
 */
class SramArchiver(
    private val savFile: File,
    private val romId: String,
    private val outbox: SaveSyncOutbox,
    private val hashStore: ArchiveHashStore,
    private val isRunning: () -> Boolean,
    private val onQueued: () -> Unit,
) {
    companion object {
        private const val TAG = "SramArchiver"
        private const val POLL_INTERVAL_MS = 5_000L
    }

    private val tracker = StabilityTracker(hashStore.get(romId))
    private var pollJob: Job? = null

    fun start(scope: CoroutineScope) {
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (!isRunning()) continue // paused sessions snapshot via snapshotNow()
                val bytes = readSav() ?: continue
                val hash = HashUtils.sha256Hex(bytes)
                if (tracker.onSample(hash)) archive(bytes, hash)
            }
        }
    }

    /** Called at pause/stop, when the emulator thread is halted and the file is quiescent. */
    fun snapshotNow() {
        val bytes = readSav() ?: return
        val hash = HashUtils.sha256Hex(bytes)
        if (tracker.onFinalSample(hash)) archive(bytes, hash)
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun readSav(): ByteArray? {
        return try {
            if (!savFile.exists() || savFile.length() == 0L) null else savFile.readBytes()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read ${savFile.name}: ${e.message}")
            null
        }
    }

    private fun archive(bytes: ByteArray, hash: String) {
        val queued = outbox.enqueue(romId, bytes, hash, System.currentTimeMillis())
        // Mark archived even when the outbox deduped it — content is identical.
        tracker.onArchived(hash)
        hashStore.put(romId, hash)
        if (queued) {
            Log.i(TAG, "Queued SRAM snapshot for $romId (${bytes.size} bytes)")
            onQueued()
        }
    }
}
