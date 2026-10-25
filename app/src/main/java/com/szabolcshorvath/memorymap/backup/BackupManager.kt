package com.szabolcshorvath.memorymap.backup

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.firebase.perf.metrics.AddTrace
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.auth.GoogleAuthManager
import com.szabolcshorvath.memorymap.data.DatabaseContents
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import com.szabolcshorvath.memorymap.dataStore
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.BACKUP_FILE_NAME_DATE_FORMATTER
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.BACKUP_METADATA_DATE_FORMATTER
import com.szabolcshorvath.memorymap.util.PerfUtil.trace
import com.szabolcshorvath.memorymap.util.PerfUtil.withTransaction
import com.szabolcshorvath.memorymap.util.PreferencesKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDateTime
import com.google.api.services.drive.model.File as DriveFile

class BackupManager(private val context: Context) {

    enum class BackupEvent { STARTED, FINISHED }

    private val tempFiles = BackupTempFiles(context.cacheDir)

    fun getDriveService(credential: GoogleAccountCredential): Drive =
        Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Memory Map").build()

    suspend fun triggerAutomaticBackup(db: MemoryMapDatabase) {
        withContext(Dispatchers.IO) {
            trace("backup_manager_trigger_automatic_backup") {
                try {
                    val email = context.dataStore.data.map { it[PreferencesKeys.USER_EMAIL_KEY] }.firstOrNull() ?: return@withContext
                    _backupEvents.emit(BackupEvent.STARTED)
                    val googleAuthManager = GoogleAuthManager(context)
                    val scopes = listOf(DriveScopes.DRIVE_FILE)
                    val credential = googleAuthManager.getGoogleAccountCredential(email, scopes)
                    performBackup(credential, db, isAutomatic = true) {
                        Log.d(TAG, "Automatic backup progress: $it")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to trigger automatic backup", e)
                } finally {
                    _backupEvents.emit(BackupEvent.FINISHED)
                }
            }
        }
    }

    suspend fun performBackup(
        credential: GoogleAccountCredential,
        db: MemoryMapDatabase,
        isAutomatic: Boolean = false,
        onProgress: (String) -> Unit
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            if (backupLock.isLocked) onProgress("Waiting for the running backup to finish...")
            backupLock.withLock {
                trace("backup_manager_perform_backup") {
                    var tempDir: File? = null
                    try {
                        tempFiles.deleteStale()

                        onProgress("Preparing database...")
                        prepareDatabaseForBackup(db)

                        tempDir = tempFiles.newBackupDir()
                        val snapshotDir = File(tempDir, "snapshot").apply { mkdirs() }
                        val snapshot = snapshotDatabase(db, snapshotDir)

                        onProgress("Checking backup...")
                        verifyDatabaseCopy(snapshot)

                        val metadataFile = createMetadataFile(snapshot, isAutomatic, tempDir)

                        onProgress("Compressing data...")
                        val zipFile = zipBackup(snapshotDir, metadataFile, File(tempDir, "backup.zip"))

                        onProgress("Uploading backup...")
                        uploadBackup(credential, isAutomatic, zipFile)

                        Result.success(Unit)
                    } catch (e: Exception) {
                        Log.e(TAG, "Backup failed", e)
                        Result.failure(e)
                    } finally {
                        tempDir?.deleteRecursively()
                    }
                }
            }
        }
    }

    @AddTrace(name = "backup_manager_prepare_database_for_backup", enabled = true)
    private fun prepareDatabaseForBackup(db: MemoryMapDatabase) {
        try {
            db.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .close()
        } catch (e: Exception) {
            when (e) {
                is IllegalStateException,
                is SQLiteException -> Log.e(TAG, "Failed to checkpoint WAL", e)
            }
            throw e
        }
    }

    /**
     * Copies the live database files into [targetDir]. A write transaction is held during the copy:
     * it blocks every other writer, and with it every checkpoint, so the main file and the WAL are
     * copied in one consistent state. The shared-memory file is not needed; SQLite rebuilds it.
     */
    private suspend fun snapshotDatabase(db: MemoryMapDatabase, targetDir: File): File {
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
    private fun uploadBackup(credential: GoogleAccountCredential, isAutomatic: Boolean, zipFile: File) {
        val driveService = getDriveService(credential)
        val folderId = getOrCreateBackupFolder(driveService)
        val fileMetadata = DriveFile()
        val prefix = if (isAutomatic) "MemoryMap_Automatic_Backup_" else "MemoryMap_Manual_Backup_"
        fileMetadata.name = "$prefix${LocalDateTime.now().format(BACKUP_FILE_NAME_DATE_FORMATTER)}.zip"
        fileMetadata.parents = listOf(folderId)

        val mediaContent = FileContent("application/zip", zipFile)
        driveService.files().create(fileMetadata, mediaContent)
            .setFields("id")
            .execute()
    }

    suspend fun listBackups(credential: GoogleAccountCredential): List<DriveFile> {
        return withContext(Dispatchers.IO) {
            trace("backup_manager_list_backups") {
                try {
                    val driveService = getDriveService(credential)
                    val folderId = getOrCreateBackupFolder(driveService)
                    val query = "'$folderId' in parents and trashed = false"
                    return@withContext collectAllPages { pageToken ->
                        val result = driveService.files().list()
                            .setQ(query)
                            .setFields("nextPageToken, files(id, name, modifiedTime, size)")
                            .setOrderBy("modifiedTime desc")
                            .setPageSize(DRIVE_LIST_PAGE_SIZE)
                            .setPageToken(pageToken)
                            .execute()
                        (result.files ?: emptyList()) to result.nextPageToken
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to list backups", e)
                    return@withContext emptyList()
                }
            }
        }
    }

    /**
     * Replaces the contents of [db] with the backup [fileId]. The backup is migrated and checked on
     * a temp copy first, then its rows are copied into [db] in one transaction: if anything fails,
     * the current data is left untouched, and [db] stays open throughout.
     */
    suspend fun restoreBackup(
        credential: GoogleAccountCredential,
        fileId: String,
        db: MemoryMapDatabase,
        onProgress: (String) -> Unit
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            if (backupLock.isLocked) onProgress("Waiting for the running backup to finish...")
            backupLock.withLock {
                trace("backup_manager_restore_backup") {
                    var tempDir: File? = null
                    try {
                        tempFiles.deleteStale()
                        tempDir = tempFiles.newRestoreDir()
                        val driveService = getDriveService(credential)

                        onProgress("Downloading backup...")
                        val zipFile = downloadBackup(driveService, fileId, File(tempDir, "backup.zip"))
                        val extractDir = File(tempDir, "files").apply { mkdirs() }

                        onProgress("Uncompressing backup...")
                        unzipBackup(zipFile, extractDir)

                        onProgress("Verifying backup metadata...")
                        verifyBackupMetadata(extractDir)

                        onProgress("Checking backup...")
                        val contents = readBackupContents(File(extractDir, BackupZip.DATABASE))

                        onProgress("Restoring database...")
                        trace("backup_manager_replace_database_contents") {
                            contents.replaceAllIn(db.databaseContentsDao())
                        }

                        Result.success(Unit)
                    } catch (e: Exception) {
                        Log.e(TAG, "Restore failed", e)
                        Result.failure(e)
                    } finally {
                        tempDir?.deleteRecursively()
                    }
                }
            }
        }
    }

    @AddTrace(name = "backup_manager_download_backup", enabled = true)
    private fun downloadBackup(driveService: Drive, fileId: String, zipFile: File): File {
        FileOutputStream(zipFile).use { outputStream ->
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
        }
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
    private suspend fun readBackupContents(dbFile: File): DatabaseContents {
        val result = runCatching {
            trace("backup_manager_read_backup_contents") {
                MemoryMapDatabase.withCheckedCopy(context, dbFile.absolutePath) { copy ->
                    DatabaseContents.readFrom(copy.databaseContentsDao())
                }
            }
        }
        result.exceptionOrNull()?.let { e ->
            if (e is CancellationException) throw e
            throw UnreadableBackupException(context.getString(R.string.restore_error_backup_unreadable), e)
        }
        return result.getOrThrow()
    }

    suspend fun deleteBackup(credential: GoogleAccountCredential, fileId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            trace("backup_manager_delete_backup") {
                try {
                    val driveService = getDriveService(credential)
                    driveService.files().delete(fileId).execute()
                    Result.success(Unit)
                } catch (e: IOException) {
                    Log.e(TAG, "Backup deletion failed", e)
                    Result.failure(e)
                }
            }
        }
    }

    @AddTrace(name = "backup_manager_get_or_create_backup_folder", enabled = true)
    private fun getOrCreateBackupFolder(driveService: Drive): String {
        val folderName = "Memory Map Backups"
        val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and trashed = false"
        val result = driveService.files().list().setQ(query).setSpaces("drive").execute()
        if (result.files.isNotEmpty()) return result.files.first().id
        val folderMetadata = DriveFile().apply {
            name = folderName
            mimeType = "application/vnd.google-apps.folder"
        }
        return driveService.files().create(folderMetadata).setFields("id").execute().id
    }

    companion object {
        const val TAG = "BackupManager"

        private const val DRIVE_LIST_PAGE_SIZE = 1000 // Drive's maximum

        /** Process-wide: BackupManager is created per caller, but only one backup or restore may run at a time. */
        private val backupLock = Mutex()

        private val _backupEvents = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 1)
        val backupEvents = _backupEvents.asSharedFlow()

        /**
         * Collects every page of a paged Drive list call. [fetchPage] gets the page token (null for the
         * first page) and returns that page's items and the next token (null or blank on the last page).
         */
        internal inline fun <T> collectAllPages(fetchPage: (pageToken: String?) -> Pair<List<T>, String?>): List<T> {
            val all = mutableListOf<T>()
            var pageToken: String? = null
            do {
                val (items, nextPageToken) = fetchPage(pageToken)
                all += items
                pageToken = nextPageToken?.takeIf { it.isNotBlank() && it != pageToken }
            } while (pageToken != null)
            return all
        }
    }
}
