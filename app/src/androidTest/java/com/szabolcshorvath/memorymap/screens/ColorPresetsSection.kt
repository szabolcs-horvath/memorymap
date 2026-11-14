package com.szabolcshorvath.memorymap.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.screens.ComposeUi.dragOnto
import com.szabolcshorvath.memorymap.screens.ComposeUi.press
import com.szabolcshorvath.memorymap.screens.ComposeUi.text
import com.szabolcshorvath.memorymap.testing.TestData.Hsv
import org.junit.Assert.assertEquals
import java.util.Locale

/**
 * The colour presets section of Settings: the swatches and the sliders that edit the selected one. [select] and
 * [assertSlidersShow] also work for the Add form's marker colour, which has the same swatches and sliders (the
 * memory's: a fragment's own swatches are left out). Found through [ComposeUi].
 */
object ColorPresetsSection {
    fun open() {
        val header = ComposeUi.scrollTo("The colour presets", hasText(HEADER))
        if (!ComposeUi.exists(hasText(ADD))) header.press()
        ComposeUi.scrollTo("\"$ADD\"", hasText(ADD))
    }

    fun select(color: Hsv) {
        ComposeUi.scrollTo("The preset ${describe(color)}", swatch(color)).press()
    }

    fun assertShown(color: Hsv) {
        ComposeUi.scrollTo("The preset ${describe(color)}", swatch(color))
    }

    /** No preset is [color]. The swatches are one row, all composed once the row is: scrolled to it, it is complete. */
    fun assertGone(color: Hsv) {
        ComposeUi.scrollTo("The presets", anySwatch)
        ComposeUi.assertGone("The preset ${describe(color)}", swatch(color))
    }

    fun assertSelected(color: Hsv) {
        ComposeUi.scrollTo("The preset ${describe(color)}", swatch(color))
        ComposeUi.waitFor("The preset ${describe(color)}, selected", swatch(color) and isSelected())
    }

    fun assertNotSelected(color: Hsv) {
        ComposeUi.scrollTo("The preset ${describe(color)}", swatch(color))
        ComposeUi.waitFor("The preset ${describe(color)}, not selected", swatch(color) and isNotSelected())
    }

    /** The presets appear in this order, from left to right. */
    fun assertLeftToRight(vararg colors: Hsv) {
        ComposeUi.scrollTo("The preset ${describe(colors.first())}", swatch(colors.first()))
        val lefts = colors.map { color -> ComposeUi.node(swatch(color)).fetchSemanticsNode().boundsInRoot.left }
        assertEquals("The order of the presets ${colors.map(::describe)}", lefts.sorted(), lefts)
    }

    /** The sliders show [color], as they do for the selected preset. */
    fun assertSlidersShow(color: Hsv) {
        assertValue("Hue", HUE_VALUE, color.hue.toInt().toString())
        assertValue("Saturation", SATURATION_VALUE, decimal(color.saturation))
        assertValue("Brightness", BRIGHTNESS_VALUE, decimal(color.brightness))
    }

    private fun assertValue(what: String, tag: String, expected: String) {
        val read = { ComposeUi.scrollTo("The $what value", ofMemory(hasTestTag(tag))).text() }
        assertEquals(what, expected, Ui.eventually(read = read) { it == expected })
    }

    /** Taps the hue slider at [fraction] of its width (0 left, 1 right) and returns the hue it shows then. */
    fun tapHue(fraction: Float): Int {
        ComposeUi.scrollTo("The hue slider", ofMemory(hasContentDescription(HUE)))
            .performTouchInput { click(percentOffset(fraction, CENTER)) }
        return ComposeUi.node(ofMemory(hasTestTag(HUE_VALUE))).text().toInt()
    }

    fun save() = tap("Save")

    fun undo() = tap("Undo")

    fun addCurrentColor() = tap(ADD)

    /** Taps "Delete preset", which asks first ([Dialog] with [DELETE_QUESTION]). */
    fun delete() = tap("Delete preset")

    /** Long-presses the preset [color] and drags it into the place of [onto]. */
    fun drag(color: Hsv, onto: Hsv) {
        ComposeUi.scrollTo("The preset ${describe(color)}", swatch(color))
        ComposeUi.node(swatch(color)).dragOnto(ComposeUi.node(swatch(onto)), horizontal = true)
    }

    const val DELETE_QUESTION = "Are you sure you want to delete this color preset?"

    private fun tap(text: String) {
        ComposeUi.scrollTo("\"$text\"", hasText(text) and isEnabled()).press()
    }

    /** [matcher], outside the Add form's fragment cards (Settings has none). */
    private fun ofMemory(matcher: SemanticsMatcher) = matcher and !AddScreen.inFragment

    private fun swatch(color: Hsv) = ofMemory(hasContentDescription(describe(color)))

    private val anySwatch = ofMemory(
        SemanticsMatcher("is a colour preset") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith(SWATCH_PREFIX) }
        }
    )

    /** How the app describes the swatch of [color] to screen readers. */
    fun describe(color: Hsv): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(
        R.string.color_preset_description,
        color.hue.toInt(),
        color.saturation,
        color.brightness
    )

    private fun decimal(value: Float) = String.format(Locale.getDefault(), "%.2f", value)

    private const val HEADER = "Color presets"
    private const val ADD = "Add current as new preset"
    private const val HUE = "Hue"
    private const val HUE_VALUE = "tv_HueValue"
    private const val SATURATION_VALUE = "tv_SaturationValue"
    private const val BRIGHTNESS_VALUE = "tv_BrightnessValue"
    private const val SWATCH_PREFIX = "Color preset: "
    private const val CENTER = 0.5f
}
