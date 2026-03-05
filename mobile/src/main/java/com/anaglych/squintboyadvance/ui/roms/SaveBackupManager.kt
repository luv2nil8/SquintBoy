package com.anaglych.squintboyadvance.ui.roms

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SaveBackupEntry(
    val file: File,
    /** User-assigned display name, or a date string derived from file mtime. */
    val displayName: String,
)

enum class SaveValidationResult { VALID, EMPTY, SIZE_MISMATCH }

/**
 * Manages local phone-side save backup files: scanning, naming, validation,
 * export, import, and deletion.
 */
class SaveBackupManager(
    private val context: Context,
    private val romId: String,
) {
    companion object {
        private const val TAG = "SaveBackupManager"
        private const val BACKUP_NAMES_PREFS = "backup_names"
        private const val FILE_PROVIDER_AUTHORITY = "com.anaglych.squintboyadvance.fileprovider"
    }

    private val namePrefs = context.getSharedPreferences(BACKUP_NAMES_PREFS, Context.MODE_PRIVATE)

    val backupDir: File
        get() = File(context.filesDir, "backups/$romId").also { it.mkdirs() }

    fun scanBackups(): List<SaveBackupEntry> {
        val files = backupDir.listFiles()
            ?.filter { it.isFile && it.extension == "sav" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        return files.map { f ->
            SaveBackupEntry(
                file = f,
                displayName = namePrefs.getString("$romId/${f.name}", null)
                    ?: formatDate(f.lastModified()),
            )
        }
    }

    fun validate(backup: SaveBackupEntry): SaveValidationResult {
        val size = backup.file.length()
        if (size == 0L) return SaveValidationResult.EMPTY
        val ext = romId.substringAfterLast('.', "").lowercase()
        val validSizes: Set<Long> = when (ext) {
            "gba" -> setOf(512L, 8192L, 65536L, 131072L)
            "gb", "gbc" -> setOf(8192L, 32768L, 131072L)
            else -> return SaveValidationResult.VALID
        }
        return if (size in validSizes) SaveValidationResult.VALID else SaveValidationResult.SIZE_MISMATCH
    }

    fun rename(backup: SaveBackupEntry, newName: String) {
        namePrefs.edit().putString("$romId/${backup.file.name}", newName.trim()).apply()
    }

    fun delete(backup: SaveBackupEntry) {
        backup.file.delete()
        namePrefs.edit().remove("$romId/${backup.file.name}").apply()
    }

    fun export(backup: SaveBackupEntry, context: Context) {
        try {
            val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, backup.file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${backup.displayName}.sav")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export save backup"))
        } catch (e: Exception) {
            Log.e(TAG, "exportBackup failed", e)
        }
    }

    fun importFromStorage(uri: Uri) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = File(backupDir, "import_$timestamp.sav")
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "importFromStorage failed", e)
        }
    }

    /** Saves a raw byte stream as a timestamped backup. Returns the created file. */
    fun createTimestampedBackup(writeContent: (File) -> Unit): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(backupDir, "backup_$timestamp.sav")
        writeContent(outFile)
        return outFile
    }

    private fun formatDate(epochMs: Long): String =
        SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.US).format(Date(epochMs))
}
