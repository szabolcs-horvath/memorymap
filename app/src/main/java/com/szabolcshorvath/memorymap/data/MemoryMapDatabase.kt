package com.szabolcshorvath.memorymap.data

import android.content.Context
import android.database.sqlite.SQLiteException
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
    abstract fun memoryGroupDao(): MemoryGroupDao

    abstract fun hsvPresetDao(): HSVPresetDao

    abstract fun databaseContentsDao(): DatabaseContentsDao

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

        /** File name of the app's database in the databases directory. */
        const val DB_NAME = "memory_map_database"

        private const val MAX_REPORTED_PROBLEMS = 5

        fun getDatabase(context: Context): MemoryMapDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context, DB_NAME).also { INSTANCE = it }
            }
        }

        private fun build(context: Context, name: String, callback: Callback? = null): MemoryMapDatabase =
            Room.databaseBuilder(context.applicationContext, MemoryMapDatabase::class.java, name)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_4_5,
                    MIGRATION_17_18
                )
                // No destructive fallback: a missing migration must fail loudly instead of
                // silently dropping the user's data. Restores of newer backups are rejected
                // up front by BackupCompatibility.
                .apply { callback?.let { addCallback(it) } }
                .build()

        /**
         * Opens a copy of the database (a backup) at [path] with the app's full configuration, so it
         * is migrated and schema-checked exactly as the app would do it. Checks its integrity, runs
         * [block] on it and closes it.
         *
         * Throws if SQLite reports corruption, or if the file wasn't an existing database: Android
         * deletes a file it can't open and SQLite then creates an empty one, which must never be
         * mistaken for a backup without memories.
         */
        suspend fun <T> withCheckedCopy(context: Context, path: String, block: suspend (MemoryMapDatabase) -> T): T {
            var createdFromScratch = false
            val copy = build(
                context,
                path,
                object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createdFromScratch = true
                    }
                }
            )
            try {
                copy.requireIntact()
                if (createdFromScratch) throw SQLiteException("Not an existing database: $path")
                return block(copy)
            } finally {
                copy.close()
            }
        }
    }

    /**
     * Opens the database (running any migrations) and throws if SQLite finds corruption.
     */
    fun requireIntact() {
        val problems = openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        if (problems != listOf("ok")) {
            throw SQLiteException("Database integrity check failed: ${problems.take(MAX_REPORTED_PROBLEMS)}")
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
