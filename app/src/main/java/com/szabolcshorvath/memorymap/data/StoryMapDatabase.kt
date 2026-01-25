package com.szabolcshorvath.memorymap.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [MemoryGroup::class, MediaItem::class],
    version = StoryMapDatabase.DB_VERSION,
    autoMigrations = [AutoMigration(from = 6, to = 8)],
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class StoryMapDatabase : RoomDatabase() {
    abstract fun memoryGroupDao(): MemoryGroupDao

    companion object {
        const val DB_VERSION = 8

        @Volatile
        private var INSTANCE: StoryMapDatabase? = null

        fun getDatabase(context: Context): StoryMapDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StoryMapDatabase::class.java,
                    "memory_map_database"
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeDatabase() {
            synchronized(this) {
                if (INSTANCE?.isOpen == true) {
                    INSTANCE?.close()
                }
                INSTANCE = null
            }
        }
    }
}