package com.anaglych.squintboyadvance

import android.content.Intent
import android.util.Log
import com.anaglych.squintboyadvance.data.sync.ArchiveIngestor
import com.anaglych.squintboyadvance.data.sync.IngestAck
import com.anaglych.squintboyadvance.data.sync.SaveSyncSettingsRepository
import com.anaglych.squintboyadvance.shared.model.SaveArchiveEntryMeta
import com.anaglych.squintboyadvance.shared.model.TransferResult
import com.anaglych.squintboyadvance.shared.protocol.WearMessageConstants
import com.anaglych.squintboyadvance.ui.ArchiveChangedSignal
import com.anaglych.squintboyadvance.ui.RomPickerTrigger
import com.anaglych.squintboyadvance.ui.TransferResultSignal
import com.anaglych.squintboyadvance.work.DriveSyncWorker
import com.anaglych.squintboyadvance.work.RetentionWorker
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class MobileListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "MobileListenerService"

        // Watch-side blob cap is 512 KB; anything past this is a corrupt stream.
        private const val MAX_BLOB_BYTES = 1024 * 1024L
    }

    private val json = Json { ignoreUnknownKeys = true }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearMessageConstants.PATH_OPEN_ROM_PICKER -> {
                RomPickerTrigger.fire()
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            }
            WearMessageConstants.PATH_PHONE_PING -> {
                try {
                    Wearable.getMessageClient(this).sendMessage(
                        event.sourceNodeId,
                        WearMessageConstants.PATH_PHONE_PONG,
                        byteArrayOf(),
                    )
                    Log.d(TAG, "Replied pong to ${event.sourceNodeId}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reply pong", e)
                }
            }
            WearMessageConstants.PATH_WATCH_PONG -> {
                WatchPongSignal.emit()
                Log.d(TAG, "Received watch pong from ${event.sourceNodeId}")
            }
            WearMessageConstants.PATH_ROM_TRANSFER_RESULT -> {
                try {
                    val payload = String(event.data, Charsets.UTF_8)
                    val result = json.decodeFromString(TransferResult.serializer(), payload)
                    TransferResultSignal.emit(result)
                    Log.i(TAG, "Transfer result for ${result.filename}: success=${result.success}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode transfer result", e)
                }
            }
            WearMessageConstants.PATH_SAVE_SYNC_CONFIG_REQUEST -> {
                SaveSyncSettingsRepository.getInstance(this).pushConfigToWatch()
            }
        }
    }

    // ── Archive push (watch → phone) ──────────────────────────────────

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path != WearMessageConstants.PATH_SAVE_ARCHIVE_PUSH) return
        try {
            handleArchivePush(channel)
        } catch (e: Exception) {
            Log.e(TAG, "Archive push failed", e)
        } finally {
            try {
                Tasks.await(Wearable.getChannelClient(this).close(channel))
            } catch (_: Exception) {}
        }
    }

    /**
     * Lock-step receive loop. The watch deletes its outbox entry when it reads
     * "OK", so ArchiveIngestor must have durably written before we ack.
     */
    private fun handleArchivePush(channel: ChannelClient.Channel) {
        val channelClient = Wearable.getChannelClient(this)
        val input = BufferedInputStream(Tasks.await(channelClient.getInputStream(channel)))
        val out = Tasks.await(channelClient.getOutputStream(channel))
        val ingestor = ArchiveIngestor(applicationContext)
        var received = 0

        while (true) {
            val line = com.anaglych.squintboyadvance.shared.util.readLine(input) ?: break
            if (line == "END") break

            val meta = try {
                json.decodeFromString(SaveArchiveEntryMeta.serializer(), line)
            } catch (e: Exception) {
                Log.e(TAG, "Bad archive meta line, aborting: ${e.message}")
                break // framing is lost; nothing after this is trustworthy
            }

            val lenBuf = ByteArray(8)
            readFully(input, lenBuf)
            val size = ByteBuffer.wrap(lenBuf).long
            if (size <= 0 || size > MAX_BLOB_BYTES) {
                Log.e(TAG, "Implausible blob size $size, aborting")
                break
            }
            val blob = ByteArray(size.toInt())
            readFully(input, blob)

            val ack = runBlocking { ingestor.ingest(meta, blob) }
            out.write((ack.wire + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
            if (ack is IngestAck.Ok) received++
            if (ack is IngestAck.Retry) break // watch aborts the drain on RETRY
        }

        if (received > 0) {
            Log.i(TAG, "Archived $received save(s) from watch")
            ArchiveChangedSignal.emit()
            // Post-ack work: never delays the ack path above.
            RetentionWorker.runOnce(this)
            DriveSyncWorker.enqueue(this)
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) throw EOFException("Stream ended mid-blob")
            offset += read
        }
    }
}
