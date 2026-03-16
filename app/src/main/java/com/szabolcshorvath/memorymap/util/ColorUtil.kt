package com.szabolcshorvath.memorymap.util

import android.graphics.Color
import com.google.android.gms.maps.model.BitmapDescriptorFactory

object ColorUtil {

    val COLOR_PRESETS = listOf(
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

    fun normalizeHue(hue: Float): Float {
        return (hue % 360.0f + 360.0f) % 360.0f
    }

    fun hueToColor(hue: Float): Int {
        return Color.HSVToColor(floatArrayOf(normalizeHue(hue), 1.0f, 1.0f))
    }
}
