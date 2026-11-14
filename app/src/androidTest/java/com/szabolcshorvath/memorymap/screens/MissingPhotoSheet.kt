package com.szabolcshorvath.memorymap.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import com.szabolcshorvath.memorymap.screens.ComposeUi.centerOnScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * The sheet a missing photo opens: it looks for the photo, suggests what was taken around the same time, and offers
 * another photo or removing it. Its questions are [Dialog]s. Found through [ComposeUi].
 */
object MissingPhotoSheet {
    private const val TITLE = "This photo can't be found"
    private const val USE_PREFIX = "Use the one taken "

    /** The question before a suggestion replaces the missing photo. */
    const val USE_TITLE = "Use this one instead?"

    /** The question before the missing photo is removed. */
    const val REMOVE_TITLE = "Remove it from this memory?"

    fun assertShown() {
        ComposeUi.waitFor("The sheet for a missing photo", hasText(TITLE))
    }

    fun assertGone() = ComposeUi.assertGone("The sheet for a missing photo", hasText(TITLE))

    /** The sheet says [text], such as that it needs access or that nothing was taken nearby. */
    fun assertMessage(text: String) {
        ComposeUi.waitFor("The message \"$text\"", hasTestTag("tvMissingMessage") and hasText(text), unmerged = true)
    }

    /**
     * Waits until the suggestions are, from the top, the photos taken [whenTaken] ("7 sec later", "3 min earlier"). The
     * sheet looks for the photo first, which can take a while.
     */
    fun assertSuggestions(vararg whenTaken: String) {
        val expected = whenTaken.toList()
        val shown = Ui.eventually(read = ::suggestions) { it == expected }
        assertEquals("The suggestions", expected, shown)
    }

    /** The sheet shows how the lost photo looked: its stored thumbnail, of [color] (the tests' thumbnails are one colour). */
    fun assertThumbnail(color: Int) {
        val center = ComposeUi.waitFor("How the lost photo looked", hasContentDescription(LOOKED)).centerOnScreen()
        val shown = Ui.eventually(read = { ScreenColors.at(listOf(center)).single() }) { ScreenColors.isClose(it, color) }
        assertTrue("The thumbnail is ${ScreenColors.hex(shown)}, not ${ScreenColors.hex(color)}", ScreenColors.isClose(shown, color))
    }

    /** The sheet names the lost file: [text], such as "IMG_1.jpg · 640 × 480". */
    fun assertFile(text: String) {
        ComposeUi.waitFor("The file \"$text\"", hasTestTag("tvMissingFile") and hasText(text), unmerged = true)
    }

    /** Taps the suggestion taken [whenTaken], which asks first ([USE_TITLE]). */
    fun pick(whenTaken: String) = tap("The suggestion taken $whenTaken", hasContentDescription(USE_PREFIX + whenTaken))

    fun showMore() = tap("Show more", hasText("Show more") and isEnabled())

    /** Taps "Allow access", which opens Android's permission dialog ([PermissionDialog]). */
    fun allowAccess() = tap("Allow access", hasText("Allow access") and isEnabled())

    /** Opens the photo picker (the fake one, which returns its next pick at once). */
    fun chooseAnother() = tap("Choose another photo", hasText("Choose another photo…"))

    /** Taps "Remove from memory", which asks first ([REMOVE_TITLE]). */
    fun remove() = tap("Remove from memory", hasText("Remove from memory"))

    /** Closes the sheet with Back (it has no button for that: dragging it down or tapping outside it does too). */
    fun close() = Ui.pressBack()

    private val suggestion = SemanticsMatcher("is a suggestion") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith(USE_PREFIX) }
    }

    private fun suggestions(): List<String> = ComposeUi.nodes(suggestion).map { node ->
        node.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first().removePrefix(USE_PREFIX)
    }

    private fun tap(what: String, matcher: SemanticsMatcher) {
        ComposeUi.waitFor(what, matcher).performClick()
    }

    private const val LOOKED = "How it looked"
}
