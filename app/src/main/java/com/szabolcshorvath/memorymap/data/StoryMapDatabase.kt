package com.szabolcshorvath.memorymap.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RenameColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MemoryGroup::class, MediaItem::class, MemoryFragment::class, HSVPreset::class],
    version = StoryMapDatabase.DB_VERSION,
    autoMigrations = [
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15, spec = AutoMigrationSpecFrom14To15::class),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17)
    ],
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class StoryMapDatabase : RoomDatabase() {
    abstract fun memoryGroupDao(): MemoryGroupDao
    abstract fun hsvPresetDao(): HSVPresetDao

    companion object {
        const val DB_VERSION = 17

        @Volatile
        private var INSTANCE: StoryMapDatabase? = null

        @Suppress("MagicNumber")
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE MemoryGroup ADD COLUMN startDate TEXT NOT NULL DEFAULT '';")
                db.execSQL("ALTER TABLE MemoryGroup ADD COLUMN endDate TEXT NOT NULL DEFAULT '';")
                db.execSQL("ALTER TABLE MemoryGroup ADD COLUMN isAllDay INTEGER NOT NULL DEFAULT 0;")
                db.execSQL("UPDATE MemoryGroup SET startDate = date, endDate = date;")
                db.execSQL("ALTER TABLE MemoryGroup DROP COLUMN date;")
            }
        }

        @Suppress("MagicNumber")
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE MemoryGroup ADD COLUMN originalFileName TEXT NOT NULL DEFAULT '';")
                db.execSQL("ALTER TABLE MemoryGroup ADD COLUMN fileSize INTEGER NOT NULL DEFAULT 0;")
                db.execSQL("ALTER TABLE MemoryGroup ADD COLUMN dateTaken INTEGER NOT NULL DEFAULT 0;")
                db.execSQL("ALTER TABLE MemoryGroup ADD COLUMN deviceId TEXT NOT NULL DEFAULT '';")
            }
        }

        @Suppress("MagicNumber")
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE MemoryGroup DROP COLUMN originalFileName;")
                db.execSQL("ALTER TABLE MemoryGroup ADD COLUMN mediaSignature TEXT NOT NULL DEFAULT '';")
            }
        }

        fun getDatabase(context: Context): StoryMapDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StoryMapDatabase::class.java,
                    "memory_map_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_4_5)
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

@RenameColumn.Entries(
    RenameColumn(
        tableName = "memory_groups",
        fromColumnName = "markerValue",
        toColumnName = "markerBrightness",
    ),
    RenameColumn(
        tableName = "memory_fragments",
        fromColumnName = "markerValue",
        toColumnName = "markerBrightness",
    )
)
class AutoMigrationSpecFrom14To15 : AutoMigrationSpec
