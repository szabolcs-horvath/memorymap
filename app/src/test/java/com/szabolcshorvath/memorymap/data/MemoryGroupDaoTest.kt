package com.szabolcshorvath.memorymap.data

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.time.ZonedDateTime

@ExtendWith(RobolectricExtension::class)
class MemoryGroupDaoTest {

    private lateinit var db: MemoryMapDatabase
    private lateinit var dao: MemoryGroupDao

    @BeforeEach
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MemoryMapDatabase::class.java).build()
        dao = db.memoryGroupDao()
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `undoing a delete restores the group, its media and its fragments`() = runBlocking {
        val groupId = dao.insertGroup(group()).toInt()
        dao.insertMediaItems(listOf(media(groupId, "content://media/1", order = 0), media(groupId, "content://media/2", order = 1)))
        dao.insertFragments(listOf(fragment(groupId, "Fragment A", order = 0), fragment(groupId, "Fragment B", order = 1)))
        val deleted = checkNotNull(dao.getGroupWithMedia(groupId))

        dao.deleteGroup(deleted.group)
        assertNull(dao.getGroupWithMedia(groupId))
        assertEquals(0, dao.getAllMediaItems().size, "delete should cascade to media")

        dao.restoreDeletedGroup(deleted)

        val restored = checkNotNull(dao.getGroupWithMedia(groupId)) { "group should be restored" }
        assertEquals(deleted.group, restored.group)
        assertEquals(
            deleted.mediaItems.map { it.copy(id = 0) }.sortedBy { it.order },
            restored.mediaItems.map { it.copy(id = 0) }.sortedBy { it.order }
        )
        assertEquals(
            deleted.fragments.map { it.copy(id = 0) }.sortedBy { it.order },
            restored.fragments.map { it.copy(id = 0) }.sortedBy { it.order }
        )
    }

    @Test
    fun `deleting duplicate media renumbers only the order of the remaining items`() = runBlocking {
        val groupId = dao.insertGroup(group()).toInt()
        dao.insertMediaItems((1..4).map { media(groupId, "content://media/$it", order = it) })
        val inserted = dao.getAllMediaItems().sortedBy { it.order }
        val duplicate = inserted[1] // order 2

        dao.deleteMediaAndRenumber(listOf(duplicate), mapOf(inserted[2].id to 2, inserted[3].id to 3))

        val remaining = dao.getAllMediaItems().sortedBy { it.order }
        assertEquals(listOf(inserted[0].id, inserted[2].id, inserted[3].id), remaining.map { it.id })
        assertEquals(listOf(1, 2, 3), remaining.map { it.order })
        // Nothing but `order` changed.
        assertEquals(listOf(inserted[0], inserted[2], inserted[3]).map { it.copy(order = null) }, remaining.map { it.copy(order = null) })
    }

    @Test
    fun `undoing a delete of a memory without media or fragments restores the group`() = runBlocking {
        val groupId = dao.insertGroup(group()).toInt()
        val deleted = checkNotNull(dao.getGroupWithMedia(groupId))

        dao.deleteGroup(deleted.group)
        dao.restoreDeletedGroup(deleted)

        val restored = checkNotNull(dao.getGroupWithMedia(groupId))
        assertEquals(deleted.group, restored.group)
        assertEquals(0, restored.mediaItems.size)
        assertEquals(0, restored.fragments.size)
    }

    private fun group() = MemoryGroup(
        title = "Trip",
        description = "Description",
        latitude = 47.5,
        longitude = 19.0,
        placeName = "Budapest",
        address = "Hungary",
        startDate = START,
        endDate = START.plusDays(2),
        isAllDay = true,
        markerHue = 120f
    )

    @Test
    fun `re-linking updates a row only while it holds the photo the run read`() = runBlocking {
        val groupId = dao.insertGroup(group()).toInt()
        dao.insertMediaItems(listOf(media(groupId, "content://old/1", order = 0)))
        val seen = dao.getAllMediaItems().single()

        // A restore during the run put another photo under the same id.
        dao.updateMediaItems(listOf(seen.copy(uri = "content://restored/7", mediaSignature = "2048_def")))
        assertEquals(0, dao.updateMediaLink(seen.id, seen.uri, seen.mediaSignature, "content://media/42", "this-phone", seen.mediaSignature, seen.fileSize))
        assertEquals("content://restored/7", dao.getAllMediaItems().single().uri)

        dao.updateMediaItems(listOf(seen))
        assertEquals(1, dao.updateMediaLink(seen.id, seen.uri, seen.mediaSignature, "content://media/42", "this-phone", seen.mediaSignature, seen.fileSize))
        assertEquals(seen.copy(uri = "content://media/42", deviceId = "this-phone"), dao.getAllMediaItems().single())
    }

    @Test
    fun `replacing a media item keeps its id, memory and place, only while it holds the photo the sheet read`() = runBlocking {
        val groupId = dao.insertGroup(group()).toInt()
        dao.insertMediaItems(listOf(media(groupId, "content://picker/1", order = 2)))
        val seen = dao.getAllMediaItems().single()
        val replacement = MediaItem(
            groupId = 0,
            uri = "content://media/external/video/media/8",
            deviceId = "this-phone",
            type = MediaType.VIDEO,
            mediaSignature = "4096_new",
            fileSize = 4096,
            dateTaken = 1_700_000_007_000
        )
        val mediaDao = db.mediaItemDao()

        assertEquals(false, mediaDao.replaceMedia(seen.copy(mediaSignature = "other"), replacement))
        assertEquals(seen, dao.getAllMediaItems().single())

        assertEquals(true, mediaDao.replaceMedia(seen, replacement))
        assertEquals(replacement.copy(id = seen.id, groupId = groupId, order = 2), dao.getAllMediaItems().single())
    }

    @Test
    fun `a thumbnail is stored once per signature and deleted once no item uses it`() = runBlocking {
        val groupId = dao.insertGroup(group()).toInt()
        dao.insertMediaItems(listOf(media(groupId, "content://media/1", order = 1)))
        val thumbnails = db.mediaThumbnailDao()

        thumbnails.insert(MediaThumbnail("1024_abc", byteArrayOf(1)))
        thumbnails.insert(MediaThumbnail("1024_abc", byteArrayOf(2))) // Kept: the same bytes give the same picture.
        thumbnails.insert(MediaThumbnail("2048_unused", byteArrayOf(3)))

        assertEquals(1, thumbnails.deleteUnused())
        assertEquals(listOf(MediaThumbnail("1024_abc", byteArrayOf(1))), thumbnails.getThumbnails(listOf("1024_abc", "2048_unused")))
    }

    @Test
    fun `details are filled in only where unknown, and only while the row holds the photo that was read`() = runBlocking {
        val groupId = dao.insertGroup(group()).toInt()
        dao.insertMediaItems(listOf(media(groupId, "content://media/1", order = 1).copy(displayName = "kept.jpg")))
        val seen = dao.getAllMediaItems().single()
        val read = seen.copy(displayName = "read.jpg", width = 640, height = 480, mimeType = "image/jpeg", perceptualHash = 42)
        val mediaDao = db.mediaItemDao()

        assertEquals(false, mediaDao.fillDetails(seen.copy(uri = "content://media/other"), read))
        assertEquals(seen, dao.getAllMediaItems().single())

        assertEquals(true, mediaDao.fillDetails(seen, read))
        assertEquals(read.copy(displayName = "kept.jpg"), dao.getAllMediaItems().single())
    }

    @Test
    fun `re-linking to an edited version takes its size and signature`() = runBlocking {
        val groupId = dao.insertGroup(group()).toInt()
        dao.insertMediaItems(listOf(media(groupId, "content://picker/1", order = 3)))
        val seen = dao.getAllMediaItems().single()

        assertEquals(1, dao.updateMediaLink(seen.id, seen.uri, seen.mediaSignature, "content://media/1", "this-phone", "900_fed", 900))

        assertEquals(
            seen.copy(uri = "content://media/1", deviceId = "this-phone", mediaSignature = "900_fed", fileSize = 900),
            dao.getAllMediaItems().single()
        )
    }

    private fun media(groupId: Int, uri: String, order: Int) = MediaItem(
        groupId = groupId,
        uri = uri,
        deviceId = "device",
        type = MediaType.IMAGE,
        mediaSignature = "1024_abc",
        fileSize = 1024,
        dateTaken = 1_700_000_000_000,
        order = order
    )

    private fun fragment(groupId: Int, placeName: String, order: Int) = MemoryFragment(
        groupId = groupId,
        latitude = 47.6,
        longitude = 19.1,
        placeName = placeName,
        address = null,
        startDate = START.plusDays(1),
        endDate = START.plusDays(1).plusHours(3),
        markerHue = 200f,
        order = order
    )

    private companion object {
        val START: ZonedDateTime = ZonedDateTime.parse("2026-01-04T10:00:00+01:00[Europe/Budapest]")
    }
}
