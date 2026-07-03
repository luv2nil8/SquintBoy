package com.anaglych.squintboyadvance.presentation.sync

import android.content.Context
import android.util.Log
import com.anaglych.squintboyadvance.shared.protocol.WearMessageConstants
import com.anaglych.squintboyadvance.shared.util.readLine
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Drains the outbox to the phone over a PATH_SAVE_ARCHIVE_PUSH channel using the
 * lock-step ack protocol. Entries are deleted only on OK (durably archived) or
 * REJECT (poison); RETRY or any transport failure leaves the outbox intact for
 * the next trigger (enqueue, session end, capability change, phone drain request).
 */
object OutboxDrainer {

    private const val TAG = "OutboxDrainer"
    private const val ACK_TIMEOUT_S = 30L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    fun requestDrain(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            if (!mutex.tryLock()) return@launch // a drain is already running
            try {
                drain(appContext)
            } catch (e: Exception) {
                Log.w(TAG, "Drain failed: ${e.message}")
            } finally {
                mutex.unlock()
            }
        }
    }

    private fun drain(context: Context) {
        if (!SaveSyncConfigRepository.getInstance(context).enabled.value) return
        val outbox = SaveSyncOutbox.getInstance(context)
        val entries = outbox.list()
        if (entries.isEmpty()) return

        val capability = Tasks.await(
            Wearable.getCapabilityClient(context).getCapability(
                WearMessageConstants.CAPABILITY_PHONE_APP,
                CapabilityClient.FILTER_REACHABLE,
            )
        )
        val node = capability.nodes.firstOrNull { it.isNearby }
            ?: capability.nodes.firstOrNull()
            ?: return // phone unreachable; keep the outbox

        val channelClient = Wearable.getChannelClient(context)
        val channel = Tasks.await(
            channelClient.openChannel(node.id, WearMessageConstants.PATH_SAVE_ARCHIVE_PUSH)
        )
        val watchdog = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "drain-watchdog").apply { isDaemon = true }
        }
        var delivered = 0
        try {
            val out = DataOutputStream(Tasks.await(channelClient.getOutputStream(channel)))
            val input = BufferedInputStream(Tasks.await(channelClient.getInputStream(channel)))

            for (entry in entries) {
                out.write((entry.metaFile.readText() + "\n").toByteArray(Charsets.UTF_8))
                out.write(ByteBuffer.allocate(8).putLong(entry.meta.sizeBytes).array())
                entry.blobFile.inputStream().use { it.copyTo(out) }
                out.flush()

                val ack = readLineWithTimeout(input, watchdog) ?: run {
                    Log.w(TAG, "No ack for ${entry.meta.romId}; aborting drain")
                    return
                }
                when {
                    ack == "OK" -> {
                        outbox.delete(entry)
                        delivered++
                    }
                    ack.startsWith("REJECT") -> {
                        Log.w(TAG, "Phone rejected ${entry.meta.romId}: $ack")
                        outbox.delete(entry)
                    }
                    else -> { // RETRY or anything unrecognized: keep entry, stop here
                        Log.i(TAG, "Phone deferred ${entry.meta.romId}: $ack")
                        return
                    }
                }
            }
            out.write("END\n".toByteArray(Charsets.UTF_8))
            out.flush()
        } finally {
            watchdog.shutdownNow()
            try {
                Tasks.await(channelClient.close(channel))
            } catch (_: Exception) {}
            if (delivered > 0) Log.i(TAG, "Drained $delivered save(s) to phone")
        }
    }

    /** Blocking readLine guarded by a watchdog that closes the stream on stall. */
    private fun readLineWithTimeout(
        input: BufferedInputStream,
        watchdog: java.util.concurrent.ScheduledExecutorService,
    ): String? {
        val killSwitch = watchdog.schedule(
            { try { (input as Closeable).close() } catch (_: Exception) {} },
            ACK_TIMEOUT_S,
            TimeUnit.SECONDS,
        )
        return try {
            readLine(input)
        } catch (_: Exception) {
            null
        } finally {
            killSwitch.cancel(false)
        }
    }
}
