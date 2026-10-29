package com.szabolcshorvath.memorymap.timeline

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.TimelineScreen
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import com.szabolcshorvath.memorymap.testing.TestData.Place
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** The Timeline tab: the list of memories by day, its search, and scrolling to a memory. */
@RunWith(AndroidJUnit4::class)
class TimelineTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun showsMemoriesNewestFirstUnderTheirDay() {
        TestData.memory("Morning run", daysAgo = 2, hour = 9)
        TestData.memory("Evening concert", daysAgo = 2, hour = 19)
        TestData.memory("Lake day", daysAgo = 5)
        openTimeline()

        val today = LocalDate.now()
        TimelineScreen.assertTopToBottom(
            TimelineScreen.dayHeader(today.minusDays(2)),
            "Evening concert",
            "Morning run",
            TimelineScreen.dayHeader(today.minusDays(5)),
            "Lake day",
        )
    }

    @Test
    fun noMemoriesShowsAMessage() {
        openTimeline()

        TimelineScreen.assertEmpty()
    }

    @Test
    fun searchFindsTitlesDescriptionsPlacesAndAddresses() {
        TestData.memory(PICNIC, description = "Sunny afternoon by the lake")
        TestData.memory(CONCERT, place = Place(48.2030, 16.3690, "Vienna", "Opernring 2"))
        TestData.memory(DINNER)
        openTimeline()

        expectMatches("LAKE", PICNIC) // description, in any case
        expectMatches("opernring", CONCERT) // address
        expectMatches("budapest", PICNIC, DINNER) // place
        expectMatches("  dinner ", DINNER) // title, spaces around ignored
    }

    @Test
    fun searchWithoutMatchesShowsAMessageUntilCleared() {
        TestData.memory(PICNIC)
        openTimeline()

        TimelineScreen.search("Atlantis")
        TimelineScreen.assertNoMatches("Atlantis")
        TimelineScreen.assertMemoryGone(PICNIC)

        TimelineScreen.search("")
        TimelineScreen.assertMemoryShown(PICNIC)
    }

    @Test
    fun scrollToTopButtonAppearsOnceScrolledAndGoesBackToTheNewest() {
        addManyMemories()
        openTimeline()
        TimelineScreen.assertScrollToTopGone()

        TimelineScreen.scrollDown()
        TimelineScreen.scrollDown()
        TimelineScreen.assertMemoryGone(memoryTitle(1))
        TimelineScreen.assertScrollToTopShown()

        TimelineScreen.scrollToTop()
        TimelineScreen.assertMemoryShown(memoryTitle(1))
        TimelineScreen.assertScrollToTopGone()
    }

    @Test
    fun showOnTimelineScrollsToAMemoryFarDown() = showOnTimelineScrollsTo(FAR_DOWN)

    @Test
    @Ignore("The oldest memories stay below the screen while the search bar shows (BUG-17). Turn this on with the fix.")
    fun showOnTimelineScrollsToTheOldestMemory() = showOnTimelineScrollsTo(MANY)

    /** Opens the newest memory, swipes to memory [n], and checks that Show on Timeline scrolls the list to it. */
    private fun showOnTimelineScrollsTo(n: Int) {
        addManyMemories()
        openTimeline()
        TimelineScreen.openMemory(memoryTitle(1))
        for (shown in 2..n) {
            MemoryScreen.swipeToOlder()
            MemoryScreen.assertShowing(memoryTitle(shown))
        }

        MemoryScreen.showOnTimeline()

        TimelineScreen.assertShown()
        BottomNav.assertSelected(Tab.TIMELINE)
        TimelineScreen.assertMemoryShown(memoryTitle(n))
        TimelineScreen.assertMemoryGone(memoryTitle(1))
    }

    private fun openTimeline() {
        app.launch()
        BottomNav.open(Tab.TIMELINE)
    }

    private fun expectMatches(query: String, vararg titles: String) {
        TimelineScreen.search(query)
        titles.forEach { TimelineScreen.assertMemoryShown(it) }
        (ALL - titles.toSet()).forEach { TimelineScreen.assertMemoryGone(it) }
    }

    /** More memories than fit on the screen, one a day: "Memory 1" yesterday, "Memory 2" the day before, ... */
    private fun addManyMemories() {
        for (n in 1..MANY) TestData.memory(memoryTitle(n), daysAgo = n.toLong())
    }

    private fun memoryTitle(n: Int) = "Memory $n"

    private companion object {
        const val PICNIC = "Picnic"
        const val CONCERT = "Concert"
        const val DINNER = "Birthday dinner"
        val ALL = setOf(PICNIC, CONCERT, DINNER)
        const val MANY = 12
        const val FAR_DOWN = 8
    }
}
