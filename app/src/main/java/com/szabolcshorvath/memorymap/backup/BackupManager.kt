package com.szabolcshorvath.memorymap.backup

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.StoryMapDatabase
import com.szabolcshorvath.memorymap.util.MediaHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.google.api.services.drive.model.File as DriveFile

class BackupManager(private val context: Context) {

    private data class LocalMediaInfo(
        val uri: String,
        val mediaSignature: String,
        val size: Long
    )

    fun getDriveService(credential: GoogleAccountCredential): Drive = Drive.Builder(
        NetHttpTransport(),
        GsonFactory.getDefaultInstance(),
        credential
    ).setApplicationName("Memory Map").build()

    suspend fun performBackup(
        credential: GoogleAccountCredential,
        onProgress: (String) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var tempDir: File? = null
            var zipFile: File? = null
            try {
                val driveService = getDriveService(credential)

                onProgress("Preparing database...")
                // Checkpoint database to ensure all data is in the main file
                try {
                    StoryMapDatabase.getDatabase(context).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)")
                        .close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val dbFile = context.getDatabasePath("memory_map_database")
                if (!dbFile.exists()) {
                    throw Exception("Database not found")
                }

                tempDir = File(context.cacheDir, "backup_temp")
                if (tempDir.exists()) tempDir.deleteRecursively()
                tempDir.mkdirs()

                // Create metadata
                val metadata = JSONObject()
                metadata.put("timestamp", System.currentTimeMillis())
                metadata.put(
                    "date",
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                )
                metadata.put("dbSize", dbFile.length())
                metadata.put("version", 1) // Backup format version

                val metadataFile = File(tempDir, "metadata.json")
                metadataFile.writeText(metadata.toString())

                // Zip files
                onProgress("Compressing data...")
                zipFile = File(context.cacheDir, "backup.zip")
                ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
                    // Add DB
                    addToZip(out, dbFile, "database.sqlite")
                    // Add Metadata
                    addToZip(out, metadataFile, "metadata.json")
                    // Add SHM and WAL if they exist
                    val walFile = context.getDatabasePath("memory_map_database-wal")
                    if (walFile.exists()) addToZip(out, walFile, "database.sqlite-wal")
                    val shmFile = context.getDatabasePath("memory_map_database-shm")
                    if (shmFile.exists()) addToZip(out, shmFile, "database.sqlite-shm")
                }

                onProgress("Uploading to Google Drive...")
                // Find or create folder
                val folderId = getOrCreateBackupFolder(driveService)

                // Upload
                val fileMetadata = DriveFile()
                fileMetadata.name = "MemoryMap_Backup_${
                    SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        Locale.US
                    ).format(Date())
                }.zip"
                fileMetadata.parents = listOf(folderId)

                val mediaContent = FileContent("application/zip", zipFile)

                driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()

                return@withContext true
            } catch (e: Exception) {
                Log.e("BackupManager", "Backup failed", e)
                return@withContext false
            } finally {
                // Cleanup
                tempDir?.deleteRecursively()
                zipFile?.delete()
            }
        }
    }

    suspend fun listBackups(credential: GoogleAccountCredential): List<DriveFile> {
        return withContext(Dispatchers.IO) {
            try {
                val driveService = getDriveService(credential)

                val folderId = getOrCreateBackupFolder(driveService)

                val query = "'$folderId' in parents and trashed = false"
                val result = driveService.files().list()
                    .setQ(query)
                    .setFields("files(id, name, modifiedTime, size)")
                    .setOrderBy("modifiedTime desc")
                    .execute()

                return@withContext result.files ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext emptyList()
            }
        }
    }

    suspend fun restoreBackup(
        credential: GoogleAccountCredential,
        fileId: String,
        onProgress: (String) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var tempZipFile: File? = null
            var tempRestoreDir: File? = null
            try {
                val driveService = getDriveService(credential)

                onProgress("Downloading backup...")
                tempZipFile = File(context.cacheDir, "restore_temp.zip")
                FileOutputStream(tempZipFile).use { outputStream ->
                    driveService.files().get(fileId)
                        .executeMediaAndDownloadTo(outputStream)
                }

                onProgress("Restoring database...")
                tempRestoreDir = File(context.cacheDir, "restore_temp_dir")
                if (tempRestoreDir.exists()) tempRestoreDir.deleteRecursively()
                tempRestoreDir.mkdirs()

                // Unzip
                ZipInputStream(BufferedInputStream(FileInputStream(tempZipFile))).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val file = File(tempRestoreDir, entry.name)
                        // canonical path check to prevent zip slip
                        if (file.canonicalPath.startsWith(tempRestoreDir.canonicalPath)) {
                            FileOutputStream(file).use { fos ->
                                val buffer = ByteArray(1024)
                                var count: Int
                                while (zis.read(buffer).also { count = it } != -1) {
                                    fos.write(buffer, 0, count)
                                }
                            }
                        }
                        entry = zis.nextEntry
                    }
                }

                // Verify metadata
                val metadataFile = File(tempRestoreDir, "metadata.json")
                if (!metadataFile.exists()) {
                    throw Exception("Invalid backup: missing metadata")
                }

                // Close DB connections
                StoryMapDatabase.closeDatabase()

                // Copy files back
                val dbFile = context.getDatabasePath("memory_map_database")
                val walFile = context.getDatabasePath("memory_map_database-wal")
                val shmFile = context.getDatabasePath("memory_map_database-shm")

                val restoredDb = File(tempRestoreDir, "database.sqlite")
                if (restoredDb.exists()) {
                    restoredDb.copyTo(dbFile, overwrite = true)
                }

                val restoredWal = File(tempRestoreDir, "database.sqlite-wal")
                if (restoredWal.exists()) {
                    restoredWal.copyTo(walFile, overwrite = true)
                } else {
                    if (walFile.exists()) walFile.delete()
                }

                val restoredShm = File(tempRestoreDir, "database.sqlite-shm")
                if (restoredShm.exists()) {
                    restoredShm.copyTo(shmFile, overwrite = true)
                } else {
                    if (shmFile.exists()) shmFile.delete()
                }

                onProgress("Verifying media...")
                verifyAndFixMediaItems()

                return@withContext true
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false
            } finally {
                // Cleanup
                tempZipFile?.delete()
                tempRestoreDir?.deleteRecursively()
            }
        }
    }

    private suspend fun verifyAndFixMediaItems() {
        val currentDeviceId =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

        val database = StoryMapDatabase.getDatabase(context)
        val dao = database.memoryGroupDao()
        val mediaItems = dao.getAllMediaItems()

        val localMediaList = getAllLocalMedia(mediaItems)

        val itemsToUpdate = mutableListOf<MediaItem>()
        val itemsWithoutLocalEquivalent = mutableListOf<MediaItem>()

        for (item in mediaItems) {
            if (item.deviceId != currentDeviceId) {
                val candidate = localMediaList.find { it.mediaSignature == item.mediaSignature }
                if (candidate != null) {
                    itemsToUpdate.add(
                        item.copy(
                            deviceId = currentDeviceId,
                            uri = candidate.uri
                        )
                    )
                } else {
                    Log.w(TAG, "No local file with signature ${item.mediaSignature} found")
                    itemsWithoutLocalEquivalent.add(item)
                }
            }
        }

        if (itemsToUpdate.isNotEmpty()) {
            dao.updateMediaItems(itemsToUpdate)
        }
        if (itemsWithoutLocalEquivalent.isNotEmpty()) {
            Log.w(TAG, "Items without local equivalent: $itemsWithoutLocalEquivalent")
        }
    }

    private fun getAllLocalMedia(mediaItems: List<MediaItem>): List<LocalMediaInfo> {
        val mediaList = mutableListOf<LocalMediaInfo>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.SIZE,
        )
        val selection = "${MediaStore.MediaColumns.SIZE} IN (${
            mediaItems.map { it.fileSize }.joinToString(", ")
        })"

        // Query Images
        queryMediaStore(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            mediaList
        )
        // Query Videos
        queryMediaStore(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            mediaList
        )

        return mediaList
    }

    private fun queryMediaStore(
        contentUri: Uri,
        projection: Array<String>,
        selection: String?,
        mediaList: MutableList<LocalMediaInfo>
    ) {
        try {
            context.contentResolver.query(
                contentUri,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)

                while (cursor.moveToNext()) {
                    val size = cursor.getLong(sizeCol)
                    val uri = android.content.ContentUris.withAppendedId(
                        contentUri,
                        cursor.getLong(idCol)
                    ).toString()

                    mediaList.add(
                        LocalMediaInfo(
                            uri,
                            MediaHasher.calculateMediaSignature(context, uri.toUri()),
                            size
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying media store: ${e.message}")
        }
    }

    suspend fun deleteBackup(credential: GoogleAccountCredential, fileId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val driveService = getDriveService(credential)
                driveService.files().delete(fileId).execute()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false
            }
        }
    }

    private fun getOrCreateBackupFolder(driveService: Drive): String {
        val folderName = "Memory Map Backups"
        val query =
            "mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and trashed = false"
        val result = driveService.files().list().setQ(query).setSpaces("drive").execute()

        if (result.files.isNotEmpty()) {
            return result.files[0].id
        }

        val folderMetadata = DriveFile()
        folderMetadata.name = folderName
        folderMetadata.mimeType = "application/vnd.google-apps.folder"

        val folder = driveService.files().create(folderMetadata)
            .setFields("id")
            .execute()

        return folder.id
    }

    private fun addToZip(out: ZipOutputStream, file: File, fileName: String) {
        FileInputStream(file).use { fi ->
            BufferedInputStream(fi).use { origin ->
                val entry = ZipEntry(fileName)
                out.putNextEntry(entry)
                origin.copyTo(out)
            }
        }
    }

    companion object {
        const val TAG = "BackupManager"
    }
}
