package com.szabolcshorvath.memorymap.screens

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.textAsString
import org.junit.Assert.assertTrue

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
    fun assertPhotoFromAnotherDevice() {
        Ui.assertShown("The error tile of a photo from another phone") {
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
    private const val SWIPE_SPEED = 2500 // Pixels per second.
    private const val MAX_SWIPES = 3
    private const val SWIPE_TIMEOUT_MS = 3_000L
}

/** The bar at the bottom that confirms an action, such as "Memory deleted" with Undo. */
object Snackbar {
    fun assertShown(text: String) = Ui.assertShown("The message \"$text\"") { hasId("snackbar_text") && hasText(text) }

    /** Waits until the message closes by itself (a long message stays for about three seconds). */
    fun assertClosesByItself(text: String) = assertTrue(
        "The message \"$text\" did not close",
        Ui.isGone(timeoutMs = CLOSE_TIMEOUT_MS) { hasId("snackbar_text") && hasText(text) }
    )

    fun tap(action: String) {
        Ui.find { hasId("snackbar_action") && textAsString().equals(action, ignoreCase = true) }.click()
        Ui.device.waitForIdle()
    }

    private const val CLOSE_TIMEOUT_MS = 15_000L
}

/** The full-screen photo and video viewer. */
object MediaViewerScreen {
    fun assertShowingPhoto() = Ui.assertShown("The full-screen photo") { hasDescription("Photo") }

    fun assertGone() = Ui.assertGone("The full-screen viewer") { hasDescription("Photo") }
}

/** The location picker opened from the Add form. */
object LocationPickerScreen {
    private const val CONFIRM = "Confirm Location"
    private const val MAX_TAPS = 3
    private const val MARKER_TIMEOUT_MS = 5_000L

    private val isPickedMarker: AccessibilityNodeInfo.() -> Boolean = {
        contentDescription?.toString()?.startsWith("Selected Location") == true
    }

    fun assertShown() = Ui.assertShown("The location picker") { hasText(CONFIRM) }

    fun assertGone() = Ui.assertGone("The location picker") { hasText(CONFIRM) }

    /**
     * Taps the middle of the map, which picks that spot. A busy emulator can deliver the end of a tap so late that
     * the map takes it for a long press and ignores it, so this taps again until the marker shows (a few times).
     */
    fun tapMap() {
        val map = Ui.find { hasDescription("Google Map") }
        repeat(MAX_TAPS) {
            map.click()
            Ui.device.waitForIdle()
            if (Ui.isShown(timeoutMs = MARKER_TIMEOUT_MS, condition = isPickedMarker)) return
        }
    }

    /** Waits until a spot is picked: its marker shows on the map. */
    fun assertLocationPicked() = Ui.assertShown("A picked location", isPickedMarker)

    fun confirm() {
        Ui.find { hasText(CONFIRM) }.click()
        Ui.device.waitForIdle()
    }
}
