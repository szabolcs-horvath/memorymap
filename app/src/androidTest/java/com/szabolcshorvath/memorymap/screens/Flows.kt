package com.szabolcshorvath.memorymap.screens

import com.szabolcshorvath.memorymap.screens.BottomNav.Tab

/**
 * Steps across several screens that many tests take on their way to what they check. Each step checks that it got
 * where it was going, so a test that fails on the way says where.
 */
object Flows {
    /** Opens memory [title] from the Timeline tab. */
    fun openMemory(title: String) {
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.openMemory(title)
        MemoryScreen.assertShowing(title)
    }

    /** Opens memory [title] from the Timeline tab and taps Edit, which opens it on the Add tab's form. */
    fun editMemory(title: String) {
        openMemory(title)
        MemoryScreen.edit()
        BottomNav.assertSelected(Tab.ADD)
        AddScreen.assertTitle(title)
        AddScreen.assertEditing()
    }

    /** On the Add form: picks a spot on the map for the memory's location and confirms it. */
    fun pickLocationOnMap() {
        AddScreen.selectLocation()
        LocationPickerScreen.tapMap()
        LocationPickerScreen.assertLocationPicked()
        LocationPickerScreen.confirm()
        AddScreen.assertHasLocation()
    }
}
