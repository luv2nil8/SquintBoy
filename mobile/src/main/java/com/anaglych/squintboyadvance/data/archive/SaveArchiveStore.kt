package com.anaglych.squintboyadvance.data.archive

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.anaglych.squintboyadvance.data.db.ArchivedSaveEntity
import com.anaglych.squintboyadvance.data.db.DriveState
import com.anaglych.squintboyadvance.data.db.LocalState
import com.anaglych.squintboyadvance.data.db.SaveSyncDatabase
import com.anaglych.squintboyadvance.data.sync.SaveSyncSettingsRepository
import com.anaglych.squintboyadvance.shared.util.HashUtils
import java.io.InputStream
import kotlinx.serialization.json.Json

/**
 * All DocumentFile operations against the user-chosen SAF archive folder.
 * SAF has no atomic rename and providers may mutate file names on create, so
 * every write verifies length and returns the actual stored name.
 */
class SaveArchiveStore(private val context: Context) {

    companion object {
        private const val TAG = "SaveArchiveStore"
        const val MANIFEST_NAME = "manifest.json"
        private const val MIME = "application/octet-stream"
    }

    private val settingsRepo = SaveSyncSettingsRepository.getInstance(context)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private fun treeRoot(): DocumentFile? {
        val uriStr = settingsRepo.settings.value.treeUri ?: return null
        return try {
            val doc = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
            if (doc != null && doc.exists() && doc.canWrite()) doc else null
        } catch (e: Exception) {
            Log.w(TAG, "Archive root unresolvable: ${e.message}")
            null
        }
    }

    /** Also maintains the folderMissing flag that drives the relink banner. */
    fun isAvailable(): Boolean {
        val available = treeRoot() != null
        settingsRepo.folderMissing.value = settingsRepo.settings.value.enabled && !available
        return available
    }

    fun ensureGameDir(gameDirName: String): DocumentFile? {
        val root = treeRoot() ?: return null
        root.findFile(gameDirName)?.let { if (it.isDirectory) return it }
        return root.createDirectory(gameDirName)
    }

    fun findGameDir(gameDirName: String): DocumentFile? =
        treeRoot()?.findFile(gameDirName)?.takeIf { it.isDirectory }

    /**
     * Writes and verifies a save blob. Returns the ACTUAL stored file name
     * (providers may append extensions), or null on any failure.
     */
    fun writeSave(gameDir: DocumentFile, desiredName: String, bytes: ByteArray): String? {
        // A leftover from a crashed prior attempt would break the unique index; replace it.
        gameDir.findFile(desiredName)?.delete()
        val file = gameDir.createFile(MIME, desiredName) ?: return null
        try {
            context.contentResolver.openOutputStream(file.uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: run {
                file.delete()
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeSave failed for $desiredName", e)
            file.delete()
            return null
        }
        if (file.length() != bytes.size.toLong()) {
            Log.e(TAG, "writeSave length mismatch for $desiredName: ${file.length()} != ${bytes.size}")
            file.delete()
            return null
        }
        return file.name ?: desiredName
    }

    fun openSave(gameDirName: String, fileName: String): InputStream? {
        val file = findGameDir(gameDirName)?.findFile(fileName) ?: return null
        return try {
            context.contentResolver.openInputStream(file.uri)
        } catch (e: Exception) {
            Log.w(TAG, "openSave failed for $gameDirName/$fileName: ${e.message}")
            null
        }
    }

    fun deleteSave(gameDirName: String, fileName: String): Boolean {
        val file = findGameDir(gameDirName)?.findFile(fileName) ?: return true // already gone
        return file.delete()
    }

    fun readManifest(gameDir: DocumentFile): GameArchiveManifest? {
        val file = gameDir.findFile(MANIFEST_NAME) ?: return null
        return try {
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                json.decodeFromString(
                    GameArchiveManifest.serializer(),
                    input.readBytes().toString(Charsets.UTF_8),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Manifest unreadable in ${gameDir.name}: ${e.message}")
            null
        }
    }

    fun writeManifest(gameDir: DocumentFile, manifest: GameArchiveManifest): Boolean {
        return try {
            val existing = gameDir.findFile(MANIFEST_NAME)
            val file = existing ?: gameDir.createFile("application/json", MANIFEST_NAME)
                ?: return false
            // "wt" truncates; the manifest is small enough that a whole rewrite is fine.
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
                out.write(
                    json.encodeToString(GameArchiveManifest.serializer(), manifest)
                        .toByteArray(Charsets.UTF_8)
                )
                out.flush()
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "writeManifest failed in ${gameDir.name}", e)
            false
        }
    }

    /** Reads, transforms, and rewrites a game's manifest in one step. */
    fun updateManifest(
        gameDir: DocumentFile,
        create: () -> GameArchiveManifest,
        transform: (GameArchiveManifest) -> GameArchiveManifest,
    ): Boolean {
        val current = readManifest(gameDir) ?: create()
        return writeManifest(gameDir, transform(current))
    }

    /**
     * Rebuilds the Room index from the archive folder: manifest entries whose
     * files exist become rows (Drive ids preserved); save files absent from the
     * manifest are hashed and adopted. Used after relink and on fresh installs.
     */
    suspend fun rescan(): Int {
        val root = treeRoot() ?: return 0
        val dao = SaveSyncDatabase.getInstance(context).archivedSaveDao()
        val driveEnabled = settingsRepo.settings.value.driveEnabled
        var imported = 0

        for (gameDir in root.listFiles().filter { it.isDirectory }) {
            val manifest = readManifest(gameDir) ?: continue // unknown dir; can't derive romId
            val gameDirName = gameDir.name ?: continue
            val byName = manifest.entries.associateBy { it.fileName }
            var manifestDirty = false
            var entries = manifest.entries

            for (file in gameDir.listFiles().filter { it.isFile && it.name != MANIFEST_NAME }) {
                val fileName = file.name ?: continue
                if (dao.findByFile(gameDirName, fileName) != null) continue

                val listed = byName[fileName]
                val entry = listed ?: run {
                    // Unlisted file (e.g. crash between write and manifest update): adopt it.
                    val bytes = context.contentResolver.openInputStream(file.uri)
                        ?.use { it.readBytes() } ?: return@run null
                    ManifestEntry(
                        fileName = fileName,
                        timestampMs = file.lastModified().takeIf { it > 0 }
                            ?: System.currentTimeMillis(),
                        sizeBytes = bytes.size.toLong(),
                        sha256 = HashUtils.sha256Hex(bytes),
                    ).also {
                        entries = entries + it
                        manifestDirty = true
                    }
                } ?: continue

                dao.insert(
                    ArchivedSaveEntity(
                        romId = manifest.romId,
                        gameDirName = gameDirName,
                        fileName = fileName,
                        timestampMs = entry.timestampMs,
                        dayKey = GameDirNaming.dayKey(entry.timestampMs),
                        sizeBytes = entry.sizeBytes,
                        sha256 = entry.sha256,
                        pinned = entry.pinned,
                        note = entry.note,
                        localState = LocalState.PRESENT,
                        driveState = when {
                            entry.driveFileId != null -> DriveState.UPLOADED
                            driveEnabled -> DriveState.PENDING
                            else -> DriveState.NOT_SYNCED
                        },
                        driveFileId = entry.driveFileId,
                    )
                )
                imported++
            }
            if (manifestDirty) writeManifest(gameDir, manifest.copy(entries = entries))
        }
        Log.i(TAG, "Rescan imported $imported save(s)")
        return imported
    }
}
