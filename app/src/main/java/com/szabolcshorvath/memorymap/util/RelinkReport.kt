package com.szabolcshorvath.memorymap.util

import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.util.LocalMediaUtil.MediaMetadata
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/** Why a media item needs re-linking. An item can have several. */
internal enum class RelinkReason(val label: String) {
    OTHER_INSTALLATION("saved by another installation"),
    URI_UNREADABLE("stored URI can't be read"),
    DIFFERENT_FILE("stored URI now points to a different file")
}

/** What a metadata query of an item's stored URI returned (the same query `LocalMediaUtil.isMediaValid` runs). */
internal sealed interface StoredUriState {
    data class Readable(val size: Long, val dateTaken: Long) : StoredUriState
    data class Unreadable(val error: String) : StoredUriState
}

/** The reasons `LocalMediaUtil.needsRelink` would give, all of them rather than the first. */
internal fun relinkReasons(item: MediaItem, installationId: String, stored: StoredUriState): List<RelinkReason> = buildList {
    if (item.deviceId != installationId) add(RelinkReason.OTHER_INSTALLATION)
    when (stored) {
        is StoredUriState.Unreadable -> add(RelinkReason.URI_UNREADABLE)
        is StoredUriState.Readable ->
            if (!LocalMediaUtil.isSameFile(item.fileSize, item.dateTaken, MediaMetadata(stored.size, stored.dateTaken))) {
                add(RelinkReason.DIFFERENT_FILE)
            }
    }
}

internal fun uriKind(uri: String): String = when {
    uri.startsWith("content://media/picker") -> "photo picker"
    uri.startsWith("content://media/") -> "MediaStore"
    else -> "other"
}

/** A photo or video in this phone's MediaStore. Times are in milliseconds; 0 means unknown. */
internal data class PhoneFile(
    val uri: String,
    val displayName: String?,
    val relativePath: String?,
    val mimeType: String?,
    val size: Long,
    val dateTaken: Long,
    val dateModified: Long,
    val width: Int,
    val height: Int,
    val ownerPackage: String?,
    val isTrashed: Boolean,
    val isPending: Boolean,
    val signature: String? = null
)

/** How far [file]'s capture or change time is from [dateTaken], whichever is closer. */
internal fun distance(file: PhoneFile, dateTaken: Long): Long =
    listOf(file.dateTaken, file.dateModified).filter { it > 0 }.minOfOrNull { abs(it - dateTaken) } ?: Long.MAX_VALUE

/** The [limit] files nearest to [dateTaken], nearest first. */
internal fun nearest(files: List<PhoneFile>, dateTaken: Long, limit: Int): List<PhoneFile> =
    files.distinctBy { it.uri }.sortedBy { distance(it, dateTaken) }.take(limit)

/** MediaStore files with the item's exact size, and how many of them have its signature (what re-linking needs). */
internal data class SameSizeCheck(val files: Int, val signatureMatches: Int)

internal data class ItemDiagnosis(
    val item: MediaItem,
    val reasons: List<RelinkReason>,
    val stored: StoredUriState,
    val hasGrant: Boolean,
    /** The signature of what the stored URI reads now, or null if it can't be read. */
    val storedUriSignature: String?,
    /** Null when not searched (no media access, or no stored size). */
    val sameSize: SameSizeCheck?,
    /** Null when not searched (no media access, no stored date, or beyond the detailed items). */
    val nearby: List<PhoneFile>?
)

internal data class InstallationInfo(
    val id: String,
    val idFileWrittenAt: Long?,
    val firstInstalledAt: Long?,
    val lastUpdatedAt: Long?
)

internal data class MediaAccessInfo(
    /** Permission name to whether it is granted. */
    val permissions: List<Pair<String, Boolean>>,
    val persistedGrants: Int,
    /** Null when the app can't read them. */
    val visibleImages: Int?,
    val visibleVideos: Int?
)

internal data class RelinkReport(
    val generatedAt: Long,
    val installation: InstallationInfo,
    val access: MediaAccessInfo,
    val totalItems: Int,
    val deviceIdCounts: Map<String, Int>,
    /** Only the items that need re-linking. */
    val diagnoses: List<ItemDiagnosis>
)

/** Renders a [RelinkReport] as plain text for the "Send logs" zip. */
internal object RelinkReportFormatter {
    /** Items shown with full details and nearby files; the others get one line each. */
    const val DETAILED_ITEMS = 40
    const val NEARBY_WINDOW_HOURS = 24L

    private const val PERCENT = 100.0
    private const val MILLIS_PER_SECOND = 1000L
    private val TIME_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun render(report: RelinkReport, zone: ZoneId): String = buildString {
        appendLine("Memory Map re-link diagnostics")
        appendLine("Generated: ${time(report.generatedAt, zone)}")
        appendLine("Lists the media items that need re-linking and, for the first $DETAILED_ITEMS, the photos and videos on this phone")
        appendLine("taken or changed within $NEARBY_WINDOW_HOURS hours of them (names, folders, dates, sizes).")
        appendInstallation(report.installation, zone)
        appendAccess(report.access)
        appendSummary(report)
        appendItemLines(report.diagnoses, zone)
        appendDetails(report, zone)
    }

    private fun StringBuilder.appendInstallation(info: InstallationInfo, zone: ZoneId) {
        appendLine()
        appendLine("== Installation ==")
        appendLine("Installation id: ${info.id}")
        appendLine("Id file written: ${time(info.idFileWrittenAt, zone)}")
        appendLine("App first installed: ${time(info.firstInstalledAt, zone)}")
        appendLine("App last updated: ${time(info.lastUpdatedAt, zone)}")
    }

    private fun StringBuilder.appendAccess(access: MediaAccessInfo) {
        appendLine()
        appendLine("== Media access ==")
        access.permissions.forEach { (name, granted) ->
            appendLine("${name.substringAfterLast('.')}: ${if (granted) "granted" else "not granted"}")
        }
        appendLine("Persisted URI grants: ${access.persistedGrants} (Android keeps at most 512)")
        appendLine("Photos visible in MediaStore: ${access.visibleImages ?: "not readable"}")
        appendLine("Videos visible in MediaStore: ${access.visibleVideos ?: "not readable"}")
    }

    private fun StringBuilder.appendSummary(report: RelinkReport) {
        val diagnoses = report.diagnoses
        appendLine()
        appendLine("== Media items ==")
        appendLine("Total: ${report.totalItems}")
        appendLine("Need re-linking: ${diagnoses.size}")
        appendLine("By device id:")
        val currentId = report.installation.id
        appendLine("  $currentId (this installation): ${report.deviceIdCounts[currentId] ?: 0}")
        report.deviceIdCounts.filterKeys { it != currentId }.forEach { (id, count) -> appendLine("  $id: $count") }
        if (diagnoses.isEmpty()) return

        appendLine("Reasons (an item can have several):")
        RelinkReason.entries.forEach { reason -> appendLine("  ${reason.label}: ${diagnoses.count { reason in it.reasons }}") }
        val kinds = diagnoses.groupingBy { uriKind(it.item.uri) }.eachCount()
        appendLine("Stored URI kinds: ${kinds.entries.joinToString { "${it.key} ${it.value}" }}")
        appendLine("With a persisted grant: ${diagnoses.count { it.hasGrant }}")
        appendLine("Without a stored signature: ${diagnoses.count { it.item.mediaSignature.isEmpty() }}")
        val searched = diagnoses.mapNotNull { it.sameSize }
        appendLine("Same-size file on this phone: ${searched.count { it.files > 0 }} of ${searched.size} searched")
        appendLine("Same size and signature (would re-link): ${searched.count { it.signatureMatches > 0 }}")
    }

    private fun StringBuilder.appendItemLines(diagnoses: List<ItemDiagnosis>, zone: ZoneId) {
        if (diagnoses.isEmpty()) return
        appendLine()
        appendLine("== Items that need re-linking ==")
        diagnoses.forEach { d ->
            val item = d.item
            appendLine(
                "#${item.id}  group ${item.groupId}  ${item.type}  ${uriKind(item.uri)}  size ${item.fileSize}  " +
                    "taken ${time(item.dateTaken, zone)}  grant ${yesNo(d.hasGrant)}  " +
                    "same size ${d.sameSize?.files ?: "-"}  signature ${d.sameSize?.signatureMatches ?: "-"}  " +
                    "[${d.reasons.joinToString("; ") { it.label }}]"
            )
        }
    }

    private fun StringBuilder.appendDetails(report: RelinkReport, zone: ZoneId) {
        val detailed = report.diagnoses.take(DETAILED_ITEMS)
        if (detailed.isEmpty()) return
        appendLine()
        appendLine("== Details (first ${detailed.size} items) ==")
        val canSearch = report.access.visibleImages != null || report.access.visibleVideos != null
        detailed.forEach { appendItemDetails(it, report.installation.id, canSearch, zone) }
    }

    private fun StringBuilder.appendItemDetails(d: ItemDiagnosis, installationId: String, canSearch: Boolean, zone: ZoneId) {
        val item = d.item
        appendLine()
        appendLine("--- #${item.id} (group ${item.groupId}, ${item.type}) ---")
        appendLine("URI: ${item.uri}")
        appendLine("Device id: ${item.deviceId} (${if (item.deviceId == installationId) "this installation" else "another installation"})")
        appendLine("Stored size: ${item.fileSize} bytes")
        appendLine("Stored date taken: ${time(item.dateTaken, zone)}")
        appendLine("Stored signature: ${item.mediaSignature.ifEmpty { "(none)" }}")
        val stored = d.stored
        appendLine(
            when (stored) {
                is StoredUriState.Readable -> "Stored URI now: size ${stored.size}, taken ${time(stored.dateTaken, zone)}"
                is StoredUriState.Unreadable -> "Stored URI now: can't be read (${stored.error})"
            }
        )
        appendLine("Stored URI signature now: ${d.storedUriSignature?.let { signatureNote(it, item) } ?: "(can't be read)"}")
        appendLine("Persisted grant: ${yesNo(d.hasGrant)}")
        appendLine(
            d.sameSize?.let { "Same-size files: ${it.files}, with the stored signature: ${it.signatureMatches}" }
                ?: "Same-size files: not searched (${if (canSearch) "no stored size" else "no media access"})"
        )
        appendNearby(d, canSearch, zone)
    }

    private fun StringBuilder.appendNearby(d: ItemDiagnosis, canSearch: Boolean, zone: ZoneId) {
        val nearby = d.nearby
        if (nearby == null) {
            val why = when {
                !canSearch -> "no media access"
                d.item.dateTaken <= 0 -> "no stored date"
                else -> "not searched"
            }
            appendLine("Nearby files: not searched ($why)")
            return
        }
        appendLine("Nearby files (taken or changed within $NEARBY_WINDOW_HOURS h, nearest first): ${nearby.size}")
        nearby.forEachIndexed { index, file -> appendPhoneFile(index + 1, file, d.item, zone) }
    }

    private fun StringBuilder.appendPhoneFile(number: Int, file: PhoneFile, item: MediaItem, zone: ZoneId) {
        val where = file.relativePath?.let { " in $it" }.orEmpty()
        appendLine("  $number. ${file.displayName ?: "(no name)"}$where, ${file.mimeType ?: "unknown type"}, ${file.width}x${file.height}")
        appendLine("     uri ${file.uri}")
        appendLine("     size ${file.size} (${sizeDelta(file.size, item.fileSize)})")
        appendLine(
            "     taken ${time(file.dateTaken, zone)} (${timeDelta(file.dateTaken, item.dateTaken)}), " +
                "changed ${time(file.dateModified, zone)} (${timeDelta(file.dateModified, item.dateTaken)})"
        )
        val flags = listOfNotNull("trashed".takeIf { file.isTrashed }, "pending".takeIf { file.isPending })
        appendLine("     owner ${file.ownerPackage ?: "unknown"}${flags.joinToString("") { ", $it" }}")
        appendLine("     signature ${file.signature?.let { signatureNote(it, item) } ?: "(can't be read)"}")
    }

    private fun signatureNote(signature: String, item: MediaItem): String =
        "$signature (${if (signature == item.mediaSignature) "same as stored" else "differs from stored"})"

    internal fun sizeDelta(size: Long, storedSize: Long): String = when {
        storedSize <= 0 -> "no stored size"
        size == storedSize -> "same as stored"
        else -> String.format(Locale.ROOT, "%+d bytes, %+.1f%%", size - storedSize, (size - storedSize) * PERCENT / storedSize)
    }

    internal fun timeDelta(time: Long, storedTime: Long): String = when {
        time <= 0 || storedTime <= 0 -> "-"
        else -> String.format(Locale.ROOT, "%+d s", (time - storedTime) / MILLIS_PER_SECOND)
    }

    internal fun time(millis: Long?, zone: ZoneId): String =
        if (millis == null || millis <= 0) {
            "unknown"
        } else {
            "${TIME_FORMAT.format(Instant.ofEpochMilli(millis).atZone(zone).truncatedTo(ChronoUnit.SECONDS))} ($millis)"
        }

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"
}
