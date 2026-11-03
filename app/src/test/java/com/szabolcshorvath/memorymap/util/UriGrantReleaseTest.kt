package com.szabolcshorvath.memorymap.util

import android.content.Intent
import androidx.core.net.toUri
import androidx.room.Room
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.MemoryGroupDao
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.time.ZonedDateTime

@ExtendWith(RobolectricExtension::class)
class UriGrantReleaseTest {

    private lateinit var db: MemoryMapDatabase
    private lateinit var dao: MemoryGroupDao
    private val context get() = RuntimeEnvironment.getApplication()

    @BeforeEach
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, MemoryMapDatabase::class.java).build()
        dao = db.memoryGroupDao()
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `releases only the given grants that no item uses`() = runBlocking {
        val stillUsed = "${PICKER}1" // re-linked in one memory, still in another
        val replaced = "${PICKER}2"
        val notAsked = "${PICKER}3" // maybe needed by a newer backup: never swept
        val groupId = dao.insertGroup(group()).toInt()
        dao.insertMediaItems(listOf(media(groupId, stillUsed), media(groupId, "content://media/external/images/media/20")))
        listOf(stillUsed, replaced, notAsked).forEach {
            context.contentResolver.takePersistableUriPermission(it.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        LocalMediaUtil.releaseUriGrantsIfUnused(context, dao, listOf(stillUsed, replaced))

        assertEquals(setOf(stillUsed, notAsked), context.contentResolver.persistedUriPermissions.mapTo(HashSet()) { it.uri.toString() })
    }

    private fun group() = MemoryGroup(
        title = "Trip",
        description = null,
        latitude = 47.5,
        longitude = 19.0,
        placeName = null,
        address = null,
        startDate = START,
        endDate = START.plusDays(1),
        isAllDay = true
    )

    private fun media(groupId: Int, uri: String) = MediaItem(
        groupId = groupId,
        uri = uri,
        deviceId = "device",
        type = MediaType.IMAGE,
        mediaSignature = "1024_abc",
        fileSize = 1024,
        dateTaken = 1_700_000_000_000
    )

    private companion object {
        const val PICKER = "content://media/picker/0/com.android.providers.media.photopicker/media/"
        val START: ZonedDateTime = ZonedDateTime.parse("2026-01-04T10:00:00+01:00[Europe/Budapest]")
    }
}
