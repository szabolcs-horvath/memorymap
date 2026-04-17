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
import com.szabolcshorvath.memorymap.util.PerfUtil.tracedDao

@Database(
    entities = [MemoryGroup::class, MediaItem::class, MemoryFragment::class, HSVPreset::class],
    version = MemoryMapDatabase.DB_VERSION,
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
abstract class MemoryMapDatabase : RoomDatabase() {
    protected abstract fun memoryGroupDaoInternal(): MemoryGroupDao
    protected abstract fun hsvPresetDaoInternal(): HSVPresetDao

    private var _memoryGroupDao: MemoryGroupDao? = null
    private var _hsvPresetDao: HSVPresetDao? = null

    fun memoryGroupDao(): MemoryGroupDao = _memoryGroupDao ?: tracedDao(memoryGroupDaoInternal()).also { _memoryGroupDao = it }

    fun hsvPresetDao(): HSVPresetDao = _hsvPresetDao ?: tracedDao(hsvPresetDaoInternal()).also { _hsvPresetDao = it }

    companion object {
        const val DB_VERSION = 18

        @Volatile
        private var INSTANCE: MemoryMapDatabase? = null

        @Suppress("MagicNumber")
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_groups ADD COLUMN startDate TEXT NOT NULL DEFAULT '';")
                db.execSQL("ALTER TABLE memory_groups ADD COLUMN endDate TEXT NOT NULL DEFAULT '';")
                db.execSQL("ALTER TABLE memory_groups ADD COLUMN isAllDay INTEGER NOT NULL DEFAULT 0;")
                db.execSQL("UPDATE memory_groups SET startDate = date, endDate = date;")
                db.execSQL("ALTER TABLE memory_groups DROP COLUMN date;")
            }
        }

        @Suppress("MagicNumber")
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_groups ADD COLUMN originalFileName TEXT NOT NULL DEFAULT '';")
                db.execSQL("ALTER TABLE memory_groups ADD COLUMN fileSize INTEGER NOT NULL DEFAULT 0;")
                db.execSQL("ALTER TABLE memory_groups ADD COLUMN dateTaken INTEGER NOT NULL DEFAULT 0;")
                db.execSQL("ALTER TABLE memory_groups ADD COLUMN deviceId TEXT NOT NULL DEFAULT '';")
            }
        }

        @Suppress("MagicNumber")
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_groups DROP COLUMN originalFileName;")
                db.execSQL("ALTER TABLE memory_groups ADD COLUMN mediaSignature TEXT NOT NULL DEFAULT '';")
            }
        }

        @Suppress("MagicNumber")
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE memory_groups SET placeName = REPLACE(REPLACE(REPLACE(placeName, CHAR(13) || CHAR(10), ' '), CHAR(13), ' '), CHAR(10), ' ');"
                )
                db.execSQL(
                    "UPDATE memory_fragments SET placeName = REPLACE(REPLACE(REPLACE(placeName, CHAR(13) || CHAR(10), ' '), CHAR(13), ' '), CHAR(10), ' ');"
                )
            }
        }

        fun getDatabase(context: Context): MemoryMapDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(context.applicationContext, MemoryMapDatabase::class.java, "memory_map_database")
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_4_5,
                        MIGRATION_17_18
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
