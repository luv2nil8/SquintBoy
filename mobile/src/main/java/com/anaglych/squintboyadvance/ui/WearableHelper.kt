package com.anaglych.squintboyadvance.ui

import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Default timeout for wearable request-response flows. */
const val WEARABLE_TIMEOUT_MS = 5000L

/**
 * Sends a message to the first connected wearable node, then starts a timeout
 * coroutine that fires [onTimeout] if no response cancels [timeoutJob] in time.
 *
 * @return the launched timeout [Job], or null if no node was connected.
 */
suspend fun sendWearableRequest(
    nodeClient: NodeClient,
    messageClient: MessageClient,
    path: String,
    data: ByteArray = byteArrayOf(),
    scope: CoroutineScope,
    tag: String,
    timeoutMs: Long = WEARABLE_TIMEOUT_MS,
    onNoNode: () -> Unit = {},
    onTimeout: () -> Unit = {},
    onError: (Exception) -> Unit = {},
): Job? {
    return try {
        val nodeId = nodeClient.connectedNodes.await().firstOrNull()?.id
        if (nodeId == null) {
            onNoNode()
            return null
        }
        messageClient.sendMessage(nodeId, path, data).await()
        scope.launch {
            delay(timeoutMs)
            onTimeout()
        }
    } catch (e: Exception) {
        Log.e(tag, "Failed to send wearable request to $path", e)
        onError(e)
        null
    }
}
