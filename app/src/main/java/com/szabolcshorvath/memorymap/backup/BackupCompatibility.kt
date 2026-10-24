package com.szabolcshorvath.memorymap.backup

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Decides whether a downloaded backup can be restored by this app version.
 *
 * Only a backup whose database is known to be newer than the app's schema is rejected: Room can't
 * migrate a database down and would refuse to open it. Every other backup, including one whose
 * version can't be read, is restored as before, so no existing backup is ever blocked.
 */
internal object BackupCompatibility {
    private const val SQLITE_HEADER_SIZE = 100
    private const val USER_VERSION_OFFSET = 60
    private const val USER_VERSION_SIZE = 4
    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /**
     * Reads the schema version (`PRAGMA user_version`, which Room sets to the database version)
     * straight from the SQLite file header, without opening or modifying the file.
     * Returns null if the file is missing or is not a SQLite database.
     */
    fun readUserVersion(dbFile: File): Int? {
        val header = readHeader(dbFile) ?: return null
        val isSqlite = header.copyOfRange(0, SQLITE_MAGIC.size).contentEquals(SQLITE_MAGIC)
        // SQLite stores header integers big-endian, which is ByteBuffer's default.
        return if (isSqlite) ByteBuffer.wrap(header, USER_VERSION_OFFSET, USER_VERSION_SIZE).int else null
    }

    private fun readHeader(dbFile: File): ByteArray? {
        if (!dbFile.isFile || dbFile.length() < SQLITE_HEADER_SIZE) return null
        return runCatching {
            ByteArray(SQLITE_HEADER_SIZE).also { bytes -> RandomAccessFile(dbFile, "r").use { it.readFully(bytes) } }
        }.getOrNull()
    }

    /**
     * Returns the backup's database version if it is newer than [appVersion], otherwise null.
     *
     * [headerVersion] comes from the database file. [metadataVersion] comes from `metadata.json`
     * and is missing in the oldest backups. The higher of the two is used, because the header can
     * lag behind if the newest schema change was still in the backup's WAL file.
     */
    fun newerBackupVersion(headerVersion: Int?, metadataVersion: Int?, appVersion: Int): Int? {
        val backupVersion = listOfNotNull(headerVersion, metadataVersion).maxOrNull() ?: return null
        return backupVersion.takeIf { it > appVersion }
    }
}

/** Thrown when a backup was made by a newer app version whose database this version can't open. */
class BackupTooNewException(val backupVersion: Int, message: String) : IOException(message)
