package com.szabolcshorvath.memorymap.data

import android.content.Context
import androidx.recyclerview.widget.DiffUtil
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
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

    /** The date or date range, with times unless it is all-day, as the phone shows dates and times. */
    fun getFormattedDate(context: Context): String?

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
            get() = if (items.size == 1) items.first().title else "${items.size} Memories"

        override val snippet: String? = null

        override val zIndex: Float? = null
    }

    class MarkerableDiffCallback : DiffUtil.ItemCallback<Markerable>() {
        override fun areItemsTheSame(oldItem: Markerable, newItem: Markerable): Boolean {
            return oldItem.groupId == newItem.groupId && oldItem.latitude == newItem.latitude && oldItem.longitude == newItem.longitude
        }

        override fun areContentsTheSame(oldItem: Markerable, newItem: Markerable): Boolean {
            return oldItem.title == newItem.title &&
                oldItem.startDate == newItem.startDate &&
                oldItem.endDate == newItem.endDate &&
                oldItem.markerHue == newItem.markerHue &&
                oldItem.markerSaturation == newItem.markerSaturation &&
                oldItem.markerBrightness == newItem.markerBrightness &&
                oldItem.latitude == newItem.latitude &&
                oldItem.longitude == newItem.longitude
        }

        override fun getChangePayload(oldItem: Markerable, newItem: Markerable): Any? {
            val diff = mutableSetOf<String>()

            if (oldItem.title != newItem.title) {
                diff.add(TITLE_DIFF_PAYLOAD)
            }

            if (oldItem.startDate != newItem.startDate || oldItem.endDate != newItem.endDate) {
                diff.add(DATE_DIFF_PAYLOAD)
            }

            if (oldItem.markerHue != newItem.markerHue ||
                oldItem.markerSaturation != newItem.markerSaturation ||
                oldItem.markerBrightness != newItem.markerBrightness
            ) {
                diff.add(COLOR_DIFF_PAYLOAD)
            }

            return if (diff.isEmpty()) null else diff
        }
    }

    companion object {
        const val TITLE_DIFF_PAYLOAD = "TITLE"
        const val DATE_DIFF_PAYLOAD = "DATE"
        const val COLOR_DIFF_PAYLOAD = "COLOR"
    }
}
