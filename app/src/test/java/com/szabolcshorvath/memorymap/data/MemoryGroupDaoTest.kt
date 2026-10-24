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
        // The public memoryGroupDao() wraps the DAO in Firebase traces, which need a configured FirebaseApp.
        dao = MemoryMapDatabase::class.java.getDeclaredMethod("memoryGroupDaoInternal")
            .apply { isAccessible = true }
            .invoke(db) as MemoryGroupDao
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
