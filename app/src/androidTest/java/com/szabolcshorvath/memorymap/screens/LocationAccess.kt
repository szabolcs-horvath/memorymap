package com.szabolcshorvath.memorymap.screens

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.szabolcshorvath.memorymap.screens.PermissionDialog.isInPermissionDialog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Asking for location access, which only the "my location" buttons do: the app's explanation first, then Android's
 * own dialog ([PermissionDialog]), and once Android no longer asks, the app's offer to open its settings.
 */
object LocationAccess {
    private const val EXPLANATION = "Use your location?"
    private const val SETTINGS_OFFER = "Location access is off"
    private const val SETTINGS_APP = "com.android.settings"
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
    fun allow() = PermissionDialog.allowWhileUsingTheApp()

    /** "Don't allow" in Android's dialog. */
    fun refuse() = PermissionDialog.refuse()

    /** Neither the app nor Android asks for location access. It waits a moment, in case a question is on its way. */
    fun assertNotAsked() = assertFalse(
        "Location access was asked for",
        Ui.isShown(timeoutMs = NOT_ASKED_WAIT_MS) { hasText(EXPLANATION) || isInPermissionDialog() }
    )

    fun assertSettingsOffered() = Dialog.assertShown(SETTINGS_OFFER)

    fun openSettings() = Dialog.tap("Open settings")

    /** Android's settings app is open (on the app's own page). */
    fun assertAppSettingsShown() = assertTrue(
        "The settings app did not open",
        Ui.device.wait(Until.hasObject(By.pkg(SETTINGS_APP)), SETTINGS_TIMEOUT_MS) == true
    )
}
