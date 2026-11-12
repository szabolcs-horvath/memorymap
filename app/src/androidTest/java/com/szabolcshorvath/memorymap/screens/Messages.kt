package com.szabolcshorvath.memorymap.screens

import android.view.accessibility.AccessibilityEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.textAsString
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.util.concurrent.TimeoutException

/**
 * The bar at the bottom that confirms an action, such as "Memory deleted" with Undo, or says what went wrong: Compose
 * gives it no ids, so its text finds it, and its action by the action's text.
 */
object Snackbar {
    fun assertShown(text: String) = Ui.assertShown("The message \"$text\"") { hasText(text) }

    /** Waits until the message closes by itself (a long message stays for about three seconds). */
    fun assertClosesByItself(text: String) = assertTrue(
        "The message \"$text\" did not close",
        Ui.isGone(timeoutMs = CLOSE_TIMEOUT_MS) { hasText(text) }
    )

    fun tap(action: String) {
        // The action is a button; Compose reports its text as an element inside it, and a tap there taps the button.
        Ui.find { textAsString().equals(action, ignoreCase = true) }.click()
        Ui.device.waitForIdle()
    }

    private const val CLOSE_TIMEOUT_MS = 15_000L
}

/**
 * A short message Android shows near the bottom of the screen. Android draws it outside the app's window, where the
 * element tree doesn't reach, so this listens for the announcement Android makes for screen readers instead.
 */
object ToastMessage {
    /** Runs [action] and waits until it shows the message [text]. */
    fun assertShownAfter(text: String, action: () -> Unit) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            automation.executeAndWaitForEvent(
                { action() },
                { event ->
                    event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                        event.text.any { it.toString() == text }
                },
                TIMEOUT_MS
            )
        } catch (_: TimeoutException) {
            fail("The message \"$text\" was not shown")
        }
    }

    private const val TIMEOUT_MS = 10_000L
}
