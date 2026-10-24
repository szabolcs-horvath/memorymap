package com.szabolcshorvath.memorymap.backup

import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer

class BackupCompatibilityTest {

    @TempDir
    lateinit var tempDir: File

    // --- readUserVersion ---

    @Test
    fun `reads the version from a database written by SQLite`() {
        // Created with: sqlite3 user_version_18.db "PRAGMA journal_mode=WAL; CREATE TABLE ...; PRAGMA user_version=18;"
        val fixture = File(checkNotNull(javaClass.getResource("/backup/user_version_18.db")).toURI())
        assertEquals(18, BackupCompatibility.readUserVersion(fixture))
    }

    @Test
    fun `reads the version from a synthetic header`() {
        assertEquals(42, BackupCompatibility.readUserVersion(sqliteFile(userVersion = 42)))
    }

    @Test
    fun `missing file has no version`() {
        assertNull(BackupCompatibility.readUserVersion(File(tempDir, "database.sqlite")))
    }

    @Test
    fun `truncated file has no version`() {
        val file = File(tempDir, "database.sqlite").apply { writeBytes(ByteArray(50)) }
        assertNull(BackupCompatibility.readUserVersion(file))
    }

    @Test
    fun `non-SQLite file has no version`() {
        val file = File(tempDir, "database.sqlite").apply { writeBytes(ByteArray(4096) { 'x'.code.toByte() }) }
        assertNull(BackupCompatibility.readUserVersion(file))
    }

    // --- newerBackupVersion: existing backups must always stay restorable ---

    @Test
    fun `every backup an existing app version can produce is restorable`() {
        for (version in 1..MemoryMapDatabase.DB_VERSION) {
            // Metadata version 2 records dbVersion, matching the file.
            assertNull(BackupCompatibility.newerBackupVersion(version, version, MemoryMapDatabase.DB_VERSION), "v$version")
            // Metadata version 1 (before January 25, 2026) has no dbVersion.
            assertNull(BackupCompatibility.newerBackupVersion(version, null, MemoryMapDatabase.DB_VERSION), "v$version, metadata v1")
        }
    }

    @Test
    fun `backup with an unreadable version is restorable`() {
        assertNull(BackupCompatibility.newerBackupVersion(null, null, APP_VERSION))
        assertNull(BackupCompatibility.newerBackupVersion(null, APP_VERSION, APP_VERSION))
    }

    @Test
    fun `older backup is restorable`() {
        assertNull(BackupCompatibility.newerBackupVersion(7, null, APP_VERSION))
    }

    // --- newerBackupVersion: rejections ---

    @Test
    fun `newer database file is rejected`() {
        assertEquals(19, BackupCompatibility.newerBackupVersion(19, null, APP_VERSION))
    }

    @Test
    fun `newer metadata is rejected even if the file header lags behind`() {
        // The newest schema change can still be in the backup's WAL file.
        assertEquals(19, BackupCompatibility.newerBackupVersion(18, 19, APP_VERSION))
    }

    @Test
    fun `newer metadata is rejected when the file header is unreadable`() {
        assertEquals(20, BackupCompatibility.newerBackupVersion(null, 20, APP_VERSION))
    }

    private fun sqliteFile(userVersion: Int): File {
        val bytes = ByteArray(4096)
        "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII).copyInto(bytes)
        ByteBuffer.wrap(bytes).putInt(60, userVersion)
        return File(tempDir, "database.sqlite").apply { writeBytes(bytes) }
    }

    private companion object {
        const val APP_VERSION = 18
    }
}
