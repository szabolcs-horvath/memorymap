package com.szabolcshorvath.memorymap.util

import android.graphics.Color
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
    val DEFAULT_HSV_PRESETS = listOf(HSV_BLACK, HSV_WHITE) + HUE_PRESETS.map {
        floatArrayOf(it, DEFAULT_MARKER_SATURATION, DEFAULT_MARKER_BRIGHTNESS)
    }

    private const val DEGREES_360 = 360.0f
    private const val MIN_TARGET_CONTRAST = 1.0
    private const val MAX_TARGET_CONTRAST = 21.0
    private const val TARGET_CONTRAST_THRESHOLD = 0.1
    private const val LUMINANCE_BINARY_SEARCH_ITERATIONS = 20
    private const val CONTRAST_RATION_CALIBRATION_CONSTANT = 0.05

    fun normalizeHue(hue: Float): Float {
        return (hue % DEGREES_360 + DEGREES_360) % DEGREES_360
    }

    @ColorInt
    fun hsvToColor(vararg hsv: Float): Int {
        @Suppress("MagicNumber")
        check(hsv.size == 3) { "You must provide exactly 3 values for HSV" }
        return hsvToColor(hsv[0], hsv[1], hsv[2])
    }

    @ColorInt
    fun hsvToColor(hue: Float, saturation: Float, brightness: Float): Int {
        // The values come from the database and from backups, so out-of-range ones are clamped instead of failing.
        val h = if (hue.isFinite()) normalizeHue(hue) else DEFAULT_MARKER_HUE
        val s = if (saturation.isNaN()) DEFAULT_MARKER_SATURATION else saturation.coerceIn(0.0f, 1.0f)
        val v = if (brightness.isNaN()) DEFAULT_MARKER_BRIGHTNESS else brightness.coerceIn(0.0f, 1.0f)
        return Color.HSVToColor(floatArrayOf(h, s, v))
    }

    @ColorInt
    fun generateColorWithTargetContrast(@ColorInt inputColor: Int, targetContrast: Double): Int {
        require(targetContrast in MIN_TARGET_CONTRAST..MAX_TARGET_CONTRAST) {
            "Target contrast must be between 1.0 and 21.0, was $targetContrast"
        }

        @Suppress("MagicNumber")
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(inputColor, hsl)
        val inputLuminance = ColorUtils.calculateLuminance(inputColor)

        // Try darker first: we need resultLuminance such that contrast(input, result) = targetContrast
        // contrast = (lighter + 0.05) / (darker + 0.05)
        // If input is lighter: result = (input + 0.05) / targetContrast - 0.05
        // If input is darker:  result = (input + 0.05) * targetContrast - 0.05

        // --- Attempt darker result ---
        // We want result to be darker than input, so input is the lighter one:
        // targetContrast = (inputLuminance + 0.05) / (resultLuminance + 0.05)
        // resultLuminance = (inputLuminance + 0.05) / targetContrast - 0.05
        val darkerLuminance = (inputLuminance + CONTRAST_RATION_CALIBRATION_CONSTANT) / targetContrast - CONTRAST_RATION_CALIBRATION_CONSTANT

        // --- Then a lighter result, for an input too dark for even black to reach the target contrast ---
        // targetContrast = (resultLuminance + 0.05) / (inputLuminance + 0.05)
        // resultLuminance = targetContrast * (inputLuminance + 0.05) - 0.05
        val lighterLuminance = targetContrast * (inputLuminance + CONTRAST_RATION_CALIBRATION_CONSTANT) - CONTRAST_RATION_CALIBRATION_CONSTANT

        // Not expected for the contrasts the app asks for: then black or white, whichever contrasts more, is the closest.
        return candidateWithContrast(hsl, inputLuminance, darkerLuminance, 0.0..inputLuminance, targetContrast)
            ?: candidateWithContrast(hsl, inputLuminance, lighterLuminance, inputLuminance..1.0, targetContrast)
            ?: if (contrastRatio(inputLuminance, 0.0) >= contrastRatio(inputLuminance, 1.0)) Color.BLACK else Color.WHITE
    }

    /** The colour with [hsl]'s hue and saturation and [luminance], if that is in [range] and reaches [targetContrast]. */
    private fun candidateWithContrast(
        hsl: FloatArray,
        inputLuminance: Double,
        luminance: Double,
        range: ClosedFloatingPointRange<Double>,
        targetContrast: Double
    ): Int? {
        if (luminance !in range) return null
        val candidate = luminanceToColor(hsl.copyOf(), luminance)
        val actualContrast = contrastRatio(inputLuminance, ColorUtils.calculateLuminance(candidate))
        return candidate.takeIf { abs(actualContrast - targetContrast) <= TARGET_CONTRAST_THRESHOLD }
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
}
