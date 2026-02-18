package com.example.squintboyadvance.presentation

import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.android.gms.tasks.Tasks
import java.io.BufferedInputStream
import java.io.File

class RomReceiverService : WearableListenerService() {

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        try {
            val inputStream = BufferedInputStream(
                Tasks.await(Wearable.getChannelClient(this).getInputStream(channel))
            )

            // Read filename header (first line, terminated by newline)
            val headerBytes = mutableListOf<Byte>()
            while (true) {
                val b = inputStream.read()
                if (b == -1 || b == '\n'.code) break
                headerBytes.add(b.toByte())
            }
            val filename = String(headerBytes.toByteArray(), Charsets.UTF_8).trim()
            if (filename.isEmpty()) return

            // Sanitize filename — strip path separators
            val safeName = filename.replace('/', '_').replace('\\', '_')

            val romsDir = File(filesDir, "roms").apply { mkdirs() }
            val outFile = File(romsDir, safeName)

            outFile.outputStream().use { out ->
                inputStream.copyTo(out)
            }
        } catch (e: Exception) {
            android.util.Log.e("RomReceiver", "Failed to receive ROM", e)
        } finally {
            try {
                Tasks.await(Wearable.getChannelClient(this).close(channel))
            } catch (_: Exception) {}
        }
    }
}
