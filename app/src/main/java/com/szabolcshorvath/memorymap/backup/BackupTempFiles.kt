package com.szabolcshorvath.memorymap.backup

import java.io.File
import java.nio.file.Files

/**
 * Temp files for backup and restore. Every run gets its own directory, so two runs can never
 * read, overwrite or delete each other's files.
 */
internal class BackupTempFiles(private val cacheDir: File) {

    fun newBackupDir(): File = newDir(BACKUP_PREFIX)

    fun newRestoreDir(): File = newDir(RESTORE_PREFIX)

    /**
     * Deletes leftovers of runs that were killed midway, including the fixed paths older app
     * versions used. Call only while holding the backup lock, so no run is using them.
     */
    fun deleteStale() {
        cacheDir.listFiles()
            ?.filter { it.name.startsWith(BACKUP_PREFIX) || it.name.startsWith(RESTORE_PREFIX) || it.name in LEGACY_NAMES }
            ?.forEach { it.deleteRecursively() }
    }

    private fun newDir(prefix: String): File = Files.createTempDirectory(cacheDir.toPath(), prefix).toFile()

    companion object {
        private const val BACKUP_PREFIX = "mm_backup_"
        private const val RESTORE_PREFIX = "mm_restore_"
        private val LEGACY_NAMES = setOf("backup_temp", "backup.zip", "restore_temp.zip", "restore_temp_dir")
    }
}
