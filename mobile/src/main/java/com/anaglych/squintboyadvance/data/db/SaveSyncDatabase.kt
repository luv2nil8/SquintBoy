package com.anaglych.squintboyadvance.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ArchivedSaveEntity::class], version = 1, exportSchema = false)
abstract class SaveSyncDatabase : RoomDatabase() {

    abstract fun archivedSaveDao(): ArchivedSaveDao

    companion object {
        @Volatile
        private var instance: SaveSyncDatabase? = null

        fun getInstance(context: Context): SaveSyncDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SaveSyncDatabase::class.java,
                    "save_sync.db",
                )
                    // Destructive is acceptable: the SAF folder's per-game manifests
                    // make the index fully rebuildable via SaveArchiveStore.rescan().
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
