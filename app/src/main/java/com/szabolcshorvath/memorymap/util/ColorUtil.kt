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

    fun normalizeHue(hue: Float): Float {
        return (hue % DEGREES_360 + DEGREES_360) % DEGREES_360
    }

    @ColorInt
    fun hueToColor(hue: Float): Int {
        return hsvToColor(hue, 1.0f, 1.0f)
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

        fun luminanceToColor(hsl: FloatArray, luminance: Double): Int {
            // Binary search for lightness in HSL that achieves the target luminance
            var low = 0f
            var high = 1f
            repeat(20) {
                val mid = (low + high) / 2f
                hsl[2] = mid
                val midLum = ColorUtils.calculateLuminance(ColorUtils.HSLToColor(hsl))
                if (midLum < luminance) low = mid else high = mid
            }
            hsl[2] = (low + high) / 2f
            return ColorUtils.HSLToColor(hsl)
        }

        fun contrastRatio(lum1: Double, lum2: Double): Double {
            val lighter = maxOf(lum1, lum2)
            val darker = minOf(lum1, lum2)
            return (lighter + 0.05) / (darker + 0.05)
        }

        val hslCopy = hsl.copyOf()

        // --- Attempt darker result ---
        // We want result to be darker than input, so input is the lighter one:
        // targetContrast = (inputLuminance + 0.05) / (resultLuminance + 0.05)
        // resultLuminance = (inputLuminance + 0.05) / targetContrast - 0.05
        val darkerLuminance = (inputLuminance + 0.05) / targetContrast - 0.05

        if (darkerLuminance in 0.0..inputLuminance) {
            val candidate = luminanceToColor(hslCopy.copyOf(), darkerLuminance)
            val actualContrast =
                contrastRatio(inputLuminance, ColorUtils.calculateLuminance(candidate))
            if (abs(actualContrast - targetContrast) <= 0.1) {
                return candidate
            }
        }

        // --- Input is too dark: even black doesn't reach target contrast ---
        // Fall back to a lighter result
        // targetContrast = (resultLuminance + 0.05) / (inputLuminance + 0.05)
        // resultLuminance = targetContrast * (inputLuminance + 0.05) - 0.05
        val lighterLuminance = targetContrast * (inputLuminance + 0.05) - 0.05

        if (lighterLuminance in inputLuminance..1.0) {
            val candidate = luminanceToColor(hslCopy.copyOf(), lighterLuminance)
            val actualContrast =
                contrastRatio(inputLuminance, ColorUtils.calculateLuminance(candidate))
            if (abs(actualContrast - targetContrast) <= 0.1) {
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

    // BAD
//    @ColorInt
//    fun generateColorWithTargetContrast(@ColorInt argb: Int, targetContrast: Double = 2.5): Int {
//        val srcLum = ColorUtils.calculateLuminance(argb)
//        val goLighter = ColorUtils.calculateContrast(argb, Color.BLACK) < targetContrast
//
//        if (srcLum < 1e-9) {
//            val targetLum = targetContrast * (srcLum + 0.05) - 0.05
//            var grey = (targetLum * 255).roundToInt().coerceIn(0, 255)
//            var result = Color.argb(Color.alpha(argb), grey, grey, grey)
//            while (ColorUtils.calculateContrast(argb, result) < targetContrast) {
//                grey = (grey + 1).coerceAtMost(255)
//                result = Color.argb(Color.alpha(argb), grey, grey, grey)
//            }
//            return result
//        }
//
//        val alpha = Color.alpha(argb)
//        val hsl = FloatArray(3)
//        ColorUtils.colorToHSL(argb, hsl)
//
//        fun buildColor(l: Float): Int {
//            val out = hsl.copyOf()
//            out[2] = l
//            return ColorUtils.HSLToColor(out).let {
//                Color.argb(alpha, Color.red(it), Color.green(it), Color.blue(it))
//            }
//        }
//
//        // Binary search over lightness in the direction we want to go.
//        // We want the value closest to the source that still meets targetContrast.
//        var lo = if (goLighter) hsl[2] else 0f
//        var hi = if (goLighter) 1f else hsl[2]
//
//        repeat(23) {
//            val mid = (lo + hi) / 2f
//            if (ColorUtils.calculateContrast(argb, buildColor(mid)) < targetContrast) {
//                if (goLighter) lo = mid else hi = mid
//            } else {
//                if (goLighter) hi = mid else lo = mid
//            }
//        }
//
//        return buildColor(if (goLighter) hi else lo)
//    }

    // DECENT
//    @ColorInt
//    fun generateColorWithTargetContrast(@ColorInt argb: Int, targetContrast: Double = 2.5): Int {
//        val srcLum = ColorUtils.calculateLuminance(argb)
//        val goLighter = ColorUtils.calculateContrast(argb, Color.BLACK) < targetContrast
//
//        val targetLum = if (goLighter) {
//            targetContrast * (srcLum + 0.05) - 0.05
//        } else {
//            (srcLum + 0.05) / targetContrast - 0.05
//        }.coerceIn(0.0, 1.0)
//
//        if (srcLum < 1e-9) {
//            var grey = (targetLum * 255).roundToInt().coerceIn(0, 255)
//            var result = Color.argb(Color.alpha(argb), grey, grey, grey)
//            while (ColorUtils.calculateContrast(argb, result) < targetContrast) {
//                grey = (grey + 1).coerceAtMost(255)
//                result = Color.argb(Color.alpha(argb), grey, grey, grey)
//            }
//            return result
//        }
//
//        val alpha = Color.alpha(argb)
//        val xyz = DoubleArray(3)
//        ColorUtils.colorToXYZ(argb, xyz)
//
//        fun buildColor(k: Double): Int {
//            val c = ColorUtils.XYZToColor(
//                (xyz[0] * k).coerceIn(0.0, XYZ_WHITE_REFERENCE_X),
//                (xyz[1] * k).coerceIn(0.0, XYZ_WHITE_REFERENCE_Y),
//                (xyz[2] * k).coerceIn(0.0, XYZ_WHITE_REFERENCE_Z),
//            )
//            return Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c))
//        }
//
//        // The closed-form k lands very close but may be just under due to rounding.
//        // Step k one 8-bit quantization unit at a time until contrast is met.
//        val step = if (goLighter) 1.0 / (srcLum * 255) else -1.0 / (srcLum * 255)
//        var k = targetLum / srcLum
//
//        var result = buildColor(k)
//        while (ColorUtils.calculateContrast(argb, result) < targetContrast) {
//            k += step
//            result = buildColor(k)
//        }
//
//        return result
//    }

    // MEDIOCRE
//    fun secondaryColorForMarker(primaryColor: Int): Int {
//        val hsl = FloatArray(3)
//        ColorUtils.colorToHSL(primaryColor, hsl)
//
//        val hue = hsl[0]
//        val saturation = hsl[1]
//        val lightness = hsl[2]
//
//        val newLightness = when {
//            // Handle Black (or very dark colors)
//            lightness < 0.1f -> 0.25f
//
//            // Handle White (or very light colors)
//            lightness > 0.9f -> 0.75f
//
//            // For mid-range colors, darken them to create contrast
//            lightness > 0.6f -> (lightness - 0.25f).coerceIn(0f, 1f)
//
//            // For already dark colors, darken them further or shift slightly
//            else -> (lightness * 0.7f).coerceIn(0f, 1f)
//        }
//
//        // Adjust saturation: If it's grayscale (Black/White), keep saturation at 0.
//        // Otherwise, boost saturation slightly to keep the secondary color "vibrant"
//        val newSaturation = if (saturation > 0.1f) {
//            (saturation + 0.1f).coerceIn(0f, 1f)
//        } else {
//            saturation
//        }
//
//        return ColorUtils.HSLToColor(floatArrayOf(hue, newSaturation, newLightness))
//    }

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
