package com.szabolcshorvath.memorymap.data

import android.content.Context
import androidx.annotation.ColorInt
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
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

    data class MarkerableCluster(val items: List<Markerable>) : ClusterItem {
        /** The middle of the items. Longitudes are taken relative to the first item, so a pin on the 180th meridian stays there. */
        override val position: LatLng = run {
            val first = items.first()
            val longitudeOffset = items.sumOf { MarkerClustering.wrapLongitude(it.longitude - first.longitude) } / items.size
            LatLng(items.sumOf { it.latitude } / items.size, first.longitude + longitudeOffset) // LatLng wraps the longitude
        }

        override val title: String?
            get() = if (items.size == 1) items.first().title else null // The map names a pin of several memories itself, in the phone's language.

        override val snippet: String? = null

        override val zIndex: Float? = null
    }
}
