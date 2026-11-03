package com.szabolcshorvath.memorymap.util

import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

class RelinkReportTest {

    private val taken = 1_760_000_000_000L

    private fun item(deviceId: String = "old-id", size: Long = 5_300_000, signature: String = "5300000_abc") = MediaItem(
        id = 12,
        groupId = 3,
        uri = "content://media/picker/0/com.android.providers.media.photopicker/media/4990",
        deviceId = deviceId,
        type = MediaType.IMAGE,
        mediaSignature = signature,
        fileSize = size,
        dateTaken = taken
    )

    private fun file(uri: String, dateTaken: Long, dateModified: Long = 0, size: Long = 4_600_000) = PhoneFile(
        uri = uri,
        displayName = "IMG_1.jpg",
        relativePath = "DCIM/Camera/",
        mimeType = "image/jpeg",
        size = size,
        dateTaken = dateTaken,
        dateModified = dateModified,
        width = 4000,
        height = 3000,
        ownerPackage = "com.google.android.apps.photos",
        isTrashed = true,
        isPending = false,
        signature = "4600000_def"
    )

    @Test
    fun `reasons list every cause, not only the first`() {
        assertEquals(
            listOf(RelinkReason.OTHER_INSTALLATION, RelinkReason.URI_UNREADABLE),
            relinkReasons(item(), "new-id", StoredUriState.Unreadable("SecurityException: denied"))
        )
    }

    @Test
    fun `a readable URI of the same file from this installation needs no re-link`() {
        assertTrue(relinkReasons(item(deviceId = "id"), "id", StoredUriState.Readable(5_300_000, 0)).isEmpty())
    }

    @Test
    fun `a readable URI with another size and date is a different file`() {
        assertEquals(
            listOf(RelinkReason.DIFFERENT_FILE),
            relinkReasons(item(deviceId = "id"), "id", StoredUriState.Readable(4_600_000, taken + 1))
        )
    }

    @Test
    fun `nearest files come first by capture or change time, without duplicates`() {
        val far = file("content://media/external/images/media/1", dateTaken = taken + 3_600_000)
        val changedNear = file("content://media/external/images/media/2", dateTaken = 0, dateModified = taken - 2_000)
        val takenNear = file("content://media/external/images/media/3", dateTaken = taken + 1_000)

        val result = nearest(listOf(far, changedNear, takenNear, takenNear), taken, limit = 2)

        assertEquals(listOf(takenNear, changedNear), result)
    }

    @Test
    fun `size and time deltas are signed`() {
        assertEquals("-700000 bytes, -13.2%", RelinkReportFormatter.sizeDelta(4_600_000, 5_300_000))
        assertEquals("same as stored", RelinkReportFormatter.sizeDelta(5_300_000, 5_300_000))
        assertEquals("no stored size", RelinkReportFormatter.sizeDelta(5_300_000, 0))
        assertEquals("+12 s", RelinkReportFormatter.timeDelta(taken + 12_000, taken))
        assertEquals("-", RelinkReportFormatter.timeDelta(0, taken))
    }

    @Test
    fun `uri kinds tell picker and MediaStore URIs apart`() {
        assertEquals("photo picker", uriKind("content://media/picker_get_content/0/media/7"))
        assertEquals("MediaStore", uriKind("content://media/external/images/media/38"))
        assertEquals("other", uriKind("file:///sdcard/a.jpg"))
    }

    @Test
    fun `grant usage counts picker items with their grant and grants no item uses`() {
        val picker = "content://media/picker/0/com.android.providers.media.photopicker/media/"
        val items = listOf(
            item().copy(id = 1, uri = "${picker}1"),
            item().copy(id = 2, uri = "${picker}1"), // the same photo in another memory
            item().copy(id = 3, uri = "${picker}2"),
            item().copy(id = 4, uri = "content://media/external/images/media/38")
        )
        val grants = setOf("${picker}1", "${picker}9", "content://com.example.documents/document/3")

        val usage = grantUsage(items, grants)

        assertEquals(mapOf("photo picker" to 3, "MediaStore" to 1), usage.itemUriKinds)
        assertEquals(3, usage.pickerItems)
        assertEquals(2, usage.pickerItemsWithGrant)
        assertEquals(2, usage.distinctPickerUris)
        assertEquals(mapOf("photo picker" to 1, "other" to 1), usage.unusedGrants)
    }

    @Test
    fun `report shows the summary, the item line and the nearby files`() {
        val diagnosis = ItemDiagnosis(
            item = item(),
            reasons = listOf(RelinkReason.OTHER_INSTALLATION, RelinkReason.URI_UNREADABLE),
            stored = StoredUriState.Unreadable("SecurityException: denied"),
            hasGrant = false,
            storedUriSignature = null,
            sameSize = SameSizeCheck(files = 0, signatureMatches = 0),
            editedVersion = EditedVersionCheck("content://media/external/images/media/4990", StoredUriState.Readable(4_900_000, taken), wouldLink = true),
            nearby = listOf(file("content://media/external/images/media/5000", dateTaken = taken))
        )
        val report = RelinkReport(
            generatedAt = taken,
            installation = InstallationInfo("new-id", idFileWrittenAt = taken, firstInstalledAt = null, lastUpdatedAt = taken),
            access = MediaAccessInfo(
                listOf("android.permission.READ_MEDIA_IMAGES" to true),
                117,
                grantUsage = grantUsage(listOf(item()), setOf("content://media/picker/0/com.android.providers.media.photopicker/media/1")),
                visibleImages = 900,
                visibleVideos = 40
            ),
            totalItems = 120,
            deviceIdCounts = mapOf("old-id" to 120),
            diagnoses = listOf(diagnosis)
        )

        val text = RelinkReportFormatter.render(report, ZoneOffset.UTC)

        listOf(
            "READ_MEDIA_IMAGES: granted",
            "App first installed: unknown",
            "  new-id (this installation): 0",
            "  old-id: 120",
            "  saved by another installation: 1",
            "Stored URI kinds: photo picker 1",
            "Stored URI kinds (all items): photo picker 1",
            "Photo picker items with a grant: 0 of 1 (1 distinct URIs)",
            "Grants no item uses: 1 (photo picker 1)",
            "Same-size file on this phone: 0 of 1 searched",
            "Edited version under the picker id (would re-link): 1 of 1 checked",
            "#12  group 3  IMAGE  photo picker  size 5300000  taken 2025-10-09T08:53:20Z (1760000000000)",
            "edited yes",
            "Edited version: content://media/external/images/media/4990: size 4900000, taken 2025-10-09T08:53:20Z (1760000000000) (+0 s), would re-link",
            "Stored URI now: can't be read (SecurityException: denied)",
            "Device id: old-id (another installation)",
            "size 4600000 (-700000 bytes, -13.2%)",
            "taken 2025-10-09T08:53:20Z (1760000000000) (+0 s)",
            "owner com.google.android.apps.photos, trashed",
            "signature 4600000_def (differs from stored)"
        ).forEach { assertTrue(it in text, "missing: $it\n$text") }
    }

    @Test
    fun `report without items to re-link has no item sections`() {
        val report = RelinkReport(
            generatedAt = taken,
            installation = InstallationInfo("id", null, null, null),
            access = MediaAccessInfo(emptyList(), 0, grantUsage(emptyList(), emptySet()), visibleImages = null, visibleVideos = null),
            totalItems = 4,
            deviceIdCounts = mapOf("id" to 4),
            diagnoses = emptyList()
        )

        val text = RelinkReportFormatter.render(report, ZoneOffset.UTC)

        assertTrue("Need re-linking: 0" in text)
        assertTrue("Photos visible in MediaStore: not readable" in text)
        assertTrue("Grants no item uses: 0 (none)" in text)
        assertFalse("== Items that need re-linking ==" in text)
        assertFalse("== Details" in text)
    }
}
