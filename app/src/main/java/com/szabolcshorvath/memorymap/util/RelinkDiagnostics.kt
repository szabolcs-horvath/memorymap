package com.szabolcshorvath.memorymap.util

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.google.firebase.perf.trace
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId

/**
 * Writes a report on the media items that need re-linking and the files on this phone that could be them,
 * for the "Send logs" zip. Re-linking only accepts a file of exactly the stored size and signature; the
 * report shows what is there instead (sizes, dates, owners, signatures), so a failed re-link can be explained.
 *
 * It only reads: nothing in the database or the media library changes.
 */
object RelinkDiagnostics {
    const val TAG = "RelinkDiagnostics"

    /** Writes the report to [file]. A failure is written into the file instead, so the logs can still be sent. */
    suspend fun writeReport(context: Context, file: File) {
        withContext(Dispatchers.IO) {
            val text = trace("relink_diagnostics_write_report") {
                runCatching { RelinkReportFormatter.render(collect(context), ZoneId.systemDefault()) }
                    .getOrElse { e ->
                        if (e is CancellationException) throw e
                        Log.e(TAG, "Could not create the re-link diagnostics", e)
                        "Could not create the re-link diagnostics: ${e.javaClass.name}: ${e.message}\n"
                    }
            }
            file.writeText(text)
        }
    }

    private suspend fun collect(context: Context): RelinkReport {
        val installationId = InstallationIdentifier.getInstallationIdentifier(context)
        val items = MemoryMapDatabase.getDatabase(context).memoryGroupDao().getAllMediaItems()
        val library = PhoneLibrary(context)
        val grants = context.contentResolver.persistedUriPermissions.mapTo(HashSet()) { it.uri.toString() }

        val needing = items.mapNotNull { item ->
            val stored = library.storedState(item.uri)
            relinkReasons(item, installationId, stored).takeIf { it.isNotEmpty() }?.let { Triple(item, stored, it) }
        }
        val sameSizeFiles = library.filesWithSizes(needing.map { it.first.fileSize })
        val diagnoses = needing.mapIndexed { index, (item, stored, reasons) ->
            ItemDiagnosis(
                item = item,
                reasons = reasons,
                stored = stored,
                hasGrant = item.uri in grants,
                storedUriSignature = if (stored is StoredUriState.Readable) library.signature(item.uri) else null,
                sameSize = sameSizeCheck(item, sameSizeFiles, library),
                nearby = if (index < RelinkReportFormatter.DETAILED_ITEMS) library.nearby(item) else null
            )
        }
        return RelinkReport(
            generatedAt = System.currentTimeMillis(),
            installation = installationInfo(context, installationId),
            access = MediaAccessInfo(
                permissions = mediaPermissions().map { it to PermissionUtil.checkPermission(context, it) },
                persistedGrants = grants.size,
                visibleImages = library.count(MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
                visibleVideos = library.count(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            ),
            totalItems = items.size,
            deviceIdCounts = items.groupingBy { it.deviceId }.eachCount(),
            diagnoses = diagnoses
        )
    }

    private fun sameSizeCheck(item: MediaItem, filesBySize: Map<Long, List<String>>?, library: PhoneLibrary): SameSizeCheck? {
        if (filesBySize == null || item.fileSize <= 0) return null
        val files = filesBySize[item.fileSize].orEmpty()
        val matches = if (item.mediaSignature.isEmpty()) 0 else files.count { library.signature(it) == item.mediaSignature }
        return SameSizeCheck(files.size, matches)
    }

    private fun installationInfo(context: Context, installationId: String): InstallationInfo {
        val packageInfo = packageInfo(context)
        return InstallationInfo(
            id = installationId,
            idFileWrittenAt = InstallationIdentifier.idFileWrittenAt(context),
            firstInstalledAt = packageInfo?.firstInstallTime,
            lastUpdatedAt = packageInfo?.lastUpdateTime
        )
    }

    private fun packageInfo(context: Context): PackageInfo? = try {
        val packageManager = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(context.packageName, 0)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun mediaPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }
}

/** Read-only queries of this phone's MediaStore, with the signatures it computes cached. */
private class PhoneLibrary(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    private val contentUris = LocalMediaUtil.readableContentUris(context)
    private val signatures = HashMap<String, String?>()

    fun signature(uri: String): String? = if (signatures.containsKey(uri)) {
        signatures[uri]
    } else {
        LocalMediaUtil.signatureOrNull(context, uri.toUri()).also { signatures[uri] = it }
    }

    /** The same metadata query `LocalMediaUtil.isMediaValid` runs, keeping the error. */
    fun storedState(uri: String): StoredUriState = try {
        resolver.query(uri.toUri(), STORED_PROJECTION, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                StoredUriState.Readable(c.long(MediaStore.MediaColumns.SIZE) ?: 0L, c.long(MediaStore.MediaColumns.DATE_TAKEN) ?: 0L)
            } else {
                StoredUriState.Unreadable("query returned no row")
            }
        } ?: StoredUriState.Unreadable("query returned no cursor")
    } catch (e: SecurityException) {
        StoredUriState.Unreadable(describe(e))
    } catch (e: IllegalArgumentException) {
        StoredUriState.Unreadable(describe(e))
    } catch (e: IllegalStateException) {
        StoredUriState.Unreadable(describe(e))
    }

    /** URIs of the files of each size, or null without media access. */
    fun filesWithSizes(sizes: List<Long>): Map<Long, List<String>>? {
        if (contentUris.isEmpty()) return null
        val wanted = sizes.filter { it > 0 }.distinct()
        val found = HashMap<Long, MutableList<String>>()
        for (contentUri in contentUris) {
            for (chunk in wanted.chunked(SIZE_QUERY_CHUNK)) {
                val selection = "${MediaStore.MediaColumns.SIZE} IN (${chunk.joinToString(",")})"
                query(contentUri, SIZE_PROJECTION, Selection(selection)) { c ->
                    val size = c.long(MediaStore.MediaColumns.SIZE) ?: return@query
                    found.getOrPut(size) { mutableListOf() } += ContentUris.withAppendedId(contentUri, c.getLong(0)).toString()
                }
            }
        }
        return found
    }

    /** Files taken or changed near the item's date, trashed ones included, or null when that can't be searched. */
    fun nearby(item: MediaItem): List<PhoneFile>? {
        if (contentUris.isEmpty() || item.dateTaken <= 0) return null
        val from = item.dateTaken - NEARBY_WINDOW_MS
        val to = item.dateTaken + NEARBY_WINDOW_MS
        val selection = "(${MediaStore.MediaColumns.DATE_TAKEN} BETWEEN ? AND ?) OR (${MediaStore.MediaColumns.DATE_MODIFIED} BETWEEN ? AND ?)"
        val args = arrayOf(from, to, from / MILLIS_PER_SECOND, to / MILLIS_PER_SECOND).map { it.toString() }.toTypedArray()
        val files = mutableListOf<PhoneFile>()
        for (contentUri in contentUris) {
            query(contentUri, nearbyProjection(), Selection(selection, args, includeTrashed = true)) { c ->
                if (files.size < NEARBY_SCAN_LIMIT) files += phoneFile(contentUri, c)
            }
        }
        return nearest(files, item.dateTaken, NEARBY_LIMIT).map { it.copy(signature = signature(it.uri)) }
    }

    /** How many files the app can see at [contentUri], or null if it can't read it. */
    fun count(contentUri: Uri): Int? {
        if (contentUri !in contentUris) return null
        var count = 0
        val read = query(contentUri, arrayOf(MediaStore.MediaColumns._ID), Selection(null)) { count++ }
        return count.takeIf { read }
    }

    private fun phoneFile(contentUri: Uri, c: Cursor) = PhoneFile(
        uri = ContentUris.withAppendedId(contentUri, c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))).toString(),
        displayName = c.string(MediaStore.MediaColumns.DISPLAY_NAME),
        relativePath = c.string(RELATIVE_PATH),
        mimeType = c.string(MediaStore.MediaColumns.MIME_TYPE),
        size = c.long(MediaStore.MediaColumns.SIZE) ?: 0L,
        dateTaken = c.long(MediaStore.MediaColumns.DATE_TAKEN) ?: 0L,
        dateModified = (c.long(MediaStore.MediaColumns.DATE_MODIFIED) ?: 0L) * MILLIS_PER_SECOND,
        width = c.long(MediaStore.MediaColumns.WIDTH)?.toInt() ?: 0,
        height = c.long(MediaStore.MediaColumns.HEIGHT)?.toInt() ?: 0,
        ownerPackage = c.string(OWNER_PACKAGE_NAME),
        isTrashed = c.long(IS_TRASHED) == 1L,
        isPending = c.long(IS_PENDING) == 1L
    )

    /** Runs [onRow] for every row. Returns false if the query failed or returned no cursor. */
    private fun query(contentUri: Uri, projection: Array<String>, selection: Selection, onRow: (Cursor) -> Unit): Boolean = try {
        val cursor = if (selection.includeTrashed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection.sql)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selection.args)
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
            }
            resolver.query(contentUri, projection, queryArgs, null)
        } else {
            resolver.query(contentUri, projection, selection.sql, selection.args, null)
        }
        cursor?.use { c -> while (c.moveToNext()) onRow(c) } != null
    } catch (e: SecurityException) {
        Log.w(RelinkDiagnostics.TAG, "Query of $contentUri failed", e)
        false
    } catch (e: IllegalArgumentException) {
        Log.w(RelinkDiagnostics.TAG, "Query of $contentUri failed", e)
        false
    }

    private fun describe(e: Exception) = "${e.javaClass.simpleName}: ${e.message}"

    /** A query's WHERE clause. Trashed and pending files are included only when asked (Android 11 and newer). */
    private class Selection(val sql: String?, val args: Array<String> = emptyArray(), val includeTrashed: Boolean = false)

    private companion object {
        const val SIZE_QUERY_CHUNK = 100
        const val NEARBY_LIMIT = 10
        const val NEARBY_SCAN_LIMIT = 5000
        const val MILLIS_PER_SECOND = 1000L
        const val SECONDS_PER_HOUR = 3600L
        const val NEARBY_WINDOW_MS = RelinkReportFormatter.NEARBY_WINDOW_HOURS * SECONDS_PER_HOUR * MILLIS_PER_SECOND

        // Column names spelled out: these exist from Android 10 (Q) or 11 (R), and older versions just lack them.
        const val RELATIVE_PATH = "relative_path"
        const val OWNER_PACKAGE_NAME = "owner_package_name"
        const val IS_PENDING = "is_pending"
        const val IS_TRASHED = "is_trashed"

        val STORED_PROJECTION = arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_TAKEN)
        val SIZE_PROJECTION = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE)

        fun nearbyProjection(): Array<String> = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) addAll(listOf(RELATIVE_PATH, OWNER_PACKAGE_NAME, IS_PENDING))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(IS_TRASHED)
        }.toTypedArray()
    }
}

private fun Cursor.long(column: String): Long? = getColumnIndex(column).takeIf { it != -1 && !isNull(it) }?.let(::getLong)

private fun Cursor.string(column: String): String? = getColumnIndex(column).takeIf { it != -1 && !isNull(it) }?.let(::getString)
