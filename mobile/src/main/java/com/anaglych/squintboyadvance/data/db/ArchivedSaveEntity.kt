package com.anaglych.squintboyadvance.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object LocalState {
    const val PRESENT = "PRESENT"

    /** Tombstone: local file removed, Drive deletion may still be pending. */
    const val DELETED = "DELETED"
}

object DriveState {
    const val NOT_SYNCED = "NOT_SYNCED"
    const val PENDING = "PENDING"
    const val UPLOADED = "UPLOADED"
    const val DELETE_PENDING = "DELETE_PENDING"
    const val ERROR = "ERROR"
}

@Entity(
    tableName = "archived_saves",
    indices = [
        Index("romId", "timestampMs"),
        Index(value = ["gameDirName", "fileName"], unique = true),
    ],
)
data class ArchivedSaveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val romId: String,
    val gameDirName: String,
    /** Actual SAF file name read back after create (providers may mutate names). */
    val fileName: String,
    val timestampMs: Long,
    /** Local date at capture as yyyymmdd int, e.g. 20260702 — calendar bucketing key. */
    val dayKey: Int,
    val sizeBytes: Long,
    val sha256: String,
    val pinned: Boolean = false,
    val note: String? = null,
    val localState: String = LocalState.PRESENT,
    val driveState: String = DriveState.NOT_SYNCED,
    val driveError: String? = null,
    val driveFileId: String? = null,
)
