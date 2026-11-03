package com.szabolcshorvath.memorymap.screens

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import org.junit.Assert.assertEquals
import java.util.regex.Pattern

/**
 * The sheet a missing photo opens: it looks for the photo, suggests what was taken around the same time, and offers
 * another photo, removing it, or Not now. Its questions are [Dialog]s.
 */
object MissingPhotoSheet {
    private const val TITLE = "This photo can't be found"
    private const val USE_PREFIX = "Use the one taken "

    /** The question before a suggestion replaces the missing photo. */
    const val USE_TITLE = "Use this one instead?"

    /** The question before the missing photo is removed. */
    const val REMOVE_TITLE = "Remove it from this memory?"

    fun assertShown() = Ui.assertShown("The sheet for a missing photo") { hasText(TITLE) }

    fun assertGone() = Ui.assertGone("The sheet for a missing photo") { hasText(TITLE) }

    /** The sheet says [text], such as that it needs access or that nothing was taken nearby. */
    fun assertMessage(text: String) = Ui.assertShown("The message \"$text\"") { hasId("tvMissingMessage") && hasText(text) }

    /**
     * Waits until the suggestions are, from the top, the photos taken [whenTaken] ("7 s later", "3 min earlier"). The
     * sheet looks for the photo first, which can take a while.
     */
    fun assertSuggestions(vararg whenTaken: String) {
        val expected = whenTaken.toList()
        val shown = Ui.eventually(read = ::suggestions) { it == expected }
        assertEquals("The suggestions", expected, shown)
    }

    /** Taps the suggestion taken [whenTaken], which asks first ([USE_TITLE]). */
    fun pick(whenTaken: String) = tap { hasDescription(USE_PREFIX + whenTaken) }

    fun showMore() = tap { hasText("Show more") && isEnabled }

    /** Taps "Allow access", which opens Android's permission dialog ([PermissionDialog]). */
    fun allowAccess() = tap { hasText("Allow access") && isEnabled }

    /** Opens the photo picker (the fake one, which returns its next pick at once). */
    fun chooseAnother() = tap { hasText("Choose another photo…") }

    /** Taps "Remove from memory", which asks first ([REMOVE_TITLE]). */
    fun remove() = tap { hasText("Remove from memory") }

    fun notNow() = tap { hasText("Not now") }

    private fun suggestions(): List<String> = try {
        Ui.device.findObjects(By.desc(Pattern.compile(Pattern.quote(USE_PREFIX) + ".*")))
            .sortedBy { it.visibleBounds.top }
            .map { it.contentDescription.removePrefix(USE_PREFIX) }
    } catch (_: StaleObjectException) {
        emptyList()
    }

    private fun tap(condition: AccessibilityNodeInfo.() -> Boolean) {
        Ui.find(condition = condition).click()
        Ui.device.waitForIdle()
    }
}
