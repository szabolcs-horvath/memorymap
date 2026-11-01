package com.szabolcshorvath.memorymap.screens

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.uiautomator.simpleViewResourceName

/**
 * Android's own dialog that asks for a permission (location, photos and videos). Its buttons are looked up by their
 * resource ids, because their texts change between Android versions.
 */
object PermissionDialog {
    /** "While using the app", for location. */
    fun allowWhileUsingTheApp() = tap(setOf("permission_allow_foreground_only_button"))

    /** "Allow all", for photos and videos ("Allow" where Android offers no choice of photos). */
    fun allowAll() = tap(setOf("permission_allow_all_button", "permission_allow_button"))

    /** "Don't allow". The second time, it also means "don't ask again" and has its own id. */
    fun refuse() = tap(setOf("permission_deny_button", "permission_deny_and_dont_ask_again_button"))

    /** Whether this element belongs to the dialog: it comes from com.android.permissioncontroller, or Google's version. */
    fun AccessibilityNodeInfo.isInPermissionDialog() = packageName?.toString()?.endsWith("permissioncontroller") == true

    private fun tap(ids: Set<String>) {
        Ui.find { isInPermissionDialog() && simpleViewResourceName() in ids }.click()
        Ui.device.waitForIdle()
    }
}
