package com.szabolcshorvath.memorymap.screens

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.performClick

/** The tab bar at the bottom of the app (a rail at the side on a wide screen). Found through [ComposeUi]. */
object BottomNav {
    enum class Tab(val label: String) {
        MAP("Map"),
        TIMELINE("Timeline"),
        ADD("Add"),
        SETTINGS("Settings"),
    }

    fun select(tab: Tab) {
        ComposeUi.waitFor("The ${tab.label} tab", tab(tab)).performClick()
    }

    /** Taps [tab] while it is already shown, which takes it back to its start (the Timeline's top, the Map's home view). */
    fun tapAgain(tab: Tab) {
        assertSelected(tab)
        select(tab)
    }

    fun assertSelected(tab: Tab) {
        ComposeUi.waitFor("The ${tab.label} tab, selected,", tab(tab) and isSelected())
    }

    /** The tab bar shows (it hides while the media viewer shows). */
    fun assertShown() {
        ComposeUi.waitFor("The tab bar", tab(Tab.MAP))
    }

    fun assertGone() = ComposeUi.assertGone("The tab bar", tab(Tab.MAP))

    /** Opens [tab] and checks that its screen shows. */
    fun open(tab: Tab) {
        select(tab)
        screenOf(tab).assertShown()
    }

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun tab(tab: Tab) = isTab and hasText(tab.label)

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
