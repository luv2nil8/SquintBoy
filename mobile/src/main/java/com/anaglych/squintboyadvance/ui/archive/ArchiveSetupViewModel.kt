package com.anaglych.squintboyadvance.ui.archive

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anaglych.squintboyadvance.data.archive.SaveArchiveStore
import com.anaglych.squintboyadvance.data.cloud.CloudResult
import com.anaglych.squintboyadvance.data.cloud.DriveAuthManager
import com.anaglych.squintboyadvance.data.cloud.DriveAuthResult
import com.anaglych.squintboyadvance.data.cloud.GoogleDriveProvider
import com.anaglych.squintboyadvance.data.db.DriveState
import com.anaglych.squintboyadvance.data.db.SaveSyncDatabase
import com.anaglych.squintboyadvance.data.sync.SaveSyncSettingsRepository
import com.anaglych.squintboyadvance.work.DriveSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ArchiveSetupViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SaveSyncSettingsRepository.getInstance(application)
    private val dao = SaveSyncDatabase.getInstance(application).archivedSaveDao()
    private val auth = DriveAuthManager(application)

    val settings = repo.settings
    val folderMissing = repo.folderMissing
    val rescanRunning = MutableStateFlow(false)

    val driveBusy = MutableStateFlow(false)
    val driveError = MutableStateFlow<String?>(null)

    /** Set when Google needs a consent screen; the UI launches it and calls [onConsentResult]. */
    val driveConsentIntent = MutableStateFlow<PendingIntent?>(null)

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

    // ── Google Drive ────────────────────────────────────────────────────

    fun connectDrive() {
        viewModelScope.launch(Dispatchers.IO) {
            driveBusy.value = true
            driveError.value = null
            when (val result = auth.authorize()) {
                is DriveAuthResult.Token -> completeConnect()
                is DriveAuthResult.NeedsConsent -> {
                    driveConsentIntent.value = result.pendingIntent
                    driveBusy.value = false
                }
                is DriveAuthResult.Failed -> {
                    driveError.value = result.message
                    driveBusy.value = false
                }
            }
        }
    }

    fun onConsentResult(data: Intent?) {
        viewModelScope.launch(Dispatchers.IO) {
            driveConsentIntent.value = null
            driveBusy.value = true
            if (auth.tokenFromConsentResult(data) != null) {
                completeConnect()
            } else {
                driveError.value = "Google Drive access was declined"
                driveBusy.value = false
            }
        }
    }

    fun disconnectDrive() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.update { it.copy(driveEnabled = false, driveAccountEmail = null) }
            // Stop worker churn; remote files are left in place.
            dao.resetDriveStates(DriveState.PENDING, DriveState.NOT_SYNCED)
            dao.resetDriveStates(DriveState.ERROR, DriveState.NOT_SYNCED)
        }
    }

    private suspend fun completeConnect() {
        val provider = GoogleDriveProvider(getApplication(), auth::getAccessTokenSilent)
        val email = (provider.accountEmail() as? CloudResult.Ok)?.value
        repo.update { it.copy(driveEnabled = true, driveAccountEmail = email) }
        // Backfill the whole archive, and clear stale AUTH/QUOTA errors.
        dao.resetDriveStates(DriveState.NOT_SYNCED, DriveState.PENDING)
        dao.resetDriveStates(DriveState.ERROR, DriveState.PENDING)
        DriveSyncWorker.enqueue(getApplication())
        driveBusy.value = false
    }

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
