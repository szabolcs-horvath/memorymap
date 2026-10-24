package com.szabolcshorvath.memorymap.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupZipTest {

    @TempDir
    lateinit var tempDir: File

    private val extractDir by lazy { File(tempDir, "extract").apply { mkdirs() } }

    @Test
    fun `written backup extracts to the same files`() {
        val db = File(tempDir, "db").apply { writeText("database bytes") }
        val metadata = File(tempDir, "meta").apply { writeText("{\"version\":2}") }
        val zip = File(tempDir, "backup.zip")

        BackupZip.write(zip, mapOf(BackupZip.DATABASE to db, BackupZip.METADATA to metadata))
        val extracted = BackupZip.extract(zip, extractDir)

        assertEquals(setOf(BackupZip.DATABASE, BackupZip.METADATA), extracted)
        assertEquals("database bytes", File(extractDir, BackupZip.DATABASE).readText())
        assertEquals("{\"version\":2}", File(extractDir, BackupZip.METADATA).readText())
    }

    @Test
    fun `old backups with WAL and SHM extract all four files`() {
        // The layout every backup had before the snapshot change.
        val zip = zipOf(
            "database.sqlite" to "db",
            "metadata.json" to "{}",
            "database.sqlite-wal" to "wal",
            "database.sqlite-shm" to "shm"
        )

        val extracted = BackupZip.extract(zip, extractDir)

        assertEquals(setOf("database.sqlite", "metadata.json", "database.sqlite-wal", "database.sqlite-shm"), extracted)
        assertEquals("wal", File(extractDir, "database.sqlite-wal").readText())
    }

    @Test
    fun `unknown, nested and path traversal entries are skipped`() {
        val zip = zipOf(
            "database.sqlite" to "db",
            "metadata.json" to "{}",
            "../evil.txt" to "evil",
            "../extract_evil/database.sqlite" to "evil",
            "nested/database.sqlite" to "evil",
            "other.txt" to "other"
        )

        val extracted = BackupZip.extract(zip, extractDir)

        assertEquals(setOf("database.sqlite", "metadata.json"), extracted)
        assertEquals("db", File(extractDir, "database.sqlite").readText())
        assertFalse(File(tempDir, "evil.txt").exists())
        assertFalse(File(tempDir, "extract_evil").exists())
        assertEquals(setOf("database.sqlite", "metadata.json"), extractDir.list()!!.toSet())
    }

    @Test
    fun `directory entries are skipped`() {
        val zip = File(tempDir, "dirs.zip")
        ZipOutputStream(FileOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("database.sqlite/"))
            out.closeEntry()
            out.putNextEntry(ZipEntry("metadata.json"))
            out.write("{}".toByteArray())
            out.closeEntry()
        }

        assertEquals(setOf("metadata.json"), BackupZip.extract(zip, extractDir))
    }

    @Test
    fun `extraction stops when the size limit is exceeded`() {
        val zip = zipOf("database.sqlite" to "x".repeat(600), "database.sqlite-wal" to "y".repeat(600))

        assertThrows<IOException> { BackupZip.extract(zip, extractDir, maxBytes = 1000) }
    }

    @Test
    fun `extraction at exactly the size limit succeeds`() {
        val zip = zipOf("database.sqlite" to "x".repeat(500), "metadata.json" to "y".repeat(500))

        assertEquals(2, BackupZip.extract(zip, extractDir, maxBytes = 1000).size)
    }

    @Test
    fun `writing an unknown entry name is refused`() {
        val file = File(tempDir, "f").apply { writeText("x") }
        assertThrows<IllegalArgumentException> { BackupZip.write(File(tempDir, "z.zip"), mapOf("other.txt" to file)) }
    }

    private fun zipOf(vararg entries: Pair<String, String>): File {
        val zip = File(tempDir, "test.zip")
        ZipOutputStream(FileOutputStream(zip)).use { out ->
            entries.forEach { (name, content) ->
                out.putNextEntry(ZipEntry(name))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return zip
    }
}
