package com.szabolcshorvath.memorymap.add

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.screens.AddScreen
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.LocationPickerScreen
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.TimelineScreen
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import com.szabolcshorvath.memorymap.testing.TestData.Hsv
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A memory's fragments and marker colour on the Add form. The tests edit an existing memory, which already has a
 * location, so only a fragment's location needs the map.
 */
@RunWith(AndroidJUnit4::class)
class FragmentsAndColorTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun aFragmentWithItsOwnPlaceIsSavedWithTheMemory() {
        TestData.memory(VIENNA)
        openForEditing()

        AddScreen.addFragment()
        AddScreen.selectFragmentLocation()
        LocationPickerScreen.tapMap()
        LocationPickerScreen.assertLocationPicked()
        LocationPickerScreen.confirm()
        AddScreen.assertFragmentHasLocation()
        AddScreen.save()

        MemoryScreen.assertShowing(VIENNA)
        MemoryScreen.assertFragmentCount(1)
    }

    @Test
    fun aFragmentStartsAtTheMemorysPlace() {
        TestData.memory(VIENNA)
        openForEditing()

        AddScreen.addFragment()
        AddScreen.assertFragmentHasLocation()
        AddScreen.save()

        MemoryScreen.assertShowing(VIENNA)
        MemoryScreen.assertFragmentCount(1)
    }

    @Test
    fun savingNeedsALocationForEveryFragment() {
        // A fragment takes the memory's place when it is added; one added before the memory had a place has none.
        app.launch()
        BottomNav.open(Tab.ADD)
        AddScreen.typeTitle(PICNIC)
        AddScreen.addFragment()
        AddScreen.assertFragmentHasNoLocation()
        AddScreen.selectLocation()
        LocationPickerScreen.tapMap()
        LocationPickerScreen.assertLocationPicked()
        LocationPickerScreen.confirm()
        AddScreen.assertHasLocation()

        AddScreen.save()
        AddScreen.assertShown()
        AddScreen.assertNew()

        AddScreen.removeFragment()
        AddScreen.assertNoFragments()
        AddScreen.save()
        MemoryScreen.assertShowing(PICNIC)
    }

    @Test
    fun aPickedColorIsSaved() {
        TestData.colorPresets(RED, GREEN, BLUE)
        TestData.memory(VIENNA)
        openForEditing()

        AddScreen.pickColor(GREEN)
        AddScreen.assertColor(GREEN)
        AddScreen.save()
        MemoryScreen.assertShowing(VIENNA)

        MemoryScreen.edit()
        AddScreen.assertColor(GREEN)
    }

    private fun openForEditing() {
        app.launch()
        BottomNav.open(Tab.TIMELINE)
        TimelineScreen.openMemory(VIENNA)
        MemoryScreen.edit()
        AddScreen.assertTitle(VIENNA)
        AddScreen.assertEditing()
    }

    private companion object {
        const val VIENNA = "Vienna trip"
        const val PICNIC = "Picnic"
        val RED = Hsv(0f)
        val GREEN = Hsv(120f)
        val BLUE = Hsv(240f)
    }
}
