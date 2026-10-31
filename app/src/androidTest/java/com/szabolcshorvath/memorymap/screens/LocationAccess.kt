package com.szabolcshorvath.memorymap.screens

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.simpleViewResourceName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Asking for location access, which only the "my location" buttons do: the app's explanation first, then Android's
 * own dialog, and once Android no longer asks, the app's offer to open its settings. Android's dialog is looked up
 * by its buttons' resource ids, because their texts change between Android versions.
 */
object LocationAccess {
    private const val EXPLANATION = "Use your location?"
    private const val SETTINGS_OFFER = "Location access is off"
    private const val SETTINGS_APP = "com.android.settings"
    private const val ALLOW = "permission_allow_foreground_only_button"

    // The second time, Android's "Don't allow" also means "don't ask again" and has its own id.
    private val REFUSE = setOf("permission_deny_button", "permission_deny_and_dont_ask_again_button")
    private const val NOT_ASKED_WAIT_MS = 2_000L
    private const val SETTINGS_TIMEOUT_MS = 15_000L

    fun assertExplained() = Dialog.assertShown(EXPLANATION)

    /** Answers the app's explanation with "Not now", which closes it without asking Android. */
    fun notNow() {
        Dialog.tap("Not now")
        Dialog.assertGone(EXPLANATION)
    }

    /** Answers the app's explanation with "Continue", which opens Android's dialog. */
    fun continueToAndroid() = Dialog.tap("Continue")

    /** "While using the app" in Android's dialog. */
    fun allow() = tapInAndroidDialog { simpleViewResourceName() == ALLOW }

    /** "Don't allow" in Android's dialog. */
    fun refuse() = tapInAndroidDialog { simpleViewResourceName() in REFUSE }

    /** Neither the app nor Android asks for location access. It waits a moment, in case a question is on its way. */
    fun assertNotAsked() = assertFalse(
        "Location access was asked for",
        Ui.isShown(timeoutMs = NOT_ASKED_WAIT_MS) { hasText(EXPLANATION) || isInAndroidDialog() }
    )

    fun assertSettingsOffered() = Dialog.assertShown(SETTINGS_OFFER)

    fun openSettings() = Dialog.tap("Open settings")

    /** Android's settings app is open (on the app's own page). */
    fun assertAppSettingsShown() = assertTrue(
        "The settings app did not open",
        Ui.device.wait(Until.hasObject(By.pkg(SETTINGS_APP)), SETTINGS_TIMEOUT_MS) == true
    )

    private fun tapInAndroidDialog(button: AccessibilityNodeInfo.() -> Boolean) {
        Ui.find { isInAndroidDialog() && button() }.click()
        Ui.device.waitForIdle()
    }

    // Android's permission dialog comes from com.android.permissioncontroller, or Google's version of it.
    private fun AccessibilityNodeInfo.isInAndroidDialog() =
        packageName?.toString()?.endsWith("permissioncontroller") == true
}
