package com.szabolcshorvath.memorymap.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.szabolcshorvath.memorymap.screens.BottomNav
import com.szabolcshorvath.memorymap.screens.BottomNav.Tab
import com.szabolcshorvath.memorymap.screens.ColorPresetsSection
import com.szabolcshorvath.memorymap.screens.Dialog
import com.szabolcshorvath.memorymap.testing.MemoryMapAppRule
import com.szabolcshorvath.memorymap.testing.TestData
import com.szabolcshorvath.memorymap.testing.TestData.Hsv
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The colour presets in Settings: selecting, editing with Save and Undo, adding, deleting and reordering them. */
@RunWith(AndroidJUnit4::class)
class ColorPresetsTest {
    @get:Rule
    val app = MemoryMapAppRule()

    @Before
    fun openPresets() {
        TestData.colorPresets(RED, GREEN, BLUE)
        app.launch()
        BottomNav.open(Tab.SETTINGS)
        ColorPresetsSection.open()
    }

    @Test
    fun selectingAPresetShowsItInTheSliders() {
        ColorPresetsSection.select(GREEN)

        ColorPresetsSection.assertSelected(GREEN)
        ColorPresetsSection.assertSlidersShow(GREEN)
    }

    @Test
    fun aSavedChangeStays() {
        ColorPresetsSection.select(RED)
        val hue = ColorPresetsSection.tapHue(HALF_WAY)
        assertNotEquals("The hue slider did not move", RED.hue.toInt(), hue)

        ColorPresetsSection.save()
        leaveAndComeBack()

        ColorPresetsSection.assertShown(Hsv(hue.toFloat()))
        ColorPresetsSection.assertGone(RED)
    }

    @Test
    fun undoPutsThePresetBack() {
        ColorPresetsSection.select(RED)
        val hue = ColorPresetsSection.tapHue(HALF_WAY)
        ColorPresetsSection.assertShown(Hsv(hue.toFloat())) // The swatch follows the sliders.

        ColorPresetsSection.undo()

        ColorPresetsSection.assertShown(RED)
        ColorPresetsSection.assertSlidersShow(RED)
    }

    @Test
    fun addingSavesTheSlidersColorAsANewPresetAndLeavesTheEditedOneAsItWas() {
        ColorPresetsSection.select(RED)
        val hue = ColorPresetsSection.tapHue(HALF_WAY)

        ColorPresetsSection.addCurrentColor()

        val added = Hsv(hue.toFloat())
        ColorPresetsSection.assertShown(added)
        ColorPresetsSection.assertShown(RED)
        ColorPresetsSection.assertLeftToRight(RED, GREEN, BLUE, added)
    }

    @Test
    fun deletingAPresetAsksFirst() {
        ColorPresetsSection.select(GREEN)

        ColorPresetsSection.delete()
        Dialog.assertShown(ColorPresetsSection.DELETE_QUESTION)
        Dialog.tap("Cancel")
        Dialog.assertGone(ColorPresetsSection.DELETE_QUESTION)
        ColorPresetsSection.assertShown(GREEN)

        ColorPresetsSection.delete()
        Dialog.tap("Delete")

        ColorPresetsSection.assertGone(GREEN)
        ColorPresetsSection.assertLeftToRight(RED, BLUE)
    }

    @Test
    fun draggingAPresetChangesTheOrderForGood() {
        ColorPresetsSection.drag(BLUE, onto = RED)
        ColorPresetsSection.assertLeftToRight(BLUE, RED, GREEN)

        leaveAndComeBack()

        ColorPresetsSection.assertLeftToRight(BLUE, RED, GREEN)
    }

    /** Switches to another tab and back, which builds Settings again from what was saved. */
    private fun leaveAndComeBack() {
        BottomNav.open(Tab.MAP)
        BottomNav.open(Tab.SETTINGS)
        ColorPresetsSection.open()
    }

    private companion object {
        val RED = Hsv(0f)
        val GREEN = Hsv(120f)
        val BLUE = Hsv(240f)
        const val HALF_WAY = 0.5f
    }
}
