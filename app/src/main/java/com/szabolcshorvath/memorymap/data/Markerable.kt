package com.szabolcshorvath.memorymap.data

import android.location.Location
import java.time.ZonedDateTime

interface Markerable {
    val groupId: Int
    val title: String
    val latitude: Double
    val longitude: Double
    val placeName: String?
    val address: String?
    val startDate: ZonedDateTime?
    val endDate: ZonedDateTime?
    val isAllDay: Boolean
    val markerHue: Float?

    fun getFormattedDate(): String?

    fun isSameLocationAs(other: Markerable): Boolean {
        if (placeAndAddressPresentForBoth(other)) {
            if (placeName == other.placeName && address == other.address) return true
        }

        val results = FloatArray(1)
        Location.distanceBetween(
            latitude,
            longitude,
            other.latitude,
            other.longitude,
            results
        )
        return results[0] < SAME_LOCATION_METERS_THRESHOLD
    }

    private fun placeAndAddressPresentForBoth(other: Markerable): Boolean =
        placeName != null && address != null && other.placeName != null && other.address != null

    companion object {
        const val SAME_LOCATION_METERS_THRESHOLD = 20.0f
    }
}
