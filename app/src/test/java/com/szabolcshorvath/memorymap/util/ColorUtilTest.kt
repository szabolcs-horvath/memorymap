package com.szabolcshorvath.memorymap.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

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
    }

    @MethodSource("normalizeHueParameters")
    @ParameterizedTest
    fun normalizeHue(hue: Float, expected: Float) {
        assertEquals(expected, ColorUtil.normalizeHue(hue))
    }
}