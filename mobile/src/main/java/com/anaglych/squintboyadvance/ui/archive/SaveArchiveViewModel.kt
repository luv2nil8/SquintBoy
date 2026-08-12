package com.anaglych.squintboyadvance.ui.archive

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anaglych.squintboyadvance.data.archive.GameDirNaming
import com.anaglych.squintboyadvance.data.archive.SaveArchiveStore
import com.anaglych.squintboyadvance.data.db.ArchivedSaveEntity
import com.anaglych.squintboyadvance.data.db.DriveState
import com.anaglych.squintboyadvance.data.db.LocalState
import com.anaglych.squintboyadvance.data.db.SaveSyncDatabase
import com.anaglych.squintboyadvance.data.sync.SaveSyncSettingsRepository
import com.anaglych.squintboyadvance.data.sync.WatchSavePusher
import com.anaglych.squintboyadvance.shared.protocol.WearMessageConstants
import com.anaglych.squintboyadvance.shared.util.HashUtils
import com.google.android.gms.wearable.Wearable
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ArchiveRestoreState(
    val inProgress: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

class SaveArchiveViewModel(
    application: Application,
    private val romId: String,
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SaveArchiveViewModel"
        private const val MAX_SAVE_BYTES = 1024 * 1024

        fun factory(application: Application, romId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SaveArchiveViewModel(application, romId) as T
            }
    }

    private val dao = SaveSyncDatabase.getInstance(application).archivedSaveDao()
    private val store = SaveArchiveStore(application)
    private val settingsRepo = SaveSyncSettingsRepository.getInstance(application)
    private val channelClient = Wearable.getChannelClient(application)
    private val messageClient = Wearable.getMessageClient(application)
    private val nodeClient = Wearable.getNodeClient(application)

    val syncSettings = settingsRepo.settings
    val folderMissing = settingsRepo.folderMissing

    val saves: StateFlow<List<ArchivedSaveEntity>> = dao.savesForRom(romId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dayCounts: StateFlow<Map<LocalDate, Int>> = dao.dayCounts(romId)
        .map { counts -> counts.associate { dayKeyToDate(it.dayKey) to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Distinct driveError values across the archive; drives the QUOTA/AUTH banner. */
    val driveErrors: StateFlow<List<String>> = dao.distinctDriveErrors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Days containing at least one pinned save — the calendar's pin markers. */
    val pinnedDays: StateFlow<Set<LocalDate>> = dao.savesForRom(romId)
        .map { list -> list.filter { it.pinned }.map { dayKeyToDate(it.dayKey) }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Month of the oldest archived save — the calendar's back-navigation bound. */
    val oldestMonth: StateFlow<YearMonth?> = dao.savesForRom(romId)
        .map { list -> list.minOfOrNull { it.timestampMs }?.let { YearMonth.from(dayKeyToDate(GameDirNaming.dayKey(it))) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The day the list shows — always set, defaults to today. */
    val selectedDay = MutableStateFlow<LocalDate>(LocalDate.now())
    val restoreState = MutableStateFlow(ArchiveRestoreState())

    fun selectDay(day: LocalDate) {
        selectedDay.value = day
    }

    fun togglePin(save: ArchivedSaveEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = save.copy(pinned = !save.pinned)
            dao.update(updated)
            updateManifestEntry(updated)
        }
    }

    fun setNote(save: ArchivedSaveEntity, note: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = save.copy(note = note?.trim()?.ifEmpty { null })
            dao.update(updated)
            updateManifestEntry(updated)
        }
    }

    fun deleteSave(save: ArchivedSaveEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            store.deleteSave(save.gameDirName, save.fileName)
            store.findGameDir(save.gameDirName)?.let { gameDir ->
                store.readManifest(gameDir)?.let { manifest ->
                    store.writeManifest(
                        gameDir,
                        manifest.copy(entries = manifest.entries.filter { it.fileName != save.fileName }),
                    )
                }
            }
            if (save.driveFileId == null) {
                dao.deleteById(save.id)
            } else {
                // Tombstone: the Drive worker propagates the deletion.
                dao.update(
                    save.copy(localState = LocalState.DELETED, driveState = DriveState.DELETE_PENDING)
                )
            }
        }
    }

    /**
     * Sends a user-picked save file to the watch as the live .sav — the manual
     * upload path while sync is on (the legacy backups UI is hidden then).
     * Same pipeline as [restoreToWatch]: validated push, then stack clear.
     */
    fun restoreFromFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            restoreState.value = ArchiveRestoreState(inProgress = true, message = "Sending…")
            try {
                val bytes = getApplication<Application>().contentResolver
                    .openInputStream(uri)?.use { it.readBytes() }
                    ?: throw Exception("Couldn't read the selected file")
                if (bytes.isEmpty()) throw Exception("The selected file is empty")
                // Hard cap matching the watch's push_v2 limit. Enforced here
                // because the legacy fallback has no ack — a watch-side
                // rejection would otherwise be reported as success.
                if (bytes.size > MAX_SAVE_BYTES) {
                    throw Exception("File is too large to be a save (${bytes.size / 1024} KB)")
                }
                val nodeId = nodeClient.connectedNodes.await().firstOrNull()?.id
                    ?: throw Exception("No watch connected")

                WatchSavePusher.push(
                    channelClient, nodeId, romId, bytes, HashUtils.sha256Hex(bytes)
                )

                messageClient.sendMessage(
                    nodeId,
                    WearMessageConstants.PATH_SAVE_CLEAR_STACKS,
                    romId.toByteArray(Charsets.UTF_8),
                ).await()

                restoreState.value = ArchiveRestoreState(message = "Sent to watch")
            } catch (e: Exception) {
                Log.e(TAG, "restoreFromFile failed", e)
                restoreState.value =
                    ArchiveRestoreState(message = e.message ?: "Failed", isError = true)
            }
        }
    }

    /**
     * Sends an archived save back to the watch as its live .sav. Tries the
     * validated push_v2 protocol, falls back to the legacy push for old watch
     * builds, then clears the watch's save/state stacks exactly like the
     * manual upload flow.
     */
    fun restoreToWatch(save: ArchivedSaveEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            restoreState.value = ArchiveRestoreState(inProgress = true, message = "Restoring…")
            try {
                val bytes = store.openSave(save.gameDirName, save.fileName)
                    ?.use { it.readBytes() }
                    ?: throw Exception("Archived file is missing")
                val nodeId = nodeClient.connectedNodes.await().firstOrNull()?.id
                    ?: throw Exception("No watch connected")

                WatchSavePusher.push(channelClient, nodeId, romId, bytes, save.sha256)

                messageClient.sendMessage(
                    nodeId,
                    WearMessageConstants.PATH_SAVE_CLEAR_STACKS,
                    romId.toByteArray(Charsets.UTF_8),
                ).await()

                restoreState.value = ArchiveRestoreState(message = "Restored to watch")
            } catch (e: Exception) {
                Log.e(TAG, "restoreToWatch failed", e)
                restoreState.value =
                    ArchiveRestoreState(message = e.message ?: "Failed", isError = true)
            }
        }
    }

    fun clearRestoreMessage() {
        restoreState.value = ArchiveRestoreState()
    }

    private fun updateManifestEntry(save: ArchivedSaveEntity) {
        val gameDir = store.findGameDir(save.gameDirName) ?: return
        val manifest = store.readManifest(gameDir) ?: return
        store.writeManifest(
            gameDir,
            manifest.copy(
                entries = manifest.entries.map {
                    if (it.fileName == save.fileName) {
                        it.copy(pinned = save.pinned, note = save.note, driveFileId = save.driveFileId)
                    } else it
                }
            ),
        )
    }

    private fun dayKeyToDate(dayKey: Int): LocalDate =
        LocalDate.of(dayKey / 10_000, (dayKey / 100) % 100, dayKey % 100)
}
