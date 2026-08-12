package com.anaglych.squintboyadvance.data.sync

import android.util.Log
import com.anaglych.squintboyadvance.shared.protocol.WearMessageConstants
import com.anaglych.squintboyadvance.shared.util.readLine
import com.google.android.gms.wearable.ChannelClient
import java.io.BufferedInputStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Pushes a .sav to the watch as a ROM's live save. Tries the validated push_v2
 * protocol (size + sha256 verified, in-place install, explicit ack) and falls
 * back to the legacy push for old watch builds. Used by both the archive
 * restore and the manual upload flow.
 */
object WatchSavePusher {

    private const val TAG = "WatchSavePusher"
    private const val ACK_TIMEOUT_MS = 15_000L

    suspend fun push(
        channelClient: ChannelClient,
        nodeId: String,
        romId: String,
        bytes: ByteArray,
        sha256: String,
    ) {
        if (!tryPushV2(channelClient, nodeId, romId, bytes, sha256)) {
            Log.i(TAG, "push_v2 failed; falling back to legacy push")
            legacyPush(channelClient, nodeId, romId, bytes)
        }
    }

    private suspend fun tryPushV2(
        channelClient: ChannelClient,
        nodeId: String,
        romId: String,
        bytes: ByteArray,
        sha256: String,
    ): Boolean = coroutineScope {
        val romBaseName = romId.substringBeforeLast('.')
        try {
            val channel =
                channelClient.openChannel(nodeId, WearMessageConstants.PATH_SAVE_PUSH_V2).await()
            var watchdog: Job? = null
            try {
                val out = channelClient.getOutputStream(channel).await()
                val input = BufferedInputStream(channelClient.getInputStream(channel).await())
                out.write("$romId/$romBaseName.sav\n".toByteArray(Charsets.UTF_8))
                out.write("${bytes.size}\n".toByteArray(Charsets.UTF_8))
                out.write("$sha256\n".toByteArray(Charsets.UTF_8))
                out.write(bytes)
                out.flush()

                // The blocking ack read is unblocked by closing the channel on timeout.
                watchdog = launch {
                    delay(ACK_TIMEOUT_MS)
                    try {
                        channelClient.close(channel).await()
                    } catch (_: Exception) {}
                }
                val ack = readLine(input)
                ack == "OK"
            } finally {
                watchdog?.cancel()
                try {
                    channelClient.close(channel).await()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "push_v2 error: ${e.message}")
            false
        }
    }

    private suspend fun legacyPush(
        channelClient: ChannelClient,
        nodeId: String,
        romId: String,
        bytes: ByteArray,
    ) {
        val romBaseName = romId.substringBeforeLast('.')
        val channel =
            channelClient.openChannel(nodeId, WearMessageConstants.PATH_SAVE_PUSH).await()
        try {
            val out = channelClient.getOutputStream(channel).await()
            out.use {
                it.write("$romId/$romBaseName.sav\n".toByteArray(Charsets.UTF_8))
                it.write(bytes)
            }
        } finally {
            channelClient.close(channel).await()
        }
    }
}
