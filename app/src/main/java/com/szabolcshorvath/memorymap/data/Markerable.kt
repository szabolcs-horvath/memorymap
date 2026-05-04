package com.szabolcshorvath.memorymap.data

import android.location.Location
import androidx.recyclerview.widget.DiffUtil
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.perf.metrics.AddTrace
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

    fun getFormattedDate(): String?

    @AddTrace(name = "markerable_is_same_location_as", enabled = true)
    fun isSameLocationAs(other: Markerable): Boolean {
        if (placeAndAddressPresentForBoth(other)) {
            if (placeName == other.placeName && address == other.address) return true
        }

        val results = FloatArray(1)
        Location.distanceBetween(latitude, longitude, other.latitude, other.longitude, results)
        return results[0] < SAME_LOCATION_METERS_THRESHOLD
    }

    private fun placeAndAddressPresentForBoth(other: Markerable): Boolean =
        placeName != null && address != null && other.placeName != null && other.address != null

    data class MarkerableCluster(val items: List<Markerable>) : ClusterItem {
        override fun getPosition(): LatLng {
            val first = items.first()
            return LatLng(first.latitude, first.longitude)
        }

        override fun getTitle(): String? = if (items.size == 1) items.first().title else "${items.size} Memories"

        override fun getSnippet(): String? = null

        override fun getZIndex(): Float? = null
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
        const val SAME_LOCATION_METERS_THRESHOLD = 20.0f
        const val TITLE_DIFF_PAYLOAD = "TITLE"
        const val DATE_DIFF_PAYLOAD = "DATE"
        const val COLOR_DIFF_PAYLOAD = "COLOR"
    }
}
