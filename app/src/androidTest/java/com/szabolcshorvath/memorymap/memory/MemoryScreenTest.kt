package com.szabolcshorvath.memorymap.memory

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.Dialog
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.Snackbar
import com.szabolcshorvath.memorymap.screens.TimelineScreen
import com.szabolcshorvath.memorymap.screens.Ui
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import com.szabolcshorvath.memorymap.testing.TestData.Place
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An opened memory: swiping between memories, deleting with Cancel, Undo and for good, and reordering its photos and
 * fragments. (A photo from another phone is in `MediaAccessTest`, which re-links it.)
 */
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
    fun deleteAsksFirstAndUndoBringsTheMemoryBackWithItsPhotoAndFragment() {
        // "Memory deleted" stays about 3 seconds, which a busy emulator can spend before the tap on Undo arrives.
        app.allowTimeToTakeAction(UNDO_SECONDS)
        val memory = TestData.memory(MIDDLE)
        TestData.photo(memory)
        TestData.fragment(memory, Place.VIENNA)
        TestData.memory(NEWEST, daysAgo = 0)
        open(MIDDLE)
        MemoryScreen.assertPhotoLoaded()
        MemoryScreen.assertFragmentCount(1)

        MemoryScreen.delete()
        Dialog.assertShown(DELETE_TITLE)
        Dialog.tap("Cancel")
        Dialog.assertGone(DELETE_TITLE)
        MemoryScreen.assertShowing(MIDDLE)

        deleteOpenMemory()
        TimelineScreen.assertMemoryGone(MIDDLE)
        Snackbar.tap("Undo")

        TimelineScreen.assertMemoryShown(MIDDLE)
        TimelineScreen.openMemory(MIDDLE)
        MemoryScreen.assertShowing(MIDDLE)
        MemoryScreen.assertPhotoLoaded()
        MemoryScreen.assertFragmentCount(1)
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
    fun draggingAPhotoOrAFragmentChangesTheirOrderForGood() {
        val memory = TestData.memory(MIDDLE)
        TestData.photo(memory, Color.RED, order = 0)
        TestData.photo(memory, Color.BLUE, order = 1)
        TestData.fragment(memory, Place.VIENNA, order = 0)
        TestData.fragment(memory, Place.ATHENS, order = 1)
        open(MIDDLE)
        MemoryScreen.assertPhotoCount(2)
        MemoryScreen.assertPhotoColors(Color.RED, Color.BLUE)
        MemoryScreen.assertFragmentsTopToBottom(VIENNA, ATHENS)

        MemoryScreen.dragPhoto(from = 1, onto = 0)
        MemoryScreen.assertPhotoColors(Color.BLUE, Color.RED)
        MemoryScreen.dragFragment(ATHENS, onto = VIENNA)
        MemoryScreen.assertFragmentsTopToBottom(ATHENS, VIENNA)

        Ui.pressBack()
        TimelineScreen.openMemory(MIDDLE)
        MemoryScreen.assertShowing(MIDDLE)
        MemoryScreen.assertPhotoCount(2)
        MemoryScreen.assertPhotoColors(Color.BLUE, Color.RED)
        MemoryScreen.assertFragmentsTopToBottom(ATHENS, VIENNA)
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
        const val UNDO_SECONDS = 15
        val VIENNA = Place.VIENNA.name!!
        val ATHENS = Place.ATHENS.name!!
    }
}
