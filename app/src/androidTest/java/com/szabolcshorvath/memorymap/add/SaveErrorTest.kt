package com.szabolcshorvath.memorymap.add

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.screens.AddScreen
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.Dialog
import com.szabolcshorvath.memorymap.screens.Flows
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.Snackbar
import com.szabolcshorvath.memorymap.screens.TimelineScreen
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Saving an edit of a memory that was deleted while the edit was open (on another tab). The edit must not be lost:
 * it ends up in the timeline, as a new memory.
 */
@RunWith(AndroidJUnit4::class)
class SaveErrorTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun anEditOfADeletedMemoryIsNotLost() = editDeleteSave(withPhoto = false)

    @Test
    fun anEditOfADeletedMemoryWithAPhotoIsNotLost() = editDeleteSave(withPhoto = true)

    /**
     * Edits memory [TRIP] and renames it, deletes it from the timeline (for good), then saves the edit. [withPhoto]: the
     * memory has a photo, which the new memory keeps.
     */
    private fun editDeleteSave(withPhoto: Boolean) {
        val id = TestData.memory(TRIP)
        if (withPhoto) TestData.photo(id)
        app.launch()
        Flows.openMemory(TRIP)
        MemoryScreen.edit()
        AddScreen.typeTitle(RENAMED)

        Flows.openMemory(TRIP)
        MemoryScreen.delete()
        Dialog.tap("Delete")
        Snackbar.assertShown("Memory deleted")
        Snackbar.assertClosesByItself("Memory deleted")

        BottomNav.open(Tab.ADD)
        AddScreen.assertTitle(RENAMED)
        AddScreen.save()

        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.assertMemoryShown(RENAMED)
        if (withPhoto) {
            TimelineScreen.openMemory(RENAMED)
            MemoryScreen.assertPhotoLoaded()
        }
    }

    private companion object {
        const val TRIP = "Vienna trip"
        const val RENAMED = "Vienna weekend"
    }
}
