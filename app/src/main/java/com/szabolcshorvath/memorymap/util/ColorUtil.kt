package com.szabolcshorvath.memorymap.util

import android.graphics.Color

object ColorUtil {

    fun normalizeHue(hue: Float): Float {
        return (hue % 360.0f + 360.0f) % 360.0f
    }

    fun hueToColor(hue: Float): Int {
        return Color.HSVToColor(floatArrayOf(normalizeHue(hue), 1.0f, 1.0f))
    }
}