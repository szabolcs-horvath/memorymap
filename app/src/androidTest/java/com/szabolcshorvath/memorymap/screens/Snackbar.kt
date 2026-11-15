package com.szabolcshorvath.memorymap.screens

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue

/**
 * The bar at the bottom that confirms an action, such as "Memory deleted" with Undo, or says what went wrong: its
 * text finds it, and its action by the action's text. Found through [ComposeUi].
 */
object Snackbar {
    fun assertShown(text: String) {
        ComposeUi.waitFor("The message \"$text\"", hasText(text))
    }

    /** Waits until the message closes by itself (a long message stays for about three seconds). */
    fun assertClosesByItself(text: String) = assertTrue(
        "The message \"$text\" did not close",
        Ui.waitUntil(CLOSE_TIMEOUT_MS) { !ComposeUi.exists(hasText(text)) }
    )

    fun tap(action: String) {
        ComposeUi.waitFor("The action \"$action\"", hasText(action, ignoreCase = true) and hasClickAction()).performClick()
    }

    private const val CLOSE_TIMEOUT_MS = 15_000L
}
