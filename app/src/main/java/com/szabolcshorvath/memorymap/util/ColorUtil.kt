package com.szabolcshorvath.memorymap.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlin.math.abs

object ColorUtil {

    const val DEFAULT_MARKER_HUE = BitmapDescriptorFactory.HUE_RED
    const val DEFAULT_MARKER_SATURATION = 1.0f
    const val DEFAULT_MARKER_BRIGHTNESS = 1.0f
    val HUE_PRESETS = listOf(
        BitmapDescriptorFactory.HUE_RED,
        BitmapDescriptorFactory.HUE_ORANGE,
        BitmapDescriptorFactory.HUE_YELLOW,
        BitmapDescriptorFactory.HUE_GREEN,
        BitmapDescriptorFactory.HUE_CYAN,
        BitmapDescriptorFactory.HUE_AZURE,
        BitmapDescriptorFactory.HUE_BLUE,
        BitmapDescriptorFactory.HUE_VIOLET,
        BitmapDescriptorFactory.HUE_MAGENTA,
        BitmapDescriptorFactory.HUE_ROSE
    )
    val HSV_BLACK = floatArrayOf(DEFAULT_MARKER_HUE, DEFAULT_MARKER_SATURATION, 0.0f)
    val HSV_WHITE = floatArrayOf(DEFAULT_MARKER_HUE, 0.0f, DEFAULT_MARKER_BRIGHTNESS)
    val HSV_PRESETS = listOf(HSV_BLACK, HSV_WHITE) + HUE_PRESETS.map {
        floatArrayOf(it, DEFAULT_MARKER_SATURATION, DEFAULT_MARKER_BRIGHTNESS)
    }

    private const val PRESET_COLOR_SIZE = 32
    private const val PRESET_COLOR_MARGIN = 12
    private const val DEGREES_360 = 360.0f
    private const val TARGET_CONTRAST_THRESHOLD = 0.1
    private const val LUMINANCE_BINARY_SEARCH_ITERATIONS = 20
    private const val CONTRAST_RATION_CALIBRATION_CONSTANT = 0.05

    fun normalizeHue(hue: Float): Float {
        return (hue % DEGREES_360 + DEGREES_360) % DEGREES_360
    }

    @ColorInt
    fun hsvToColor(vararg hsv: Float): Int {
        check(hsv.size == 3) { "You must provide exactly 3 values for HSV" }
        return hsvToColor(hsv[0], hsv[1], hsv[2])
    }

    @ColorInt
    fun hsvToColor(hue: Float, saturation: Float, brightness: Float): Int {
        check(saturation in 0.0f..1.0f) { "Saturation must be between 0 and 1" }
        check(brightness in 0.0f..1.0f) { "Brightness must be between 0 and 1" }
        return Color.HSVToColor(floatArrayOf(normalizeHue(hue), saturation, brightness))
    }

    @ColorInt
    fun generateColorWithTargetContrast(@ColorInt inputColor: Int, targetContrast: Double): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(inputColor, hsl)

        val inputLuminance = ColorUtils.calculateLuminance(inputColor)

        // Try darker first: we need resultLuminance such that contrast(input, result) = targetContrast
        // contrast = (lighter + 0.05) / (darker + 0.05)
        // If input is lighter: result = (input + 0.05) / targetContrast - 0.05
        // If input is darker:  result = (input + 0.05) * targetContrast - 0.05

        val hslCopy = hsl.copyOf()

        // --- Attempt darker result ---
        // We want result to be darker than input, so input is the lighter one:
        // targetContrast = (inputLuminance + 0.05) / (resultLuminance + 0.05)
        // resultLuminance = (inputLuminance + 0.05) / targetContrast - 0.05
        val darkerLuminance =
            (inputLuminance + CONTRAST_RATION_CALIBRATION_CONSTANT) / targetContrast - CONTRAST_RATION_CALIBRATION_CONSTANT

        if (darkerLuminance in 0.0..inputLuminance) {
            val candidate = luminanceToColor(hslCopy.copyOf(), darkerLuminance)
            val actualContrast =
                contrastRatio(inputLuminance, ColorUtils.calculateLuminance(candidate))
            if (abs(actualContrast - targetContrast) <= TARGET_CONTRAST_THRESHOLD) {
                return candidate
            }
        }

        // --- Input is too dark: even black doesn't reach target contrast ---
        // Fall back to a lighter result
        // targetContrast = (resultLuminance + 0.05) / (inputLuminance + 0.05)
        // resultLuminance = targetContrast * (inputLuminance + 0.05) - 0.05
        val lighterLuminance =
            targetContrast * (inputLuminance + CONTRAST_RATION_CALIBRATION_CONSTANT) - CONTRAST_RATION_CALIBRATION_CONSTANT

        if (lighterLuminance in inputLuminance..1.0) {
            val candidate = luminanceToColor(hslCopy.copyOf(), lighterLuminance)
            val actualContrast =
                contrastRatio(inputLuminance, ColorUtils.calculateLuminance(candidate))
            if (abs(actualContrast - targetContrast) <= TARGET_CONTRAST_THRESHOLD) {
                return candidate
            }
        }

        // --- Last resort: clamp to black or white, whichever is closer to target ---
        val blackContrast = contrastRatio(inputLuminance, 0.0)
        val whiteContrast = contrastRatio(inputLuminance, 1.0)
        return if (abs(blackContrast - targetContrast) <= abs(whiteContrast - targetContrast)) {
            Color.BLACK
        } else {
            Color.WHITE
        }
    }

    private fun luminanceToColor(hsl: FloatArray, luminance: Double): Int {
        // Binary search for lightness in HSL that achieves the target luminance
        var low = 0f
        var high = 1f
        repeat(LUMINANCE_BINARY_SEARCH_ITERATIONS) {
            val mid = (low + high) / 2f
            hsl[2] = mid
            val midLum = ColorUtils.calculateLuminance(ColorUtils.HSLToColor(hsl))
            if (midLum < luminance) low = mid else high = mid
        }
        hsl[2] = (low + high) / 2f
        return ColorUtils.HSLToColor(hsl)
    }

    private fun contrastRatio(lum1: Double, lum2: Double): Double {
        val lighter = maxOf(lum1, lum2)
        val darker = minOf(lum1, lum2)
        return (lighter + CONTRAST_RATION_CALIBRATION_CONSTANT) / (darker + CONTRAST_RATION_CALIBRATION_CONSTANT)
    }

    fun getPresetColorView(
        context: Context,
        @ColorInt color: Int,
        onClickListener: View.OnClickListener?
    ): View {
        val size = (PRESET_COLOR_SIZE * context.resources.displayMetrics.density).toInt()
        val margin = (PRESET_COLOR_MARGIN * context.resources.displayMetrics.density).toInt()

        val view = View(context)
        val params = LinearLayout.LayoutParams(size, size)
        params.setMargins(0, 0, margin, 0)
        view.layoutParams = params

        val shape = GradientDrawable()
        shape.shape = GradientDrawable.OVAL
        shape.setColor(color)
        shape.setStroke((1 * context.resources.displayMetrics.density).toInt(), Color.LTGRAY)
        view.background = shape

        view.setOnClickListener(onClickListener)
        return view
    }
}
