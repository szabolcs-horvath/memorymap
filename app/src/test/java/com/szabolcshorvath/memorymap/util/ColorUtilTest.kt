package com.szabolcshorvath.memorymap.util

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.szabolcshorvath.memorymap.util.ColorUtil.HSV_BLACK
import com.szabolcshorvath.memorymap.util.ColorUtil.HSV_WHITE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.math.abs

@ExtendWith(RobolectricExtension::class)
class ColorUtilTest {

    companion object {
        @JvmStatic
        fun normalizeHueParameters() = listOf(
            Arguments.of(-1080.0f, 0.0f),
            Arguments.of(-721.0f, 359.0f),
            Arguments.of(-720.0f, 0.0f),
            Arguments.of(-361.0f, 359.0f),
            Arguments.of(-360.0f, 0.0f),
            Arguments.of(-1.0f, 359.0f),
            Arguments.of(0.0f, 0.0f),
            Arguments.of(1.0f, 1.0f),
            Arguments.of(90.0f, 90.0f),
            Arguments.of(180.0f, 180.0f),
            Arguments.of(360.0f, 0.0f),
            Arguments.of(361.0f, 1.0f),
            Arguments.of(720.0f, 0.0f),
            Arguments.of(721.0f, 1.0f),
            Arguments.of(1080.0f, 0.0f),
            Arguments.of(1081.0f, 1.0f)
        )

        @JvmStatic
        fun generateColorWithTargetContrastParameters(): List<Arguments> {
            val arguments = mutableListOf<Arguments>(
                Arguments.of(HSV_BLACK[0], HSV_BLACK[1], HSV_BLACK[2]),
                Arguments.of(HSV_WHITE[0], HSV_WHITE[1], HSV_WHITE[2])
            )
            for (h in 0..360 step 5) {
                for (s in 5..100 step 5) {
                    for (v in 5..100 step 5) {
                        arguments.add(
                            Arguments.of(
                                h.toFloat(),
                                s.toFloat() / 100f,
                                v.toFloat() / 100f
                            )
                        )
                    }
                }
            }
            return arguments
        }

        private const val TARGET_CONTRAST = 2.0
        private const val TARGET_CONTRAST_THRESHOLD = 0.1
    }

    @MethodSource("normalizeHueParameters")
    @ParameterizedTest
    fun normalizeHue(hue: Float, expected: Float) {
        assertEquals(expected, ColorUtil.normalizeHue(hue))
    }

    @MethodSource("generateColorWithTargetContrastParameters")
    @ParameterizedTest(name = "generateColorWithTargetContrast: H:{0}, S:{1}, V:{2}")
    fun generateColorWithTargetContrast(hue: Float, saturation: Float, value: Float) {
        val primaryColor = Color.HSVToColor(floatArrayOf(hue, saturation, value))

        val secondaryColor =
            ColorUtil.generateColorWithTargetContrast(primaryColor, TARGET_CONTRAST)

        val contrast = ColorUtils.calculateContrast(secondaryColor, primaryColor)
        assertTrue(
            abs(contrast - TARGET_CONTRAST) < TARGET_CONTRAST_THRESHOLD,
            "Contrast $contrast is not sufficient for primary color $primaryColor (H:$hue, S:$saturation, V:$value) and secondary color $secondaryColor"
        )
    }
}
