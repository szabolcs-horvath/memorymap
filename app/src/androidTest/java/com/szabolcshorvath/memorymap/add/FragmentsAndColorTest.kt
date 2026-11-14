package com.szabolcshorvath.memorymap.add

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.screens.AddScreen
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.DatePickerDialog
import com.szabolcshorvath.memorymap.screens.DateRangePickerDialog
import com.szabolcshorvath.memorymap.screens.Dialog
import com.szabolcshorvath.memorymap.screens.Flows
import com.szabolcshorvath.memorymap.screens.LocationPickerScreen
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import com.szabolcshorvath.memorymap.testing.TestData.Hsv
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * A memory's fragments and marker colour on the Add form: a fragment's own place, dates and colour. The tests edit
 * an existing memory, which already has a location, so only a fragment's location needs the map. (The form keeps
 * fragments in the order they were added; `MemoryScreenTest` reorders them on the memory screen.)
 */
@RunWith(AndroidJUnit4::class)
class FragmentsAndColorTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Test
    fun aFragmentStartsAtTheMemorysPlaceAndIsSavedWithAPlaceOfItsOwn() {
        TestData.memory(VIENNA)
        openForEditing()

        AddScreen.addFragment()
        AddScreen.assertFragmentHasLocation() // The memory's place.
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
    fun savingNeedsALocationForEveryFragment() {
        // A fragment takes the memory's place when it is added; one added before the memory had a place has none.
        app.launch()
        BottomNav.open(Tab.ADD)
        AddScreen.typeTitle(PICNIC)
        AddScreen.addFragment()
        AddScreen.assertFragmentHasNoLocation()
        Flows.pickLocationOnMap()

        AddScreen.save()
        AddScreen.assertShown()
        AddScreen.assertNew()

        AddScreen.removeFragment()
        AddScreen.assertNoFragments()
        AddScreen.save()
        MemoryScreen.assertShowing(PICNIC)
    }

    @Test
    fun aFragmentKeepsDatesOfItsOwnAndThenItsOwnAllDayRange() {
        TestData.memory(VIENNA)
        openForEditing()

        AddScreen.addFragment()
        AddScreen.openFragmentStartDatePicker()
        DatePickerDialog.pickDayInPreviousMonth(PICKED_DAY)
        Dialog.tap("OK")
        AddScreen.save()

        MemoryScreen.assertShowing(VIENNA)
        MemoryScreen.assertFragmentStartsOn(LocalDate.now().minusMonths(1).withDayOfMonth(PICKED_DAY))

        // Edited again, the fragment becomes all day, over a range of its own.
        val start = LocalDate.now().minusDays(ALL_DAY_START_DAYS_AGO)
        val end = LocalDate.now().minusDays(ALL_DAY_END_DAYS_AGO)
        MemoryScreen.edit()
        AddScreen.openFragmentAllDayRangePicker(hasOwnDates = true)
        DateRangePickerDialog.pick(start, end)
        AddScreen.save()

        MemoryScreen.assertShowing(VIENNA)
        MemoryScreen.assertFragmentDates("${DateTimeFormatterUtil.date(start)} - ${DateTimeFormatterUtil.date(end)}")
    }

    @Test
    fun aPickedColorIsSavedAndAFragmentKeepsAColorOfItsOwn() {
        TestData.colorPresets(RED, GREEN, BLUE)
        TestData.memory(VIENNA) // Red, as every memory starts.
        openForEditing()
        AddScreen.pickColor(GREEN)
        AddScreen.assertColor(GREEN)

        AddScreen.addFragment() // It starts with the memory's colour.
        AddScreen.pickFragmentColor(BLUE)
        AddScreen.save()

        MemoryScreen.assertShowing(VIENNA)
        MemoryScreen.assertFragmentColor(ColorUtil.hsvToColor(BLUE.hue, BLUE.saturation, BLUE.brightness))
        MemoryScreen.edit()
        AddScreen.assertColor(GREEN)
    }

    @Test
    fun thePresetOfTheFormsColorIsHighlightedUntilTheColorChanges() {
        TestData.colorPresets(RED, GREEN, BLUE)
        TestData.memory(VIENNA) // Red, as every memory starts.
        openForEditing()
        AddScreen.assertPresetHighlighted(RED)
        AddScreen.assertPresetHighlighted(GREEN, highlighted = false)

        AddScreen.pickColor(GREEN)

        AddScreen.assertPresetHighlighted(GREEN)
        AddScreen.assertPresetHighlighted(RED, highlighted = false)

        val hue = AddScreen.tapHue(HALF)

        assertNotEquals("The hue after the tap", GREEN.hue.toInt(), hue)
        AddScreen.assertPresetHighlighted(GREEN, highlighted = false)
    }

    private fun openForEditing() {
        app.launch()
        Flows.editMemory(VIENNA)
    }

    private companion object {
        const val VIENNA = "Vienna trip"
        const val PICNIC = "Picnic"
        const val PICKED_DAY = 10
        const val ALL_DAY_START_DAYS_AGO = 20L
        const val ALL_DAY_END_DAYS_AGO = 18L
        val RED = Hsv(0f)
        val GREEN = Hsv(120f)
        val BLUE = Hsv(240f)
        const val HALF = 0.5f
    }
}
