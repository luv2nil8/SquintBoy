package com.example.squintboyadvance.presentation

import android.util.Log
import com.example.squintboyadvance.shared.model.*
import com.example.squintboyadvance.shared.protocol.WearMessageConstants
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
        val inputStream = BufferedInputStream(
            Tasks.await(Wearable.getChannelClient(this).getInputStream(channel))
        )

        val filename = readHeaderLine(inputStream) ?: return
        val safeName = filename.replace('/', '_').replace('\\', '_')

        val romsDir = File(filesDir, "roms").apply { mkdirs() }
        val outFile = File(romsDir, safeName)

        outFile.outputStream().use { out ->
            inputStream.copyTo(out)
        }
        Log.i(TAG, "Received ROM: $safeName (${outFile.length()} bytes)")
    }

    private fun handleSavePush(channel: ChannelClient.Channel) {
        val inputStream = BufferedInputStream(
            Tasks.await(Wearable.getChannelClient(this).getInputStream(channel))
        )

        // Header: "romId/filename\n"
        val header = readHeaderLine(inputStream) ?: return
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
        val romId = readHeaderLine(inputStream) ?: return
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
        val newSettings = json.decodeFromString(EmulatorSettings.serializer(), settingsJson)
        val settingsRepo = SettingsRepository.getInstance(this)
        settingsRepo.update { newSettings }
        Log.i(TAG, "Settings synced from phone")
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

    // ── Helpers ────────────────────────────────────────────────────────

    private fun readHeaderLine(input: BufferedInputStream): String? {
        val bytes = mutableListOf<Byte>()
        while (true) {
            val b = input.read()
            if (b == -1 || b == '\n'.code) break
            bytes.add(b.toByte())
        }
        val line = String(bytes.toByteArray(), Charsets.UTF_8).trim()
        return line.ifEmpty { null }
    }

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
