package com.szabolcshorvath.memorymap.backup

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The backup zip format. Every backup ever made contains `database.sqlite` and `metadata.json`,
 * and older ones also `database.sqlite-wal` / `database.sqlite-shm`. These names never change.
 */
internal object BackupZip {
    const val DATABASE = "database.sqlite"
    const val DATABASE_WAL = "database.sqlite-wal"
    const val DATABASE_SHM = "database.sqlite-shm"
    const val METADATA = "metadata.json"

    private val ENTRY_NAMES = setOf(DATABASE, DATABASE_WAL, DATABASE_SHM, METADATA)

    /** Far above any real backup (the database holds no media), but stops a zip bomb from filling the disk. */
    const val MAX_EXTRACTED_BYTES = 1L shl 30 // 1 GiB

    private const val BUFFER_SIZE = 8 * 1024

    /** Writes [files] (entry name to file) into [zipFile]. */
    fun write(zipFile: File, files: Map<String, File>) {
        require(files.keys.all { it in ENTRY_NAMES }) { "Unknown backup entries: ${files.keys - ENTRY_NAMES}" }
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
            files.forEach { (name, file) ->
                out.putNextEntry(ZipEntry(name))
                BufferedInputStream(FileInputStream(file)).use { it.copyTo(out) }
                out.closeEntry()
            }
        }
    }

    /**
     * Extracts the known backup entries of [zipFile] into [targetDir] and returns their names.
     * Anything else (directories, unknown or nested names, `../` paths) is skipped, so nothing can
     * be written outside [targetDir]. Throws if the extracted data exceeds [maxBytes].
     */
    fun extract(zipFile: File, targetDir: File, maxBytes: Long = MAX_EXTRACTED_BYTES): Set<String> {
        val extracted = mutableSetOf<String>()
        var remaining = maxBytes
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            generateSequence { zis.nextEntry }
                .filter { !it.isDirectory && it.name in ENTRY_NAMES }
                .forEach { entry ->
                    FileOutputStream(File(targetDir, entry.name)).use { out ->
                        remaining -= copyAtMost(zis, out, remaining)
                    }
                    extracted += entry.name
                }
        }
        return extracted
    }

    /** Copies [input] to [output]; throws if it is longer than [limit] bytes. Returns the bytes copied. */
    private fun copyAtMost(input: InputStream, output: FileOutputStream, limit: Long): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val count = input.read(buffer)
            if (count == -1) return copied
            copied += count
            if (copied > limit) throw IOException("Backup is too large when extracted")
            output.write(buffer, 0, count)
        }
    }
}
