package com.szabolcshorvath.memorymap.timeline

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.TimelineScreen
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import com.szabolcshorvath.memorymap.testing.TestData.Place
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
    fun searchFindsTitlesDescriptionsPlacesAndAddressesOrSaysNothingMatchesUntilCleared() {
        TestData.memory(PICNIC, description = "Sunny afternoon by the lake")
        TestData.memory(CONCERT, place = Place(48.2030, 16.3690, "Vienna", "Opernring 2"))
        TestData.memory(DINNER)
        openTimeline()

        expectMatches("LAKE", PICNIC) // description, in any case
        expectMatches("opernring", CONCERT) // address
        expectMatches("budapest", PICNIC, DINNER) // place
        expectMatches("  dinner ", DINNER) // title, spaces around ignored

        expectMatches("Atlantis")
        TimelineScreen.assertNoMatches("Atlantis")
        expectMatches("", *ALL.toTypedArray())
    }

    @Test
    fun showOnTimelineScrollsToAMemoryFarDownAndScrollToTopGoesBackToTheNewest() {
        showOnTimelineScrollsTo(FAR_DOWN)

        TimelineScreen.assertScrollToTopShown()
        TimelineScreen.scrollToTop()
        TimelineScreen.assertMemoryShown(memoryTitle(1))
        TimelineScreen.assertScrollToTopGone()

        // Scrolled by hand, the button comes back.
        TimelineScreen.scrollDown()
        TimelineScreen.scrollDown()
        TimelineScreen.assertMemoryGone(memoryTitle(1))
        TimelineScreen.assertScrollToTopShown()
    }

    @Test
    fun showOnTimelineScrollsToTheOldestMemory() = showOnTimelineScrollsTo(MANY)

    @Test
    fun showOnTimelineFlashesTheMemory() {
        // Five times slower: the one-second flash lasts five, which a slow emulator can't miss.
        app.useAnimations(slowdown = 5)
        TestData.memory(PICNIC, daysAgo = 1)
        TestData.memory(CONCERT, daysAgo = 2)
        openTimeline()
        TimelineScreen.openMemory(CONCERT)
        MemoryScreen.assertShowing(CONCERT)

        TimelineScreen.assertFlashesAfter(CONCERT) { MemoryScreen.showOnTimeline() }
    }

    @Test
    fun tappingTheTimelineTabAgainGoesBackToTheNewest() {
        // The scroll back is animated: with animations off it would end at once, and an animation that gets cancelled
        // would go unnoticed.
        app.useAnimations()
        addManyMemories()
        openTimeline()
        TimelineScreen.scrollDown()
        TimelineScreen.scrollDown()
        TimelineScreen.assertMemoryGone(memoryTitle(1))

        BottomNav.select(Tab.TIMELINE)

        TimelineScreen.assertMemoryShown(memoryTitle(1))
        TimelineScreen.assertScrollToTopGone()
    }

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
