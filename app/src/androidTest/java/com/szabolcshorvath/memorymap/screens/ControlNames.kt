package com.szabolcshorvath.memorymap.screens

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.simpleViewResourceName
import org.junit.Assert.assertTrue

/**
 * Checks that a screen reader can name everything the user can act on in the app's windows: each element that can be
 * tapped, long-pressed, switched or typed into needs a text, a content description, a hint, a label, or text inside it
 * that is not a control of its own (TalkBack reads that out for it). The off-screen part of a scrolling page is in the
 * element tree too, so it is checked without scrolling; a list only holds the rows it shows. The map's own elements
 * are Google's and are left out. It reads the accessibility tree, as a screen reader does, not Compose's own
 * ([ComposeUi]).
 *
 * Create one per test, [check] each screen, and [assertAllNamed] at the end, so one run lists every unnamed control.
 */
class ControlNames {
    private val unnamed = mutableListOf<String>()

    /** Notes the unnamed controls now on screen, under [screen]. */
    fun check(screen: String) {
        Ui.catchUp()
        Ui.device.waitForIdle()
        appRoots().flatMap(::unnamedIn).distinct().forEach { unnamed += "$screen: $it" }
    }

    fun assertAllNamed() = assertTrue("Controls without a name:\n" + unnamed.joinToString("\n"), unnamed.isEmpty())

    private fun appRoots(): List<AccessibilityNodeInfo> {
        Ui.device // Makes sure UI Automator has set itself up to read every window.
        return InstrumentationRegistry.getInstrumentation().uiAutomation.windows
            .mapNotNull { it.root }
            .filter { it.packageName?.toString() == Ui.APP_PACKAGE }
    }

    private fun unnamedIn(node: AccessibilityNodeInfo): List<String> {
        // The map, and the tappable frame the Maps SDK wraps around it.
        if (node.isMap() || (node.isClickable && node.hasMapInside())) return emptyList()
        val own = if (node.isImportantControl() && !node.hasName()) listOf(node.describe()) else emptyList()
        return own + node.children().flatMap(::unnamedIn)
    }

    private fun AccessibilityNodeInfo.isMap() = contentDescription?.toString() == GOOGLE_MAP

    private fun AccessibilityNodeInfo.hasMapInside(): Boolean = children().any { it.isMap() || it.hasMapInside() }

    // A Material slider hides itself and shows its thumb as an element of its own, which has a range instead of a click.
    private fun AccessibilityNodeInfo.isImportantControl() =
        (isImportantForAccessibility && (isClickable || isLongClickable || isCheckable || isEditable)) || rangeInfo != null

    private fun AccessibilityNodeInfo.isControl() = isClickable || isLongClickable || isCheckable || isEditable || rangeInfo != null

    private fun AccessibilityNodeInfo.hasName(): Boolean {
        // A Material slider without a content description reads only "Value, 120", not which slider it is.
        if (rangeInfo != null) return contentDescription?.startsWith(SLIDER_WITHOUT_NAME) == false
        return hasOwnName() || !hintText.isNullOrBlank() || labeledBy?.hasOwnName() == true || children().any { it.speaksForParent() }
    }

    private fun AccessibilityNodeInfo.hasOwnName() = !text.isNullOrBlank() || !contentDescription.isNullOrBlank()

    /** A child that TalkBack reads out as part of its parent: one with a name that is not a control itself. */
    private fun AccessibilityNodeInfo.speaksForParent(): Boolean =
        !isControl() && (hasOwnName() || children().any { it.speaksForParent() })

    private fun AccessibilityNodeInfo.children() = (0 until childCount).mapNotNull { getChild(it) }

    private fun AccessibilityNodeInfo.describe(): String {
        val id = simpleViewResourceName() ?: "(no id)"
        val type = className?.toString()?.substringAfterLast('.')
        val bounds = android.graphics.Rect().also(::getBoundsInScreen)
        return "$id ($type at ${bounds.toShortString()})"
    }

    private companion object {
        const val SLIDER_WITHOUT_NAME = "Value,"
    }
}
