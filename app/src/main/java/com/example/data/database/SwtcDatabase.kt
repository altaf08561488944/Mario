package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.GameFileDao
import com.example.data.dao.SwtcDao
import com.example.data.entity.BootConfigEntity
import com.example.data.entity.ImportedGameFileEntity
import com.example.data.entity.MyFolderFileEntity
import com.example.data.entity.SaveStateEntity
import com.example.data.entity.VirtualCartridgeEntity

@Database(
    entities = [
        BootConfigEntity::class,
        VirtualCartridgeEntity::class,
        MyFolderFileEntity::class,
        SaveStateEntity::class,
        ImportedGameFileEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SwtcDatabase : RoomDatabase() {

    abstract fun swtcDao(): SwtcDao
    abstract fun gameFileDao(): GameFileDao

    companion object {
        @Volatile
        private var INSTANCE: SwtcDatabase? = null

        fun getDatabase(context: Context): SwtcDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SwtcDatabase::class.java,
                    "swtc_noos_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
