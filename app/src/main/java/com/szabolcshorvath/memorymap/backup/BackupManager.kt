package com.szabolcshorvath.memorymap.backup

import android.content.Context
import android.util.Log
import com.google.firebase.perf.metrics.AddTrace
import com.szabolcshorvath.memorymap.AppServices
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.data.AppPreferences
import com.szabolcshorvath.memorymap.data.DatabaseContents
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.BACKUP_FILE_NAME_DATE_FORMATTER
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.BACKUP_METADATA_DATE_FORMATTER
import com.szabolcshorvath.memorymap.util.PerfUtil.trace
import com.szabolcshorvath.memorymap.util.PerfUtil.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDateTime
import com.google.api.services.drive.model.File as DriveFile

/** Backs up the database [db] to the signed-in account's Drive and restores it from there. Built once, by `AppContainer`. */
class BackupManager(private val context: Context, private val db: MemoryMapDatabase, private val preferences: AppPreferences) {

    private val tempFiles = BackupTempFiles(context.cacheDir)

    /**
     * Backs up if a user is signed in. Only [AutomaticBackupWorker] calls this; everything else
     * schedules a backup with [AutomaticBackups.schedule].
     */
    suspend fun runAutomaticBackup(): AutomaticBackupOutcome = withContext(Dispatchers.IO) {
        trace("backup_manager_run_automatic_backup") {
            val email = preferences.email.first()
                ?: return@trace AutomaticBackupOutcome.NOT_SIGNED_IN
            performBackup(email, isAutomatic = true) { Log.d(TAG, "Automatic backup progress: $it") }.fold(
                onSuccess = { AutomaticBackupOutcome.SUCCEEDED },
                onFailure = { e -> if (DriveProblem.of(e).isRetryable) AutomaticBackupOutcome.RETRY else AutomaticBackupOutcome.FAILED }
            )
        }
    }

    suspend fun performBackup(email: String, isAutomatic: Boolean = false, onProgress: (String) -> Unit): Result<Unit> =
        locked("backup_manager_perform_backup", tempFiles::newBackupDir, onProgress, failure = { "Backup failed" }) { tempDir ->
            onProgress(context.getString(R.string.backup_preparing))
            prepareDatabaseForBackup()

            val snapshotDir = File(tempDir, "snapshot").apply { mkdirs() }
            val snapshot = snapshotDatabase(snapshotDir)

            onProgress(context.getString(R.string.backup_checking))
            verifyDatabaseCopy(snapshot)

            val metadataFile = createMetadataFile(snapshot, isAutomatic, tempDir)

            onProgress(context.getString(R.string.backup_compressing))
            val zipFile = zipBackup(snapshotDir, metadataFile, File(tempDir, "backup.zip"))

            onProgress(context.getString(R.string.backup_uploading))
            uploadBackup(email, isAutomatic, zipFile)
        }

    /**
     * Runs [block] with a temp directory of its own ([newDir]) while holding the backup lock, in a trace named
     * [traceName]. Says on [onProgress] when it has to wait for another run first. A failure is logged ([failure] says
     * what failed) and returned; the directory is deleted either way.
     */
    private suspend fun locked(
        traceName: String,
        newDir: () -> File,
        onProgress: (String) -> Unit,
        failure: (Exception) -> String,
        block: suspend (tempDir: File) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (backupLock.isLocked) onProgress(context.getString(R.string.backup_waiting))
        backupLock.withLock {
            trace(traceName) {
                var tempDir: File? = null
                try {
                    tempFiles.deleteStale()
                    tempDir = newDir()
                    block(tempDir)
                    Result.success(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, failure(e), e)
                    Result.failure(e)
                } finally {
                    tempDir?.deleteRecursively()
                }
            }
        }
    }

    @AddTrace(name = "backup_manager_prepare_database_for_backup", enabled = true)
    private fun prepareDatabaseForBackup() {
        db.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(TRUNCATE)")
            .close()
    }

    /**
     * Copies the live database files into [targetDir]. A write transaction is held during the copy:
     * it blocks every other writer, and with it every checkpoint, so the main file and the WAL are
     * copied in one consistent state. The shared-memory file is not needed; SQLite rebuilds it.
     */
    private suspend fun snapshotDatabase(targetDir: File): File {
        val dbFile = context.getDatabasePath(MemoryMapDatabase.DB_NAME)
        if (!dbFile.exists()) {
            throw FileNotFoundException("Database not found")
        }
        val walFile = context.getDatabasePath("${MemoryMapDatabase.DB_NAME}-wal")
        val snapshot = File(targetDir, BackupZip.DATABASE)
        db.withTransaction("backup_manager_snapshot_database") {
            dbFile.copyTo(snapshot)
            if (walFile.exists() && walFile.length() > 0) {
                walFile.copyTo(File(targetDir, BackupZip.DATABASE_WAL))
            }
        }
        return snapshot
    }

    /**
     * Opens the snapshot the way a restore will (same migrations, schema and integrity checks), so a
     * backup that couldn't be restored is never uploaded.
     */
    private suspend fun verifyDatabaseCopy(dbFile: File) {
        trace("backup_manager_verify_database_copy") {
            MemoryMapDatabase.withCheckedCopy(context, dbFile.absolutePath) { }
        }
    }

    @AddTrace(name = "backup_manager_create_metadata_file", enabled = true)
    private fun createMetadataFile(dbFile: File, isAutomatic: Boolean, tempDir: File): File {
        val metadata = JSONObject()
        metadata.put("timestamp", System.currentTimeMillis())
        metadata.put("date", LocalDateTime.now().format(BACKUP_METADATA_DATE_FORMATTER))
        metadata.put("dbSize", dbFile.length())
        metadata.put("version", 2) // Metadata version
        metadata.put("dbVersion", MemoryMapDatabase.DB_VERSION)
        metadata.put("isAutomatic", isAutomatic)

        val metadataFile = File(tempDir, BackupZip.METADATA)
        metadataFile.writeText(metadata.toString())
        return metadataFile
    }

    @AddTrace(name = "backup_manager_zip_backup", enabled = true)
    private fun zipBackup(snapshotDir: File, metadataFile: File, zipFile: File): File {
        // Closing the verified copy normally merges the WAL into the main file; include it if it is still there.
        val entries = listOf(BackupZip.DATABASE, BackupZip.DATABASE_WAL)
            .associateWith { File(snapshotDir, it) }
            .filterValues { it.exists() } + (BackupZip.METADATA to metadataFile)
        BackupZip.write(zipFile, entries)
        return zipFile
    }

    @AddTrace(name = "backup_manager_upload_backup", enabled = true)
    private fun uploadBackup(email: String, isAutomatic: Boolean, zipFile: File) {
        val prefix = if (isAutomatic) AUTOMATIC_BACKUP_PREFIX else MANUAL_BACKUP_PREFIX
        val name = "$prefix${LocalDateTime.now().format(BACKUP_FILE_NAME_DATE_FORMATTER)}.zip"
        AppServices.backupStorage.upload(context, email, name, zipFile)
    }

    /**
     * Every backup of [email], newest first, or the failure ([DriveProblem.of] tells what kind). A failure is never an
     * empty list: that would look as if the backups were gone.
     */
    suspend fun listBackups(email: String): Result<List<DriveFile>> =
        driveCall("backup_manager_list_backups", "Failed to list backups") { AppServices.backupStorage.list(context, email) }

    /**
     * Replaces the contents of [db] with the backup [fileId]. The backup is migrated and checked on
     * a temp copy first, then its rows are copied into [db] in one transaction: if anything fails,
     * the current data is left untouched, and [db] stays open throughout.
     */
    suspend fun restoreBackup(email: String, fileId: String, onProgress: (String) -> Unit): Result<Unit> =
        locked("backup_manager_restore_backup", tempFiles::newRestoreDir, onProgress, failure = { e -> "Restore failed" + versionsOf(e) }) { tempDir ->
            onProgress(context.getString(R.string.restore_downloading))
            val zipFile = downloadBackup(email, fileId, File(tempDir, "backup.zip"))
            val extractDir = File(tempDir, "files").apply { mkdirs() }

            onProgress(context.getString(R.string.restore_uncompressing))
            unzipBackup(zipFile, extractDir)

            onProgress(context.getString(R.string.restore_verifying))
            verifyBackupMetadata(extractDir)

            onProgress(context.getString(R.string.backup_checking))
            val contents = readBackupContents(File(extractDir, BackupZip.DATABASE))

            onProgress(context.getString(R.string.restore_restoring))
            trace("backup_manager_replace_database_contents") {
                contents.replaceAllIn(db.databaseContentsDao())
            }
        }

    @AddTrace(name = "backup_manager_download_backup", enabled = true)
    private fun downloadBackup(email: String, fileId: String, zipFile: File): File {
        AppServices.backupStorage.download(context, email, fileId, zipFile)
        return zipFile
    }

    @AddTrace(name = "backup_manager_unzip_backup", enabled = true)
    private fun unzipBackup(zipFile: File, extractDir: File) {
        BackupZip.extract(zipFile, extractDir)
        // A stale shared-memory file is never needed (SQLite rebuilds it from the WAL), so don't trust it.
        File(extractDir, BackupZip.DATABASE_SHM).delete()
    }

    @AddTrace(name = "backup_manager_verify_backup_metadata", enabled = true)
    private fun verifyBackupMetadata(extractDir: File) {
        requireBackupFiles(extractDir)
        val metadataFile = File(extractDir, BackupZip.METADATA)
        val dbFile = File(extractDir, BackupZip.DATABASE)

        val headerVersion = BackupCompatibility.readUserVersion(dbFile)
        val metadataVersion = readMetadataDbVersion(metadataFile)
        Log.d(TAG, "Backup DB version: header=$headerVersion, metadata=$metadataVersion, app=${MemoryMapDatabase.DB_VERSION}")
        val newerVersion = BackupCompatibility.newerBackupVersion(headerVersion, metadataVersion, MemoryMapDatabase.DB_VERSION)
        if (newerVersion != null) {
            throw BackupTooNewException(newerVersion, context.getString(R.string.restore_error_backup_too_new))
        }
    }

    private fun requireBackupFiles(extractDir: File) {
        if (!File(extractDir, BackupZip.METADATA).exists()) {
            throw FileNotFoundException("Invalid backup: missing metadata")
        }
        if (!File(extractDir, BackupZip.DATABASE).isFile) {
            // Opening a missing file would create an empty database and wipe everything on restore.
            throw FileNotFoundException("Invalid backup: missing database")
        }
    }

    /** `dbVersion` was added in metadata version 2; older backups don't have it. */
    private fun readMetadataDbVersion(metadataFile: File): Int? = runCatching {
        val metadata = JSONObject(metadataFile.readText())
        if (metadata.has("dbVersion")) metadata.getInt("dbVersion") else null
    }.getOrNull()

    /**
     * Opens the extracted backup with the app's own database configuration, which runs the same
     * migrations and schema checks as the app, checks its integrity and reads every row.
     */
    private suspend fun readBackupContents(dbFile: File): DatabaseContents = try {
        trace("backup_manager_read_backup_contents") {
            MemoryMapDatabase.withCheckedCopy(context, dbFile.absolutePath) { copy ->
                DatabaseContents.readFrom(copy.databaseContentsDao())
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
        throw UnreadableBackupException(context.getString(R.string.restore_error_backup_unreadable), e)
    }

    suspend fun deleteBackup(email: String, fileId: String): Result<Unit> =
        driveCall("backup_manager_delete_backup", "Backup deletion failed") { AppServices.backupStorage.delete(context, email, fileId) }

    /** Runs a single Drive [call] off the main thread in a trace named [traceName]; a failure is logged ([failure]) and returned. */
    private suspend fun <T> driveCall(traceName: String, failure: String, call: () -> T): Result<T> = withContext(Dispatchers.IO) {
        trace(traceName) {
            try {
                Result.success(call())
            } catch (e: IOException) {
                Log.e(TAG, failure, e)
                Result.failure(e)
            }
        }
    }

    companion object {
        const val TAG = "BackupManager"

        /** How the backup files' names start, by the kind of backup. Stored in Drive: never change them. */
        internal const val AUTOMATIC_BACKUP_PREFIX = "MemoryMap_Automatic_Backup_"
        internal const val MANUAL_BACKUP_PREFIX = "MemoryMap_Manual_Backup_"

        /** Process-wide: only one backup or restore may run at a time. */
        private val backupLock = Mutex()

        /** For the log: the versions of a backup too new to restore. */
        private fun versionsOf(e: Exception): String = (e as? BackupTooNewException)
            ?.let { " (backup database version ${it.backupVersion}, app ${MemoryMapDatabase.DB_VERSION})" }
            .orEmpty()
    }
}
