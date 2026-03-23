package com.anaglych.squintboyadvance.presentation

import android.os.PowerManager
import android.util.Log
import com.anaglych.squintboyadvance.shared.model.*
import com.anaglych.squintboyadvance.shared.model.DemoLimits
import com.anaglych.squintboyadvance.shared.protocol.WearMessageConstants
import com.anaglych.squintboyadvance.shared.util.readLine
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.android.gms.tasks.Tasks
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer

class RomReceiverService : WearableListenerService() {

    companion object {
        private const val TAG = "RomReceiverService"
        private const val STALL_TIMEOUT_MS = 30_000L
    }

    private val json = Json { ignoreUnknownKeys = true }

    // ── Channel dispatch ──────────────────────────────────────────────

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        try {
            when (channel.path) {
                WearMessageConstants.PATH_ROM_TRANSFER -> handleRomTransfer(channel)
                WearMessageConstants.PATH_SAVE_PUSH -> handleSavePush(channel)
                WearMessageConstants.PATH_SAVE_PULL -> handleSavePull(channel)
                else -> Log.w(TAG, "Unknown channel path: ${channel.path}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Channel error on ${channel.path}", e)
        } finally {
            try {
                Tasks.await(Wearable.getChannelClient(this).close(channel))
            } catch (_: Exception) {}
        }
    }

    private fun handleRomTransfer(channel: ChannelClient.Channel) {
        // Demo limits: reject GBA transfers and enforce ROM cap
        val entitlement = EntitlementRepository.getInstance(this)
        val romsDir = File(filesDir, "roms").apply { mkdirs() }
        if (entitlement.isDemo) {
            val existingRomCount = romsDir.listFiles().orEmpty()
                .count { it.isFile && !it.name.startsWith(".") }
            if (existingRomCount >= DemoLimits.MAX_ROMS) {
                sendTransferResult(
                    channel, "unknown", false, 0, 0,
                    "Demo limit: ${DemoLimits.MAX_ROMS} ROMs max. Get the full version for unlimited ROMs."
                )
                return
            }
        }

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "SquintBoy:RomTransfer"
        ) // acquired after we know file size
        var tempFile: File? = null
        var receivedBytes = 0L
        var expectedSize = 0L
        var safeName = ""

        try {
            val inputStream = BufferedInputStream(
                Tasks.await(Wearable.getChannelClient(this).getInputStream(channel))
            )

            val filename = readLine(inputStream)
                ?: throw Exception("Missing filename header")
            val sizeStr = readLine(inputStream)
                ?: throw Exception("Missing filesize header")
            expectedSize = sizeStr.toLongOrNull()
                ?: throw Exception("Invalid filesize header: $sizeStr")

            // Acquire wake lock scaled to file size: ~100KB/s min BT + 30s overhead, min 60s
            val timeoutMs = maxOf((expectedSize / 100_000) * 1000 + 30_000, 60_000L)
            wakeLock.acquire(timeoutMs)

            safeName = filename.replace('/', '_').replace('\\', '_')
            tempFile = File(romsDir, ".transferring_$safeName")
            tempFile.createNewFile() // must exist on disk before signal

            // Signal library so shimmer card appears immediately
            RomLibrarySignal.emit()

            tempFile.outputStream().use { out ->
                val buffer = ByteArray(8192)
                val lastDataTime = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())

                // Watchdog closes the stream if no progress for 30s
                val watchdog = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
                    Thread(r, "transfer-watchdog").apply { isDaemon = true }
                }
                watchdog.scheduleAtFixedRate({
                    if (System.currentTimeMillis() - lastDataTime.get() > STALL_TIMEOUT_MS) {
                        try { inputStream.close() } catch (_: Exception) {}
                    }
                }, 10, 5, java.util.concurrent.TimeUnit.SECONDS)

                try {
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        receivedBytes += read
                        lastDataTime.set(System.currentTimeMillis())
                    }
                } catch (e: java.io.IOException) {
                    if (receivedBytes < expectedSize) {
                        throw Exception("Transfer stalled — no data for 30s")
                    }
                    // If we got all bytes, the watchdog closed a finished stream — that's fine
                } finally {
                    watchdog.shutdownNow()
                }
            }

            if (receivedBytes != expectedSize) {
                throw Exception("Size mismatch: expected $expectedSize, got $receivedBytes")
            }

            val outFile = File(romsDir, safeName)
            if (!tempFile.renameTo(outFile)) {
                // renameTo can fail on some filesystems; fall back to copy+delete
                tempFile.copyTo(outFile, overwrite = true)
                tempFile.delete()
            }
            tempFile = null // prevent finally-block cleanup

            Log.i(TAG, "Received ROM: $safeName ($receivedBytes bytes)")
            RomLibrarySignal.emitTransfer(safeName, true)
            RomLibrarySignal.emit()
            sendTransferResult(channel, safeName, true, receivedBytes, expectedSize)
        } catch (e: Exception) {
            Log.e(TAG, "ROM transfer failed: ${e.message}", e)
            tempFile?.delete()
            RomLibrarySignal.emit() // rescan so shimmer card disappears
            RomLibrarySignal.emitTransfer(
                safeName.ifEmpty { "unknown" }, false, e.message
            )
            sendTransferResult(
                channel, safeName.ifEmpty { "unknown" },
                false, receivedBytes, expectedSize, e.message
            )
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private fun sendTransferResult(
        channel: ChannelClient.Channel,
        filename: String,
        success: Boolean,
        receivedBytes: Long,
        expectedBytes: Long,
        errorMessage: String? = null,
    ) {
        try {
            val result = TransferResult(filename, success, receivedBytes, expectedBytes, errorMessage)
            val payload = json.encodeToString(TransferResult.serializer(), result)
            Tasks.await(
                Wearable.getMessageClient(this).sendMessage(
                    channel.nodeId,
                    WearMessageConstants.PATH_ROM_TRANSFER_RESULT,
                    payload.toByteArray(Charsets.UTF_8),
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send transfer result", e)
        }
    }

    private fun handleSavePush(channel: ChannelClient.Channel) {
        val inputStream = BufferedInputStream(
            Tasks.await(Wearable.getChannelClient(this).getInputStream(channel))
        )

        // Header: "romId/filename\n"
        val header = readLine(inputStream) ?: return
        val slashIdx = header.indexOf('/')
        if (slashIdx < 0) return
        val romId = header.substring(0, slashIdx)
        val fileName = header.substring(slashIdx + 1)

        val savesDir = File(filesDir, "saves").apply { mkdirs() }
        val outFile = File(savesDir, fileName)
        outFile.outputStream().use { out ->
            inputStream.copyTo(out)
        }
        Log.i(TAG, "Received save: $romId/$fileName (${outFile.length()} bytes)")
    }

    private fun handleSavePull(channel: ChannelClient.Channel) {
        val channelClient = Wearable.getChannelClient(this)
        val inputStream = BufferedInputStream(
            Tasks.await(channelClient.getInputStream(channel))
        )
        val outputStream = Tasks.await(channelClient.getOutputStream(channel))

        // Header: "romId\n"
        val romId = readLine(inputStream) ?: return
        val romBaseName = romId.substringBeforeLast('.')

        // Collect SRAM save files for this ROM (flat in saves/, matched by prefix)
        val savesDir = File(filesDir, "saves")
        val files = savesDir.listFiles().orEmpty().filter { f ->
            f.isFile && f.name.startsWith("$romBaseName.sav")
        }

        DataOutputStream(outputStream).use { out ->
            // Write JSON manifest as first line
            val entries = files.map { f ->
                SaveFileEntry(
                    romId = romId,
                    fileName = f.name,
                    sizeBytes = f.length(),
                    lastModified = f.lastModified(),
                    type = classifySaveFile(f.name)
                )
            }
            val manifest = json.encodeToString(SaveListResponse.serializer(), SaveListResponse(entries))
            out.write("$manifest\n".toByteArray(Charsets.UTF_8))

            // Write each file: 8-byte big-endian size + raw bytes
            for (f in files) {
                val size = f.length()
                val sizeBytes = ByteBuffer.allocate(8).putLong(size).array()
                out.write(sizeBytes)
                f.inputStream().use { it.copyTo(out) }
            }
            out.flush()
        }
        Log.i(TAG, "Streamed ${files.size} save files for $romId")
    }

    // ── Message dispatch ──────────────────────────────────────────────

    override fun onMessageReceived(event: MessageEvent) {
        try {
            when (event.path) {
                WearMessageConstants.PATH_ROM_LIST_REQUEST -> handleRomListRequest(event)
                WearMessageConstants.PATH_ROM_DELETE -> handleRomDelete(event)
                WearMessageConstants.PATH_SETTINGS_REQUEST -> handleSettingsRequest(event)
                WearMessageConstants.PATH_SETTINGS_SYNC -> handleSettingsSync(event)
                WearMessageConstants.PATH_SAVE_LIST_REQUEST -> handleSaveListRequest(event)
                WearMessageConstants.PATH_SAVE_CLEAR_STACKS -> handleSaveClearStacks(event)
                WearMessageConstants.PATH_ROM_RENAME -> handleRomRename(event)
                WearMessageConstants.PATH_SCREEN_INFO_REQUEST -> handleScreenInfoRequest(event)
                WearMessageConstants.PATH_ENTITLEMENT_PUSH -> {
                    val isPro = String(event.data, Charsets.UTF_8) == "1"
                    EntitlementRepository.getInstance(this).handleEntitlementPush(isPro)
                    Log.i(TAG, "Entitlement pushed from phone: isPro=$isPro")
                }
                WearMessageConstants.PATH_ENTITLEMENT_RESPONSE -> {
                    // Phone responds to our startup entitlement request
                    val isPro = String(event.data, Charsets.UTF_8) == "1"
                    EntitlementRepository.getInstance(this).handleEntitlementPush(isPro)
                    Log.i(TAG, "Entitlement response from phone: isPro=$isPro")
                }
                WearMessageConstants.PATH_WATCH_PING -> {
                    Tasks.await(
                        Wearable.getMessageClient(this).sendMessage(
                            event.sourceNodeId,
                            WearMessageConstants.PATH_WATCH_PONG,
                            byteArrayOf(),
                        )
                    )
                    Log.d(TAG, "Replied watch pong to ${event.sourceNodeId}")
                }
                else -> Log.w(TAG, "Unknown message path: ${event.path}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Message error on ${event.path}", e)
        }
    }

    private fun handleRomListRequest(event: MessageEvent) {
        val romsDir = File(filesDir, "roms")
        val metadataStore = RomMetadataStore.getInstance(this)
        val romFiles = romsDir.listFiles().orEmpty().filter { it.isFile }

        val entries = romFiles.map { f ->
            val romId = f.name
            val meta = metadataStore.get(romId)
            val ext = romId.substringAfterLast('.', "").lowercase()
            WatchRomEntry(
                romId = romId,
                systemType = SystemType.fromExtension(ext) ?: SystemType.GB,
                fileSize = f.length(),
                lastPlayed = meta.lastPlayed,
                totalPlayTimeMs = meta.totalPlayTimeMs
            )
        }

        val response = json.encodeToString(RomListResponse.serializer(), RomListResponse(entries))
        Tasks.await(
            Wearable.getMessageClient(this)
                .sendMessage(event.sourceNodeId, WearMessageConstants.PATH_ROM_LIST_RESPONSE, response.toByteArray())
        )
    }

    private fun handleRomDelete(event: MessageEvent) {
        val romId = String(event.data, Charsets.UTF_8)
        val romBaseName = romId.substringBeforeLast('.')

        // Delete ROM file
        File(filesDir, "roms/$romId").delete()
        // Delete SRAM saves (flat: {baseName}.sav, {baseName}.sav.0, etc.)
        File(filesDir, "saves").listFiles()?.filter { it.name.startsWith("$romBaseName.sav") }
            ?.forEach { it.delete() }
        // Delete save states (flat: {baseName}.ss0, etc.)
        File(filesDir, "states").listFiles()?.filter { it.name.startsWith("$romBaseName.") }
            ?.forEach { it.delete() }
        // Delete screenshot
        File(filesDir, "screenshots/$romId.png").delete()
        // Clear metadata
        val metadataStore = RomMetadataStore.getInstance(this)
        metadataStore.remove(romId)
        Log.i(TAG, "Deleted ROM and associated data: $romId")
        RomLibrarySignal.emit()
    }

    private fun handleSettingsRequest(event: MessageEvent) {
        val settingsRepo = SettingsRepository.getInstance(this)
        val settings = settingsRepo.settings.value
        val response = json.encodeToString(EmulatorSettings.serializer(), settings)
        Tasks.await(
            Wearable.getMessageClient(this)
                .sendMessage(event.sourceNodeId, WearMessageConstants.PATH_SETTINGS_RESPONSE, response.toByteArray())
        )
    }

    private fun handleSettingsSync(event: MessageEvent) {
        val settingsJson = String(event.data, Charsets.UTF_8)
        val incoming = json.decodeFromString(EmulatorSettings.serializer(), settingsJson)
        val settingsRepo = SettingsRepository.getInstance(this)
        // Merge: apply incoming global fields but preserve local romOverrides
        settingsRepo.update { current ->
            incoming.copy(romOverrides = current.romOverrides + incoming.romOverrides)
        }
        Log.i(TAG, "Settings synced from phone")
    }

    private fun handleScreenInfoRequest(event: MessageEvent) {
        // Use screenWidthDp * density to match the pause overlay's scale computation exactly
        val dm = resources.displayMetrics
        val screenWidthPx = (resources.configuration.screenWidthDp * dm.density).toInt()
        Tasks.await(
            Wearable.getMessageClient(this)
                .sendMessage(
                    event.sourceNodeId,
                    WearMessageConstants.PATH_SCREEN_INFO_RESPONSE,
                    screenWidthPx.toString().toByteArray()
                )
        )
    }

    private fun handleSaveListRequest(event: MessageEvent) {
        val savesDir = File(filesDir, "saves")
        val romsDir = File(filesDir, "roms")

        // Build a set of known ROM base names to associate .sav files with romIds
        val romFiles = romsDir.listFiles().orEmpty().filter { it.isFile }
        val baseNameToRomId = romFiles.associate { it.nameWithoutExtension to it.name }

        val entries = mutableListOf<SaveFileEntry>()
        for (f in savesDir.listFiles().orEmpty().filter { it.isFile }) {
            // e.g. "Pokemon Crystal.sav" or "Pokemon Crystal.sav.0"
            val baseName = f.name.substringBefore(".sav")
            val romId = baseNameToRomId[baseName] ?: "$baseName.unknown"
            entries.add(
                SaveFileEntry(
                    romId = romId,
                    fileName = f.name,
                    sizeBytes = f.length(),
                    lastModified = f.lastModified(),
                    type = classifySaveFile(f.name)
                )
            )
        }

        val response = json.encodeToString(SaveListResponse.serializer(), SaveListResponse(entries))
        Tasks.await(
            Wearable.getMessageClient(this)
                .sendMessage(event.sourceNodeId, WearMessageConstants.PATH_SAVE_LIST_RESPONSE, response.toByteArray())
        )
    }

    private fun handleSaveClearStacks(event: MessageEvent) {
        val romId = String(event.data, Charsets.UTF_8)
        val romBaseName = romId.substringBeforeLast('.')
        val statesDir = File(filesDir, "states")
        val savesDir = File(filesDir, "saves")
        for (i in 0..4) {
            File(statesDir, "$romBaseName.ss$i").delete()
            File(savesDir, "$romBaseName.sav.$i").delete()
        }
        Log.i(TAG, "Cleared save/state stacks for $romBaseName")
    }

    private fun handleRomRename(event: MessageEvent) {
        val payload = String(event.data, Charsets.UTF_8)
        val newlineIdx = payload.indexOf('\n')
        if (newlineIdx < 0) return
        val romId = payload.substring(0, newlineIdx)
        val newName = payload.substring(newlineIdx + 1).trim()
        val metadataStore = RomMetadataStore.getInstance(this)
        metadataStore.update(romId) { it.copy(displayName = newName.ifEmpty { null }) }
        Log.i(TAG, "Renamed ROM $romId → ${newName.ifEmpty { "(cleared)" }}")
        RomLibrarySignal.emit()
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun classifySaveFile(name: String): SaveFileType {
        return when {
            name.endsWith(".ss0") || name.endsWith(".ss1") ||
            name.endsWith(".ss2") || name.endsWith(".ss3") ||
            name.endsWith(".ss4") -> SaveFileType.STATE
            name.contains(".sav.") -> SaveFileType.SRAM_BACKUP
            name.endsWith(".sav") -> SaveFileType.SRAM_LIVE
            else -> SaveFileType.SRAM_LIVE
        }
    }
}
