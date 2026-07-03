package com.anaglych.squintboyadvance.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class DayCount(val dayKey: Int, val count: Int)

@Dao
interface ArchivedSaveDao {

    @Query(
        "SELECT * FROM archived_saves WHERE romId = :romId AND localState = 'PRESENT' " +
            "ORDER BY timestampMs DESC"
    )
    fun savesForRom(romId: String): Flow<List<ArchivedSaveEntity>>

    @Query(
        "SELECT * FROM archived_saves WHERE romId = :romId AND localState = 'PRESENT' " +
            "ORDER BY timestampMs DESC"
    )
    suspend fun savesForRomOnce(romId: String): List<ArchivedSaveEntity>

    @Query(
        "SELECT * FROM archived_saves WHERE romId = :romId AND dayKey = :dayKey " +
            "AND localState = 'PRESENT' ORDER BY timestampMs DESC"
    )
    suspend fun savesForDay(romId: String, dayKey: Int): List<ArchivedSaveEntity>

    @Query(
        "SELECT dayKey, COUNT(*) as count FROM archived_saves WHERE romId = :romId " +
            "AND localState = 'PRESENT' GROUP BY dayKey"
    )
    fun dayCounts(romId: String): Flow<List<DayCount>>

    @Query(
        "SELECT * FROM archived_saves WHERE romId = :romId AND localState = 'PRESENT' " +
            "ORDER BY timestampMs DESC LIMIT 1"
    )
    suspend fun newestForRom(romId: String): ArchivedSaveEntity?

    @Query(
        "SELECT * FROM archived_saves WHERE driveState IN ('PENDING', 'DELETE_PENDING')"
    )
    suspend fun pendingDriveWork(): List<ArchivedSaveEntity>

    @Query(
        "SELECT * FROM archived_saves WHERE romId = :romId AND sha256 = :sha256 " +
            "AND timestampMs = :timestampMs LIMIT 1"
    )
    suspend fun findDuplicate(romId: String, sha256: String, timestampMs: Long): ArchivedSaveEntity?

    @Query("SELECT * FROM archived_saves WHERE gameDirName = :gameDirName AND fileName = :fileName LIMIT 1")
    suspend fun findByFile(gameDirName: String, fileName: String): ArchivedSaveEntity?

    @Query("SELECT * FROM archived_saves WHERE id = :id")
    suspend fun byId(id: Long): ArchivedSaveEntity?

    @Insert
    suspend fun insert(entity: ArchivedSaveEntity): Long

    @Update
    suspend fun update(entity: ArchivedSaveEntity)

    @Query("DELETE FROM archived_saves WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM archived_saves")
    suspend fun deleteAll()
}
