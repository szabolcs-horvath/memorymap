package com.szabolcshorvath.memorymap.backup

import android.content.Context
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.firebase.perf.metrics.AddTrace
import com.szabolcshorvath.memorymap.auth.DriveAccessNeededException
import com.szabolcshorvath.memorymap.auth.GoogleAccountService
import java.io.File
import java.io.FileOutputStream
import com.google.api.services.drive.model.File as DriveFile

/** Backups in the "Memory Map Backups" folder of the account's Google Drive (`drive.file` scope). */
object DriveBackupStorage : BackupStorage {

    private const val FOLDER_NAME = "Memory Map Backups"
    private const val DRIVE_LIST_PAGE_SIZE = 1000 // Drive's maximum
    private const val HTTP_UNAUTHORIZED = 401
    private const val TOKEN_ATTEMPTS = 2 // the first token, and one replacing it if Drive rejects it

    override fun list(context: Context, email: String): List<DriveFile> = withDrive(context, email) { driveService ->
        val folderId = getOrCreateBackupFolder(driveService)
        val query = "'$folderId' in parents and trashed = false"
        collectAllPages { pageToken ->
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

    override fun upload(context: Context, email: String, name: String, zipFile: File) = withDrive(context, email) { driveService ->
        val fileMetadata = DriveFile()
        fileMetadata.name = name
        fileMetadata.parents = listOf(getOrCreateBackupFolder(driveService))

        val mediaContent = FileContent("application/zip", zipFile)
        driveService.files().create(fileMetadata, mediaContent)
            .setFields("id")
            .execute()
        Unit
    }

    override fun download(context: Context, email: String, fileId: String, target: File) = withDrive(context, email) { driveService ->
        // Opened here, so a second try writes the file from the start.
        FileOutputStream(target).use { outputStream ->
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
        }
    }

    override fun delete(context: Context, email: String, fileId: String) = withDrive(context, email) { driveService ->
        driveService.files().delete(fileId).execute()
        Unit
    }

    /** Runs [call] with a Drive client for [email], authorized with an access token from Play services. */
    private fun <T> withDrive(context: Context, email: String, call: (Drive) -> T): T = withFreshToken(
        token = { GoogleAccountService.driveAccessToken(context, email) },
        clear = { GoogleAccountService.clearAccessToken(context, it) },
        call = { token -> call(driveService(token)) }
    )

    private fun driveService(token: String): Drive {
        val authorize = HttpRequestInitializer { request -> request.headers.authorization = "Bearer $token" }
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), authorize)
            .setApplicationName("Memory Map")
            .build()
    }

    /**
     * Runs [call] with a token from [token]. A token Drive rejects (401) is [clear]ed and replaced once: Play services
     * can still hold one for access the user has removed meanwhile. Rejected again, the user has to allow access.
     */
    internal fun <T> withFreshToken(token: () -> String, clear: (String) -> Unit, call: (String) -> T): T {
        var rejection: GoogleJsonResponseException? = null
        repeat(TOKEN_ATTEMPTS) {
            val current = token()
            try {
                return call(current)
            } catch (e: GoogleJsonResponseException) {
                if (e.statusCode != HTTP_UNAUTHORIZED) throw e
                clear(current)
                rejection = e
            }
        }
        throw DriveAccessNeededException(consent = null, cause = rejection)
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
