package com.szabolcshorvath.memorymap.screens

import android.graphics.Point
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.textAsString
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.time.LocalDate

/** A memory opened from the map or the timeline (one page of the memory pager). */
object MemoryScreen {
    private const val EDIT = "Edit Memory"

    fun assertShown() = Ui.assertShown("A memory") { hasDescription(EDIT) }

    fun assertGone() = Ui.assertGone("The memory") { hasDescription(EDIT) }

    fun assertShowing(title: String) = Ui.assertShown("The memory \"$title\"") { hasId("tv_title") && hasText(title) }

    fun showOnMap() = tap { hasText("Show on Map") }

    fun showOnTimeline() = tap { hasText("Show on Timeline") }

    fun edit() = tap { hasDescription(EDIT) }

    fun openFirstPhoto() = tap { hasDescription("Open photo") }

    /** Taps Delete, which asks for confirmation first ([Dialog] "Delete Memory"). */
    fun delete() = tap { hasDescription("Delete Memory") }

    /** Swipes to the next memory in the timeline's order (an older one). */
    fun swipeToOlder() = swipe(Direction.LEFT)

    /** Swipes to the previous memory in the timeline's order (a newer one). */
    fun swipeToNewer() = swipe(Direction.RIGHT)

    /** The date line, such as "Oct 24, 2026 12:00 PM - 2:00 PM", or the day alone for an all-day memory. */
    fun dateText(): String = Ui.find { hasId("tv_date") }.text

    fun assertDescription(text: String) = Ui.assertShown("The description \"$text\"") {
        hasId("tv_description") && hasText(text)
    }

    /** Waits until the memory shows [count] photo thumbnails. */
    fun assertPhotoCount(count: Int) = assertThumbnailCount(OPEN_PHOTO, count)

    /** Waits until the memory shows [count] video thumbnails (marked with a play icon). */
    fun assertVideoCount(count: Int) = assertThumbnailCount(OPEN_VIDEO, count)

    fun openFirstVideo() {
        Ui.scrollTo { hasDescription(OPEN_VIDEO) }
        tap { hasDescription(OPEN_VIDEO) }
    }

    private fun assertThumbnailCount(description: String, count: Int) {
        if (count > 0) Ui.scrollTo { hasDescription(description) }
        val found = Ui.eventually(read = { Ui.device.findObjects(By.desc(description)).size }) { it == count }
        assertEquals("Thumbnails described \"$description\"", count, found)
    }

    /** Opens the "Memory Fragments" section if it is closed, and waits until it lists [count] fragments. */
    fun assertFragmentCount(count: Int) {
        openFragments()
        val found = Ui.eventually(read = { fragmentCards().size }) { it == count }
        assertEquals("Fragments of the memory", count, found)
    }

    /**
     * The (only) fragment has dates of its own, starting on [date] (its dates read like "Sep 10, 2026 9:00 AM - …").
     * A fragment without dates of its own shows none.
     */
    fun assertFragmentStartsOn(date: LocalDate) {
        val text = fragmentCard().findObject(By.res(Ui.APP_PACKAGE, "tv_time"))?.text.orEmpty()
        val day = date.format(DateTimeFormatterUtil.dateFormatter())
        assertTrue("The fragment's dates \"$text\" don't start on $day", text.startsWith(day))
    }

    /**
     * The (only) fragment shows the marker colour [color], in the strip along its left edge. Waits for it, in case
     * the fragments section is still opening.
     */
    fun assertFragmentColor(color: Int) {
        val strip = checkNotNull(fragmentCard().findObject(By.res(Ui.APP_PACKAGE, "colorIndicator"))) {
            "The fragment has no colour strip"
        }
        val spot = strip.visibleCenter
        val shown = Ui.eventually(read = { ScreenColors.at(listOf(spot)).single() }) { ScreenColors.isClose(it, color) }
        assertTrue(
            "The fragment's colour ${ScreenColors.hex(shown)} is not ${ScreenColors.hex(color)}",
            ScreenColors.isClose(shown, color)
        )
    }

    /** The (only) fragment's dates read exactly [text], such as "Sep 10, 2026 - Sep 12, 2026" for an all-day one. */
    fun assertFragmentDates(text: String) {
        val shown = fragmentCard().findObject(By.res(Ui.APP_PACKAGE, "tv_time"))?.text
        assertEquals("The fragment's dates", text, shown)
    }

    /** The fragments are listed in this order, from top to bottom, by the place each card shows (all on screen). */
    fun assertFragmentsTopToBottom(vararg places: String) {
        openFragments()
        Ui.scrollTo { hasId("tv_location") && isInFragmentCard() && hasText(places.last()) }
        val shown = Ui.eventually(read = ::fragmentPlaces) { it == places.toList() }
        assertEquals("The order of the fragments", places.toList(), shown)
    }

    private fun fragmentPlaces(): List<String> = try {
        fragmentCards().map { it.findObject(By.res(Ui.APP_PACKAGE, "tv_location")).text }
    } catch (_: StaleObjectException) {
        emptyList() // A card was bound again while it was read.
    }

    private fun fragmentCards(): List<UiObject2> =
        Ui.device.findObjects(By.res(Ui.APP_PACKAGE, "fragmentCard")).sortedBy { it.visibleBounds.top }

    /**
     * Long-presses the fragment card showing [place] and drags it into the place of the one showing [onto]. The list
     * moves a card past another only once it covers most of it, so the drag ends at the far edge of [onto].
     */
    fun dragFragment(place: String, onto: String) {
        fun card(name: String) = fragmentCards().first {
            it.findObject(By.res(Ui.APP_PACKAGE, "tv_location"))?.text == name
        }
        val moved = card(place)
        val target = card(onto).visibleBounds
        val from = moved.visibleCenter
        val y = if (target.centerY() < from.y) target.top + EDGE_INSET else target.bottom - EDGE_INSET
        moved.drag(Point(target.centerX(), y), DRAG_SPEED)
        Ui.device.waitForIdle()
    }

    /** Taps the (only) fragment's own Show on Map, which shows only while fragment markers are turned on. */
    fun showFragmentOnMap() {
        openFragments()
        Ui.scrollTo { hasText(SHOW_ON_MAP) && isInFragmentCard() }
        tap { hasText(SHOW_ON_MAP) && isInFragmentCard() }
    }

    /** Taps "Re-link Media Files" on the could-not-load warning, which asks for photo access first if needed. */
    fun relinkMedia() {
        Ui.scrollTo { hasText(RELINK) }
        tap { hasText(RELINK) && isEnabled }
    }

    /**
     * The first fragment's card, on screen, with the fragments section opened. It looks for the card's location line,
     * which every card shows (its Show on Map button shows only while fragment markers are turned on).
     */
    private fun fragmentCard(): UiObject2 {
        openFragments()
        Ui.scrollTo { hasId("tv_location") && isInFragmentCard() }
        return Ui.find { hasId("fragmentCard") }
    }

    private fun AccessibilityNodeInfo.isInFragmentCard(): Boolean {
        var node = parent
        while (node != null) {
            if (node.viewIdResourceName?.endsWith(":id/fragmentCard") == true) return true
            node = node.parent
        }
        return false
    }

    private fun openFragments() {
        Ui.scrollTo { hasText(FRAGMENTS) }
        if (!Ui.isShown(timeoutMs = 0) { hasId("fragmentCard") }) Ui.find { hasText(FRAGMENTS) }.click()
    }

    /**
     * The photos are, from left to right, of these colours (the tests' photos are a single colour each). The
     * thumbnails all read "Open photo", so the colours are read from a screenshot, at each thumbnail's centre. Waits
     * for the thumbnails to load and fade in.
     */
    fun assertPhotoColors(vararg colors: Int) {
        Ui.scrollTo { hasDescription(OPEN_PHOTO) }
        // A thumbnail that is bound again while it is read is gone: read them all again next time.
        fun read() = try {
            ScreenColors.at(thumbnails().map { it.visibleCenter })
        } catch (_: StaleObjectException) {
            emptyList()
        }
        fun List<Int>.matches() = size == colors.size && zip(colors.toList()).all { (a, b) -> ScreenColors.isClose(a, b) }
        val shown = Ui.eventually(read = ::read) { it.matches() }
        assertTrue(
            "The photos' colours ${shown.map(ScreenColors::hex)} are not ${colors.map(ScreenColors::hex)}",
            shown.matches()
        )
    }

    /** Opens photo [index] (0 is the first) in the full-screen viewer. */
    fun openPhoto(index: Int) {
        Ui.scrollTo { hasDescription(OPEN_PHOTO) }
        thumbnails()[index].click()
        Ui.device.waitForIdle()
    }

    /** Long-presses photo [from] (0 is the first) and drags it into the place of photo [onto]. */
    fun dragPhoto(from: Int, onto: Int) {
        Ui.scrollTo { hasDescription(OPEN_PHOTO) }
        val thumbs = thumbnails()
        val target = thumbs[onto].visibleBounds
        // The list moves a photo past another only once it covers most of it: end at the far edge.
        val x = if (onto < from) target.left + EDGE_INSET else target.right - EDGE_INSET
        thumbs[from].drag(Point(x, target.centerY()), DRAG_SPEED)
        Ui.device.waitForIdle()
    }

    private fun thumbnails(): List<UiObject2> = Ui.device.findObjects(By.desc(OPEN_PHOTO))
        .sortedWith(compareBy({ it.visibleBounds.top }, { it.visibleBounds.left }))

    /** The photo shows as a thumbnail, without the could-not-load warning. */
    fun assertPhotoLoaded() {
        Ui.assertShown("A photo thumbnail") { hasDescription("Open photo") }
        Ui.assertGone("A thumbnail that could not be loaded") { hasId("tvErrorDetails") }
        Ui.assertGone("The could-not-load warning") { isMediaWarning() }
    }

    /**
     * A photo from another phone shows as an error tile with its type, size and date instead of the picture, and the
     * screen warns that some media could not be loaded.
     */
    fun assertPhotoFromAnotherDevice() = assertPhotoNotLoaded()

    /**
     * A photo the app can't read shows as an error tile with its type, size and date instead of the picture, and the
     * screen warns that some media could not be loaded.
     */
    fun assertPhotoNotLoaded() {
        Ui.assertShown("The error tile of a photo that could not be loaded") {
            hasId("tvErrorDetails") && textAsString().orEmpty().startsWith("IMAGE")
        }
        Ui.assertShown("The could-not-load warning") { isMediaWarning() }
    }

    private fun AccessibilityNodeInfo.isMediaWarning() =
        textAsString().orEmpty().startsWith("Some media items could not be loaded")

    /**
     * Swipes slowly across most of the page, so it turns by distance, not speed. A busy emulator can still deliver a
     * swipe so late that the page doesn't turn, so this swipes again (a few times) until another memory shows.
     */
    private fun swipe(direction: Direction) {
        val before = Ui.find { hasId("tv_title") }.text
        repeat(MAX_SWIPES) {
            Ui.find { hasId("memoryViewPager") }.swipe(direction, SWIPE_PERCENT, SWIPE_SPEED)
            Ui.device.waitForIdle()
            if (Ui.isShown(timeoutMs = SWIPE_TIMEOUT_MS) { hasId("tv_title") && textAsString() != before }) return
        }
    }

    private fun tap(condition: AccessibilityNodeInfo.() -> Boolean) {
        Ui.find(condition = condition).click()
        Ui.device.waitForIdle()
    }

    private const val SWIPE_PERCENT = 0.8f
    private const val FRAGMENTS = "Memory Fragments"
    private const val SHOW_ON_MAP = "Show on Map"
    private const val RELINK = "Re-link Media Files"
    private const val OPEN_PHOTO = "Open photo"
    private const val OPEN_VIDEO = "Open video"
    private const val EDGE_INSET = 2
    private const val DRAG_SPEED = 600 // Pixels per second.
    private const val SWIPE_SPEED = 2500 // Pixels per second.
    private const val MAX_SWIPES = 3
    private const val SWIPE_TIMEOUT_MS = 3_000L
}
