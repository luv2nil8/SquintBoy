package com.anaglych.squintboyadvance.ui.archive

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anaglych.squintboyadvance.data.archive.SaveArchiveStore
import com.anaglych.squintboyadvance.data.sync.SaveSyncSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ArchiveSetupViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SaveSyncSettingsRepository.getInstance(application)

    val settings = repo.settings
    val folderMissing = repo.folderMissing
    val rescanRunning = MutableStateFlow(false)

    fun setEnabled(enabled: Boolean) = repo.update { it.copy(enabled = enabled) }

    fun setFolder(uri: Uri) {
        getApplication<Application>().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        repo.update { it.copy(treeUri = uri.toString()) }
        rescan()
    }

    /** Rebuild the Room index from the folder (relink / fresh-install adoption). */
    fun rescan() {
        viewModelScope.launch(Dispatchers.IO) {
            rescanRunning.value = true
            try {
                SaveArchiveStore(getApplication()).rescan()
            } finally {
                rescanRunning.value = false
            }
        }
    }

    fun setMaxAgeDays(days: Int?) = repo.update { it.copy(maxAgeDays = days) }

    fun setMaxCountPerGame(count: Int?) = repo.update { it.copy(maxCountPerGame = count) }

    fun setThinning(enabled: Boolean) =
        repo.update { it.copy(thinAfterDays = if (enabled) 7 else null) }

    /** Human-readable folder label from the SAF tree URI. */
    fun folderLabel(treeUri: String?): String? {
        if (treeUri == null) return null
        return try {
            Uri.parse(treeUri).lastPathSegment?.substringAfterLast(':')?.ifEmpty { null }
                ?: treeUri
        } catch (_: Exception) {
            treeUri
        }
    }
}
