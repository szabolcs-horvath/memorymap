package com.szabolcshorvath.memorymap.ui.add

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.szabolcshorvath.memorymap.appContainer
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.data.MemoryFragment
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.MemoryGroupWithMedia
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentLinkedQueue

@ExtendWith(RobolectricExtension::class)
class AddMemoryViewModelTest {

    // Created on first use: the Robolectric application only exists once a test runs.
    private val viewModel by lazy { newForm() }

    private fun newForm() = RuntimeEnvironment.getApplication().let { AddMemoryViewModel(it, SavedStateHandle(), it.appContainer.memories) }

    /** The URIs the form asked to release, in the background. (Grants that saved media use: `UriGrantReleaseTest`.) */
    private val released = ConcurrentLinkedQueue<String>()

    @BeforeEach
    fun recordReleases() {
        viewModel.releaseGrants = { released += it }
    }

    @Test
    fun `a new form has no unsaved changes`() {
        assertFalse(viewModel.hasUnsavedChanges())
    }

    @Test
    fun `typing a title is a change, and deleting it again is not`() {
        viewModel.title = "Trip"
        assertTrue(viewModel.hasUnsavedChanges())
        viewModel.title = null
        assertFalse(viewModel.hasUnsavedChanges())
    }

    @Test
    fun `picking a location, a colour, a date or media is a change`() {
        val changes = listOf<AddMemoryViewModel.() -> Unit>(
            {
                lat = 47.5
                lng = 19.05
            },
            { markerHue = 120f },
            { endDateTime = endDateTime.plusDays(1) },
            { isAllDay = true },
            { selectedMedia += SelectedMedia(Uri.parse("content://media/1"), MediaType.IMAGE, "device") }
        )
        for (change in changes) {
            val form = newForm()
            form.change()
            assertTrue(form.hasUnsavedChanges())
        }
    }

    @Test
    fun `expanding a fragment's sections is not a change, moving it is`() {
        viewModel.fragments += FragmentEditState(lat = 47.5, lng = 19.05)
        viewModel.markUnchanged()

        viewModel.fragments[0] = viewModel.fragments[0].copy(isColorExpanded = true)
        assertFalse(viewModel.hasUnsavedChanges())

        viewModel.fragments[0] = viewModel.fragments[0].copy(lat = 48.0)
        assertTrue(viewModel.hasUnsavedChanges())
    }

    @Test
    fun `a memory loaded for editing is unchanged until the user changes it`() {
        viewModel.fillForEditing(loadedMemory())
        viewModel.markUnchanged()
        assertFalse(viewModel.hasUnsavedChanges())

        viewModel.description = "With friends"
        assertTrue(viewModel.hasUnsavedChanges())
    }

    @Test
    fun `a loaded memory fills the form, with media and fragments in their saved order`() {
        viewModel.fillForEditing(loadedMemory())
        assertEquals(7, viewModel.editingMemoryId)
        assertEquals("Trip", viewModel.title)
        assertEquals(listOf("content://media/1", "content://media/2"), viewModel.selectedMedia.map { it.uri.toString() })
        assertEquals(listOf("Castle", "Bridge"), viewModel.fragments.map { it.placeName })
    }

    @Test
    fun `clearing the form leaves no unsaved changes`() {
        viewModel.editingMemoryId = 7
        viewModel.title = "Trip"
        viewModel.resetForm()
        assertFalse(viewModel.hasUnsavedChanges())
    }

    @Test
    fun `removing a picked photo releases its grant, and the photo left in the form keeps it`() {
        pick(PICKED_1, PICKED_2)

        viewModel.removeMedia(viewModel.selectedMedia.single { it.uri.toString() == PICKED_1 })

        awaitReleased(PICKED_1)
    }

    @Test
    fun `clearing the form releases the grants of the photos picked in it`() {
        pick(PICKED_1, PICKED_2)

        viewModel.resetForm()

        awaitReleased(PICKED_1, PICKED_2)
    }

    @Test
    fun `loading a memory for editing releases the grants of the photos picked before`() {
        pick(PICKED_1)

        viewModel.fillForEditing(loadedMemory())

        awaitReleased(PICKED_1)
    }

    @Test
    fun `media loaded for editing are never released by the form`() {
        // They may belong to a memory whose delete can still be undone; a save releases what an edit removed.
        viewModel.fillForEditing(loadedMemory())
        viewModel.removeMedia(viewModel.selectedMedia.first())
        viewModel.resetForm()
        pick(PICKED_1)

        viewModel.removeMedia(viewModel.selectedMedia.single { it.uri.toString() == PICKED_1 })

        awaitReleased(PICKED_1) // and nothing before it
    }

    /** Picks [uris] as the photo picker does (the pick takes a persisted grant of each). */
    private fun pick(vararg uris: String) {
        viewModel.onMediaPicked(uris.toList())
        viewModel.selectedMedia += uris.map { SelectedMedia(Uri.parse(it), MediaType.IMAGE, "device") }
    }

    /** Waits until the form has asked to release exactly [expected]: the release runs in the background. */
    private fun awaitReleased(vararg expected: String) {
        val deadline = System.currentTimeMillis() + RELEASE_TIMEOUT_MS
        while (released.size < expected.size && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MS)
        assertEquals(expected.sorted(), released.sorted())
    }

    private fun loadedMemory() = MemoryGroupWithMedia(
        group = MemoryGroup(
            id = 7,
            title = "Trip",
            description = null,
            latitude = 47.5,
            longitude = 19.05,
            placeName = "Budapest",
            address = "Hungary",
            startDate = START,
            endDate = START.plusDays(2),
            isAllDay = false,
            markerHue = 120f
        ),
        mediaItems = listOf(media("content://media/2", order = 1), media("content://media/1", order = 0)),
        fragments = listOf(fragment("Bridge", order = 1), fragment("Castle", order = 0))
    )

    private fun media(uri: String, order: Int) = MediaItem(
        groupId = 7,
        uri = uri,
        deviceId = "device",
        type = MediaType.IMAGE,
        mediaSignature = "1024_abc",
        fileSize = 1024,
        dateTaken = 1_700_000_000_000,
        order = order
    )

    private fun fragment(placeName: String, order: Int) = MemoryFragment(
        groupId = 7,
        latitude = 47.5,
        longitude = 19.04,
        placeName = placeName,
        address = null,
        startDate = null,
        endDate = null,
        markerHue = 200f,
        order = order
    )

    private companion object {
        const val PICKED_1 = "content://media/picker/0/com.android.providers.media.photopicker/media/31"
        const val PICKED_2 = "content://media/picker/0/com.android.providers.media.photopicker/media/32"
        const val RELEASE_TIMEOUT_MS = 5_000L
        const val POLL_MS = 20L
        val START: ZonedDateTime = ZonedDateTime.parse("2026-01-04T10:00:00+01:00[Europe/Budapest]")
    }
}
