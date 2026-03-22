package com.szabolcshorvath.memorymap.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import com.google.android.gms.maps.model.BitmapDescriptorFactory

object ColorUtil {

    const val DEFAULT_MARKER_HUE = BitmapDescriptorFactory.HUE_RED
    const val DEFAULT_MARKER_SATURATION = 1.0f
    const val DEFAULT_MARKER_VALUE = 1.0f
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
    private const val PRESET_COLOR_SIZE = 32
    private const val PRESET_COLOR_MARGIN = 12
    private const val DEGREES_360 = 360.0f

    fun normalizeHue(hue: Float): Float {
        return (hue % DEGREES_360 + DEGREES_360) % DEGREES_360
    }

    fun hueToColor(hue: Float): Int {
        return hsvToColor(hue, 1.0f, 1.0f)
    }

    fun hsvToColor(hue: Float, saturation: Float, value: Float): Int {
        check(saturation in 0.0f..1.0f) { "Saturation must be between 0 and 1" }
        check(value in 0.0f..1.0f) { "Value must be between 0 and 1" }
        return Color.HSVToColor(floatArrayOf(normalizeHue(hue), saturation, value))
    }

    fun getPresetColorView(
        context: Context,
        hue: Float,
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
        shape.setColor(hsvToColor(hue, DEFAULT_MARKER_SATURATION, DEFAULT_MARKER_VALUE))
        shape.setStroke((1 * context.resources.displayMetrics.density).toInt(), Color.LTGRAY)
        view.background = shape

        view.setOnClickListener(onClickListener)
        return view
    }
}
