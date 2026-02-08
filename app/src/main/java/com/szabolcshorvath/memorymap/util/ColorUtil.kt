package com.szabolcshorvath.memorymap.util

import android.graphics.Color

object ColorUtil {

    fun normalizeHue(hue: Float): Float {
        return (hue % 360.0f + 360.0f) % 360.0f
    }

    fun hueToColor(hue: Float): Int {
        return Color.HSVToColor(floatArrayOf(normalizeHue(hue), 1.0f, 1.0f))
    }

    fun colorToHSV(color: Int): FloatArray {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv
    }

    fun textColorForBackground(backgroundColor: Int): Int {
        val darkness = 1 - (0.299 * Color.red(backgroundColor) +
                0.587 * Color.green(backgroundColor) +
                0.114 * Color.blue(backgroundColor)
                ) / 255
        return if (darkness < 0.5) Color.BLACK else Color.WHITE
    }
}