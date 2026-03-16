package com.szabolcshorvath.memorymap.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import com.google.android.gms.maps.model.BitmapDescriptorFactory

object ColorUtil {

    const val DEFAULT_MARKER_HUE = BitmapDescriptorFactory.HUE_RED
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
    private const val PRESET_COLOR_SIZE = 32
    private const val PRESET_COLOR_MARGIN = 12

    fun normalizeHue(hue: Float): Float {
        return (hue % 360.0f + 360.0f) % 360.0f
    }

    fun hueToColor(hue: Float): Int {
        return Color.HSVToColor(floatArrayOf(normalizeHue(hue), 1.0f, 1.0f))
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
        shape.setColor(hueToColor(hue))
        shape.setStroke((1 * context.resources.displayMetrics.density).toInt(), Color.LTGRAY)
        view.background = shape

        view.setOnClickListener(onClickListener)
        return view
    }
}
