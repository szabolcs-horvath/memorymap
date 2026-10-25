package com.szabolcshorvath.memorymap.fragment

import android.net.Uri
import com.szabolcshorvath.memorymap.adapter.MemoryFragmentEditAdapter.FragmentEditState
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.data.MemoryFragment
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.MemoryGroupWithMedia
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.time.ZonedDateTime

@ExtendWith(RobolectricExtension::class)
class AddMemoryGroupFragmentViewModelTest {

    // Created on first use: the Robolectric application only exists once a test runs.
    private val viewModel by lazy { newForm() }

    private fun newForm() = AddMemoryGroupFragmentViewModel(RuntimeEnvironment.getApplication())

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
        val changes = listOf<AddMemoryGroupFragmentViewModel.() -> Unit>(
            {
                lat = 47.5
                lng = 19.05
            },
            { markerHue = 120f },
            { endDateTime = endDateTime.plusDays(1) },
            { isAllDay = true },
            { selectedMedia += AddMemoryGroupFragment.SelectedMedia(Uri.parse("content://media/1"), MediaType.IMAGE, "device") }
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

        viewModel.fragments[0] = viewModel.fragments[0].copy(isDateExpanded = true, isColorExpanded = true)
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
        val START: ZonedDateTime = ZonedDateTime.parse("2026-01-04T10:00:00+01:00[Europe/Budapest]")
    }
}
