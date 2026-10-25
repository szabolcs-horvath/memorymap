package com.szabolcshorvath.memorymap.fakes

import android.content.Context
import com.google.api.client.util.DateTime
import com.szabolcshorvath.memorymap.backup.BackupStorage
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicInteger
import com.google.api.services.drive.model.File as DriveFile

/**
 * Backups kept in memory instead of on Google Drive. Uploads from the app land here too, so a test can back up
 * and then restore. The test orchestrator starts a new process for every test, so every test starts empty.
 */
object FakeBackupStorage : BackupStorage {
    private class StoredBackup(val file: DriveFile, val content: ByteArray?)

    private val backups = mutableMapOf<String, MutableList<StoredBackup>>()
    private val nextId = AtomicInteger(1)

    /** Adds a backup for [email]. Without [content], restoring it fails like a broken download would. */
    @Synchronized
    fun add(email: String, name: String, modifiedMillis: Long, content: ByteArray? = null): DriveFile {
        val file = DriveFile()
            .setId("fake-backup-${nextId.getAndIncrement()}")
            .setName(name)
            .setModifiedTime(DateTime(modifiedMillis))
            .setSize(content?.size?.toLong() ?: 0L)
        backups.getOrPut(email) { mutableListOf() } += StoredBackup(file, content)
        return file
    }

    @Synchronized
    override fun list(context: Context, email: String): List<DriveFile> =
        backups[email].orEmpty().map { it.file }.sortedByDescending { it.modifiedTime.value }

    @Synchronized
    override fun upload(context: Context, email: String, name: String, zipFile: File) {
        add(email, name, System.currentTimeMillis(), zipFile.readBytes())
    }

    @Synchronized
    override fun download(context: Context, email: String, fileId: String, target: File) {
        val content = find(email, fileId).content ?: throw FileNotFoundException("Fake backup $fileId has no content")
        target.writeBytes(content)
    }

    @Synchronized
    override fun delete(context: Context, email: String, fileId: String) {
        backups[email]?.remove(find(email, fileId))
    }

    private fun find(email: String, fileId: String): StoredBackup =
        backups[email]?.firstOrNull { it.file.id == fileId } ?: throw FileNotFoundException("No fake backup $fileId")
}
