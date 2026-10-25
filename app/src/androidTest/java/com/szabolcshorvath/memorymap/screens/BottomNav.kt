package com.szabolcshorvath.memorymap.screens

import org.junit.Assert.assertTrue

/** The tab bar at the bottom of the app. */
object BottomNav {
    enum class Tab(val label: String) {
        MAP("Map"),
        TIMELINE("Timeline"),
        ADD("Add"),
        SETTINGS("Settings"),
    }

    fun select(tab: Tab) {
        Ui.find { isTab(tab) }.click()
        Ui.device.waitForIdle()
    }

    fun assertSelected(tab: Tab) = assertTrue(
        "The ${tab.label} tab is not selected",
        Ui.isShown { isTab(tab) && isSelected }
    )

    /** Opens [tab] and checks that its screen shows. */
    fun open(tab: Tab) {
        select(tab)
        screenOf(tab).assertShown()
    }

    // The selected tab is not clickable, so match the label only.
    private fun android.view.accessibility.AccessibilityNodeInfo.isTab(tab: Tab) = isFocusable && hasDescription(tab.label)

    fun screenOf(tab: Tab): TabScreen = when (tab) {
        Tab.MAP -> MapScreen
        Tab.TIMELINE -> TimelineScreen
        Tab.ADD -> AddScreen
        Tab.SETTINGS -> SettingsScreen
    }
}

/** A screen shown by a tab. */
interface TabScreen {
    fun assertShown()

    fun assertGone()
}
