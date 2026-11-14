package com.szabolcshorvath.memorymap.screens

import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue

/** The phone's on-screen keyboard, which Android draws in a window of its own. */
object Keyboard {
    fun assertShown() = assertTrue("The keyboard is not shown", Ui.waitUntil { showsNow() })

    fun assertHidden() = assertTrue("The keyboard is still shown", Ui.waitUntil { !showsNow() })

    /** Closes the keyboard, which shows or is about to (its window can take a few seconds to be listed). */
    fun close() {
        assertShown()
        Ui.pressBack()
        assertHidden()
    }

    private fun showsNow(): Boolean {
        Ui.device // Makes sure UI Automator has set itself up to read every window.
        return InstrumentationRegistry.getInstrumentation().uiAutomation.windows
            .any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    }
}
