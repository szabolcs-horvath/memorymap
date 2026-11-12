package com.szabolcshorvath.memorymap.ui.add

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.work.testing.WorkManagerTestInitHelper
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.MemoryGroupDao
import com.szabolcshorvath.memorymap.data.MemoryGroupWithMedia
import com.szabolcshorvath.memorymap.data.MemoryMapDatabase
import com.szabolcshorvath.memorymap.data.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.milliseconds

/**
 * What saving the Add form does with its photos: the same photo is stored once, a memory's photos are numbered without
 * gaps, photos kept through an edit keep what was stored about them, and an edit gives up the grants of the photos it
 * removed, unless another memory still shows them. (What the form releases before a save: `AddMemoryViewModelTest`.)
 */
@ExtendWith(RobolectricExtension::class)
class SaveMediaTest {

    private lateinit var db: MemoryMapDatabase
    private lateinit var dao: MemoryGroupDao
    private val context get() = RuntimeEnvironment.getApplication()
    private val form by lazy { AddMemoryViewModel(context, SavedStateHandle(), MemoryRepository(context, db)).also { it.releaseGrants = {} } }

    @BeforeEach
    fun setUp() {
        // A save finishes on the main thread. Robolectric gives every test a new one, while Dispatchers.Main would keep
        // the first test's.
        Dispatchers.setMain(Handler(Looper.getMainLooper()).asCoroutineDispatcher())
        db = Room.inMemoryDatabaseBuilder(context, MemoryMapDatabase::class.java).allowMainThreadQueries().build()
        dao = db.memoryGroupDao()
        // A save schedules an automatic backup; the test WorkManager only records it.
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @AfterEach
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `the same photo picked through two links is saved once, and the photos are numbered without gaps`() {
        val beach = photoFile("beach.jpg", BEACH_BYTES)
        val beachAgain = photoFile("beach-copy.jpg", BEACH_BYTES)
        val castle = photoFile("castle.jpg", CASTLE_BYTES)
        newMemory()
        form.selectedMedia += listOf(beach, beachAgain, castle).map { picked(it) }

        val saved = save()

        assertEquals(listOf(beach.toString(), castle.toString()), saved.media().map { it.uri })
        assertEquals(listOf(1, 2), saved.media().map { it.order })
        val signatures = saved.media().map { it.mediaSignature }
        assertTrue(signatures.all { it.isNotEmpty() } && signatures.distinct().size == 2, "Signatures: $signatures")
    }

    @Test
    fun `a photo whose file can't be read is still saved, once`() {
        val gone = File(context.cacheDir, "gone.jpg").toUri()
        newMemory()
        form.selectedMedia += listOf(picked(gone), picked(gone))

        val saved = save()

        assertEquals(listOf(gone.toString()), saved.media().map { it.uri })
        assertEquals("", saved.media().single().mediaSignature)
    }

    @Test
    fun `photos kept through an edit keep their signature and details, in the order the form shows`() {
        val id = storedMemory(stored(PICKER_1, signature = "100_aaa", order = 1), stored(PICKER_2, signature = "200_bbb", order = 2))
        fillFormForEditing(id)
        form.selectedMedia.reverse()

        val saved = save()

        assertEquals(id, saved.group.id)
        assertEquals(listOf(PICKER_2, PICKER_1), saved.media().map { it.uri })
        assertEquals(listOf(1, 2), saved.media().map { it.order })
        val kept = saved.media().single { it.uri == PICKER_1 }
        assertEquals("100_aaa", kept.mediaSignature)
        assertEquals(100L, kept.fileSize)
        assertEquals("IMG_100_aaa.jpg", kept.displayName)
        assertEquals(4032, kept.width)
        assertEquals(0x0123_4567_89AB_CDEFL, kept.perceptualHash)
    }

    @Test
    fun `an edit gives up the grants of the photos it removed, but not of one another memory still shows`() {
        val id = storedMemory(stored(PICKER_1, "100_aaa", 1), stored(PICKER_2, "200_bbb", 2), stored(PICKER_3, "300_ccc", 3))
        storedMemory(stored(PICKER_3, "300_ccc", 1))
        grant(PICKER_1, PICKER_2, PICKER_3, UNRELATED)
        fillFormForEditing(id)
        form.selectedMedia.removeAll { it.uri.toString() != PICKER_1 }

        val saved = save()

        assertEquals(listOf(PICKER_1), saved.media().map { it.uri })
        assertEquals(setOf(PICKER_1, PICKER_3, UNRELATED), grantedUris())
    }

    @Test
    fun `two links to the same photo in one memory become one item, and the dropped link's grant is given up`() {
        // As after re-linking: two items of one memory may end up pointing to the same file.
        val id = storedMemory(stored(PICKER_1, "100_aaa", 1), stored(PICKER_2, "100_aaa", 2), stored(PICKER_3, "300_ccc", 3))
        grant(PICKER_1, PICKER_2, PICKER_3)
        fillFormForEditing(id)

        val saved = save()

        assertEquals(listOf(PICKER_1, PICKER_3), saved.media().map { it.uri })
        assertEquals(listOf(1, 2), saved.media().map { it.order })
        assertEquals(setOf(PICKER_1, PICKER_3), grantedUris())
    }

    @Test
    fun `saving a new memory gives up no grants`() {
        grant(PICKER_1)
        newMemory()
        form.selectedMedia += SelectedMedia(PICKER_1.toUri(), MediaType.IMAGE, DEVICE, 100, DATE_TAKEN, "100_aaa")

        val saved = save()

        assertEquals(listOf(PICKER_1), saved.media().map { it.uri })
        assertEquals(setOf(PICKER_1), grantedUris())
    }

    private fun newMemory() {
        form.title = "Trip"
        form.lat = 47.5
        form.lng = 19.05
    }

    /** Saves the form as the Save button does, and returns the saved memory. */
    private fun save(): MemoryGroupWithMedia {
        form.saveMemoryGroup()
        // The save runs in the background and finishes on the main thread, which the test thread stands in for.
        val deadline = System.currentTimeMillis() + SAVE_TIMEOUT_MS
        while (form.isSaving.value && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(POLL_MS)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return runBlocking {
            val result = withTimeout(SAVE_TIMEOUT_MS.milliseconds) { form.saveResult.first() }
            check(result is AddMemoryViewModel.SaveResult.Success) { "The save failed: $result" }
            dao.getGroupWithMedia(result.groupId)!!
        }
    }

    private fun MemoryGroupWithMedia.media() = mediaItems.sortedWith(MediaItem.DISPLAY_ORDER)

    private fun picked(uri: Uri) = SelectedMedia(uri, MediaType.IMAGE, DEVICE)

    private fun photoFile(name: String, bytes: ByteArray): Uri = File(context.cacheDir, name).apply { writeBytes(bytes) }.toUri()

    private fun storedMemory(vararg media: MediaItem): Int = runBlocking {
        val id = dao.insertGroup(group()).toInt()
        dao.insertMediaItems(media.map { it.copy(groupId = id) })
        id
    }

    /** Opens stored memory [id] in the form, as Edit does. */
    private fun fillFormForEditing(id: Int) = form.fillForEditing(runBlocking { dao.getGroupWithMedia(id)!! })

    private fun stored(uri: String, signature: String, order: Int) = MediaItem(
        groupId = 0,
        uri = uri,
        deviceId = DEVICE,
        type = MediaType.IMAGE,
        mediaSignature = signature,
        fileSize = signature.substringBefore('_').toLong(),
        dateTaken = DATE_TAKEN,
        order = order,
        displayName = "IMG_$signature.jpg",
        width = 4032,
        height = 3024,
        mimeType = "image/jpeg",
        perceptualHash = 0x0123_4567_89AB_CDEFL
    )

    private fun group() = MemoryGroup(
        title = "Trip",
        description = null,
        latitude = 47.5,
        longitude = 19.05,
        placeName = null,
        address = null,
        startDate = START,
        endDate = START.plusDays(1),
        isAllDay = true
    )

    private fun grant(vararg uris: String) = uris.forEach {
        context.contentResolver.takePersistableUriPermission(it.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun grantedUris() = context.contentResolver.persistedUriPermissions.mapTo(HashSet()) { it.uri.toString() }

    private companion object {
        const val PICKER = "content://media/picker/0/com.android.providers.media.photopicker/media/"
        const val PICKER_1 = "${PICKER}1"
        const val PICKER_2 = "${PICKER}2"
        const val PICKER_3 = "${PICKER}3"
        const val UNRELATED = "${PICKER}99"
        const val DEVICE = "device"
        const val DATE_TAKEN = 1_700_000_000_000L
        const val SAVE_TIMEOUT_MS = 10_000L
        const val POLL_MS = 20L
        val BEACH_BYTES = ByteArray(10_000) { (it % 251).toByte() }
        val CASTLE_BYTES = ByteArray(12_000) { (it % 241).toByte() }
        val START: ZonedDateTime = ZonedDateTime.parse("2026-01-04T10:00:00+01:00[Europe/Budapest]")
    }
}
