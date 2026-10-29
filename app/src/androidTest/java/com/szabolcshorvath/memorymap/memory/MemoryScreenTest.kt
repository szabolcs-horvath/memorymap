package com.szabolcshorvath.memorymap.memory

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.Dialog
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.Snackbar
import com.szabolcshorvath.memorymap.screens.TimelineScreen
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** An opened memory: swiping between memories, deleting with undo, and its photos. */
@RunWith(AndroidJUnit4::class)
class MemoryScreenTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun swipesBetweenMemoriesInTimelineOrder() {
        TestData.memory(NEWEST, daysAgo = 1)
        TestData.memory(MIDDLE, daysAgo = 3)
        TestData.memory(OLDEST, daysAgo = 5)
        open(MIDDLE)

        MemoryScreen.swipeToOlder()
        MemoryScreen.assertShowing(OLDEST)

        MemoryScreen.swipeToNewer()
        MemoryScreen.assertShowing(MIDDLE)
        MemoryScreen.swipeToNewer()
        MemoryScreen.assertShowing(NEWEST)
    }

    @Test
    fun deleteAsksFirstAndCancelKeepsTheMemory() {
        TestData.memory(MIDDLE)
        open(MIDDLE)

        MemoryScreen.delete()
        Dialog.assertShown(DELETE_TITLE)
        Dialog.tap("Cancel")

        Dialog.assertGone(DELETE_TITLE)
        MemoryScreen.assertShowing(MIDDLE)
    }

    @Test
    fun undoBringsADeletedMemoryBackWithItsPhoto() {
        TestData.photo(TestData.memory(MIDDLE))
        TestData.memory(NEWEST, daysAgo = 0)
        open(MIDDLE)
        MemoryScreen.assertPhotoLoaded()

        deleteOpenMemory()
        TimelineScreen.assertMemoryGone(MIDDLE)
        Snackbar.tap("Undo")

        TimelineScreen.assertMemoryShown(MIDDLE)
        TimelineScreen.openMemory(MIDDLE)
        MemoryScreen.assertShowing(MIDDLE)
        MemoryScreen.assertPhotoLoaded()
    }

    @Test
    fun deleteIsFinalOnceTheMessageCloses() {
        TestData.photo(TestData.memory(MIDDLE))
        open(MIDDLE)

        deleteOpenMemory()
        Snackbar.assertClosesByItself(DELETED)

        TimelineScreen.assertMemoryGone(MIDDLE)
        TimelineScreen.assertEmpty()
    }

    @Test
    fun aPhotoFromAnotherPhoneShowsItsDetailsInsteadOfThePicture() {
        TestData.photoFromAnotherDevice(TestData.memory(MIDDLE))
        open(MIDDLE)

        MemoryScreen.assertPhotoFromAnotherDevice()
    }

    private fun open(title: String) {
        app.launch()
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.openMemory(title)
        MemoryScreen.assertShowing(title)
    }

    /** Deletes the open memory, which goes back to the timeline with an Undo message. */
    private fun deleteOpenMemory() {
        MemoryScreen.delete()
        Dialog.tap("Delete")
        MemoryScreen.assertGone()
        TimelineScreen.assertShown()
        Snackbar.assertShown(DELETED)
    }

    private companion object {
        const val NEWEST = "Danube walk"
        const val MIDDLE = "Vienna trip"
        const val OLDEST = "Lake day"
        const val DELETE_TITLE = "Delete Memory"
        const val DELETED = "Memory deleted"
    }
}
