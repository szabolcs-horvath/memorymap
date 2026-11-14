package com.szabolcshorvath.memorymap.data

import android.content.Context
import androidx.annotation.ColorInt
import com.szabolcshorvath.memorymap.util.ColorUtil
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil
import java.time.ZonedDateTime

interface Markerable {
    val groupId: Int
    val title: String?
    val latitude: Double
    val longitude: Double
    val placeName: String?
    val address: String?
    val startDate: ZonedDateTime?
    val endDate: ZonedDateTime?
    val isAllDay: Boolean
    val markerHue: Float?
    val markerSaturation: Float?
    val markerBrightness: Float?

    /** The date or date range, with times unless it is all-day, as the phone shows dates and times. Null without dates. */
    fun getFormattedDate(context: Context): String? {
        val start = startDate
        val end = endDate
        return if (start != null && end != null) DateTimeFormatterUtil.dateRange(context, start, end, isAllDay) else null
    }

    /** The marker's colour, with the default for any part of it that isn't set. */
    @ColorInt
    fun markerColor(): Int = ColorUtil.hsvToColor(
        markerHue ?: ColorUtil.DEFAULT_MARKER_HUE,
        markerSaturation ?: ColorUtil.DEFAULT_MARKER_SATURATION,
        markerBrightness ?: ColorUtil.DEFAULT_MARKER_BRIGHTNESS
    )

    /**
     * A fragment as the map shows it: under its memory's [title]. Room's objects are shared with every screen that
     * reads the same data, so the map wraps a fragment instead of changing it. The title is part of its equality, so a
     * renamed memory's fragment counts as changed.
     */
    data class FragmentMarker(val fragment: MemoryFragment, override val title: String) : Markerable by fragment
}
