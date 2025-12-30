package com.szabolcshorvath.memorymap.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [MemoryGroup::class, MediaItem::class], version = 4, exportSchema = true)
@TypeConverters(Converters::class)
abstract class StoryMapDatabase : RoomDatabase() {
    abstract fun memoryGroupDao(): MemoryGroupDao

    companion object {
        @Volatile
        private var INSTANCE: StoryMapDatabase? = null

        fun getDatabase(context: Context): StoryMapDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StoryMapDatabase::class.java,
                    "memory_map_database"
                )
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}