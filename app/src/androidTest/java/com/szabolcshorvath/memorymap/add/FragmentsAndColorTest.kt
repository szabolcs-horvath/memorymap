package com.szabolcshorvath.memorymap.add

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.screens.AddScreen
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.DatePickerDialog
import com.szabolcshorvath.memorymap.screens.DateRangePickerDialog
import com.szabolcshorvath.memorymap.screens.Dialog
import com.szabolcshorvath.memorymap.screens.LocationPickerScreen
import com.szabolcshorvath.memorymap.screens.MemoryScreen
import com.szabolcshorvath.memorymap.screens.TimelineScreen
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import com.szabolcshorvath.memorymap.testing.TestData.Hsv
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * A memory's fragments and marker colour on the Add form: a fragment's own place, dates and colour. The tests edit
 * an existing memory, which already has a location, so only a fragment's location needs the map. (Fragments keep
 * the order they were added in; the form has no way to reorder them.)
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

    @Test
    fun aFragmentKeepsDatesOfItsOwn() {
        TestData.memory(VIENNA)
        openForEditing()

        AddScreen.addFragment()
        AddScreen.openFragmentStartDatePicker()
        DatePickerDialog.pickDayInPreviousMonth(PICKED_DAY)
        Dialog.tap("OK")
        AddScreen.save()

        MemoryScreen.assertShowing(VIENNA)
        MemoryScreen.assertFragmentStartsOn(LocalDate.now().minusMonths(1).withDayOfMonth(PICKED_DAY))
    }

    @Test
    fun anAllDayFragmentKeepsItsOwnDays() {
        TestData.memory(VIENNA)
        openForEditing()
        val start = LocalDate.now().minusDays(ALL_DAY_START_DAYS_AGO)
        val end = LocalDate.now().minusDays(ALL_DAY_END_DAYS_AGO)

        AddScreen.addFragment()
        AddScreen.openFragmentAllDayRangePicker()
        DateRangePickerDialog.pick(start, end)
        AddScreen.save()

        MemoryScreen.assertShowing(VIENNA)
        val format = DateTimeFormatterUtil.dateFormatter()
        MemoryScreen.assertFragmentDates("${start.format(format)} - ${end.format(format)}")
    }

    @Test
    fun aFragmentKeepsAColorOfItsOwn() {
        TestData.colorPresets(RED, GREEN, BLUE)
        TestData.memory(VIENNA)
        openForEditing()
        AddScreen.pickColor(RED)

        AddScreen.addFragment() // It starts with the memory's colour.
        AddScreen.pickFragmentColor(BLUE)
        AddScreen.save()

        MemoryScreen.assertShowing(VIENNA)
        MemoryScreen.assertFragmentColor(ColorUtil.hsvToColor(BLUE.hue, BLUE.saturation, BLUE.brightness))
        MemoryScreen.edit()
        AddScreen.assertColor(RED)
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
        const val PICKED_DAY = 10
        const val ALL_DAY_START_DAYS_AGO = 20L
        const val ALL_DAY_END_DAYS_AGO = 18L
        val RED = Hsv(0f)
        val GREEN = Hsv(120f)
        val BLUE = Hsv(240f)
    }
}
