package com.szabolcshorvath.memorymap.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BackupTempFilesTest {

    @TempDir
    lateinit var cacheDir: File

    private val tempFiles by lazy { BackupTempFiles(cacheDir) }

    @Test
    fun `every run gets its own directory`() {
        val backup1 = tempFiles.newBackupDir()
        val backup2 = tempFiles.newBackupDir()
        val restore = tempFiles.newRestoreDir()

        assertTrue(backup1.isDirectory && backup2.isDirectory && restore.isDirectory)
        assertEquals(3, setOf(backup1, backup2, restore).size)
        assertEquals(cacheDir, backup1.parentFile)
        assertNotEquals(backup1.name, backup2.name)
    }

    @Test
    fun `deleteStale removes leftover runs and the legacy fixed paths`() {
        File(tempFiles.newBackupDir(), "backup.zip").writeText("zip")
        File(tempFiles.newRestoreDir(), "files").mkdirs()
        File(cacheDir, "backup_temp").apply { mkdirs() }.resolve("metadata.json").writeText("{}")
        File(cacheDir, "backup.zip").writeText("zip")
        File(cacheDir, "restore_temp.zip").writeText("zip")
        File(cacheDir, "restore_temp_dir").mkdirs()

        tempFiles.deleteStale()

        assertEquals(emptyList<String>(), cacheDir.list()!!.toList())
    }

    @Test
    fun `deleteStale leaves other cache files alone`() {
        val logs = File(cacheDir, "debug_logs.zip").apply { writeText("logs") }
        val imageCache = File(cacheDir, "image_cache").apply { mkdirs() }

        tempFiles.deleteStale()

        assertTrue(logs.exists())
        assertTrue(imageCache.exists())
    }

    @Test
    fun `deleteStale on an empty cache does nothing`() {
        tempFiles.deleteStale()
        assertTrue(cacheDir.list()!!.isEmpty())
    }
}
