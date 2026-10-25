package com.szabolcshorvath.memorymap.backup

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.firebase.perf.metrics.AddTrace
import java.io.File
import java.io.FileOutputStream
import com.google.api.services.drive.model.File as DriveFile

/** Backups in the "Memory Map Backups" folder of the account's Google Drive (`drive.file` scope). */
object DriveBackupStorage : BackupStorage {

    private const val FOLDER_NAME = "Memory Map Backups"
    private const val DRIVE_LIST_PAGE_SIZE = 1000 // Drive's maximum

    override fun list(context: Context, email: String): List<DriveFile> {
        val driveService = driveService(context, email)
        val folderId = getOrCreateBackupFolder(driveService)
        val query = "'$folderId' in parents and trashed = false"
        return collectAllPages { pageToken ->
            val result = driveService.files().list()
                .setQ(query)
                .setFields("nextPageToken, files(id, name, modifiedTime, size)")
                .setOrderBy("modifiedTime desc")
                .setPageSize(DRIVE_LIST_PAGE_SIZE)
                .setPageToken(pageToken)
                .execute()
            (result.files ?: emptyList()) to result.nextPageToken
        }
    }

    override fun upload(context: Context, email: String, name: String, zipFile: File) {
        val driveService = driveService(context, email)
        val fileMetadata = DriveFile()
        fileMetadata.name = name
        fileMetadata.parents = listOf(getOrCreateBackupFolder(driveService))

        val mediaContent = FileContent("application/zip", zipFile)
        driveService.files().create(fileMetadata, mediaContent)
            .setFields("id")
            .execute()
    }

    override fun download(context: Context, email: String, fileId: String, target: File) {
        FileOutputStream(target).use { outputStream ->
            driveService(context, email).files().get(fileId).executeMediaAndDownloadTo(outputStream)
        }
    }

    override fun delete(context: Context, email: String, fileId: String) {
        driveService(context, email).files().delete(fileId).execute()
    }

    private fun driveService(context: Context, email: String): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE)).apply {
            selectedAccountName = email
        }
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("Memory Map")
            .build()
    }

    @AddTrace(name = "drive_backup_storage_get_or_create_backup_folder", enabled = true)
    private fun getOrCreateBackupFolder(driveService: Drive): String {
        val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$FOLDER_NAME' and trashed = false"
        val result = driveService.files().list().setQ(query).setSpaces("drive").execute()
        if (result.files.isNotEmpty()) return result.files.first().id
        val folderMetadata = DriveFile().apply {
            name = FOLDER_NAME
            mimeType = "application/vnd.google-apps.folder"
        }
        return driveService.files().create(folderMetadata).setFields("id").execute().id
    }

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
