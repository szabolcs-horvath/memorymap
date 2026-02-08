package com.szabolcshorvath.memorymap.data

import android.location.Location
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.szabolcshorvath.memorymap.data.MemoryGroup.Companion.SAME_LOCATION_METERS_THRESHOLD
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Entity(tableName = "memory_groups")
data class MemoryGroup(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String?,
    val latitude: Double,
    val longitude: Double,
    val placeName: String?,
    val address: String?,
    val startDate: ZonedDateTime,
    val endDate: ZonedDateTime,
    val isAllDay: Boolean,
    @ColumnInfo(defaultValue = "0.0")
    val markerHue: Float? = 0.0f
) {
    fun getFormattedDate(): String {
        // Use FormatStyle to respect locale settings
        val startDay = startDate.format(dateFormatter.withLocale(Locale.getDefault()))
        val endDay = endDate.format(dateFormatter.withLocale(Locale.getDefault()))

        if (isAllDay) {
            return if (startDay == endDay) {
                startDay
            } else {
                "$startDay - $endDay"
            }
        } else {
            val startTime = startDate.format(timeFormatter.withLocale(Locale.getDefault()))
            val endTime = endDate.format(timeFormatter.withLocale(Locale.getDefault()))

            return if (startDay == endDay) {
                "$startDay $startTime - $endTime"
            } else {
                "$startDay $startTime - $endDay $endTime"
            }
        }
    }

    /**
     * Determines if another MemoryGroup is at the same location.
     * Groups are considered same if their metadata (place name and address) match,
     * or if they are within a [SAME_LOCATION_METERS_THRESHOLD]-meter radius.
     */
    fun isSameLocationAs(other: MemoryGroup): Boolean {
        if (placeName != null && address != null && other.placeName != null && other.address != null) {
            if (placeName == other.placeName && address == other.address) return true
        }

        val loc1 = Location(null).apply {
            latitude = this@MemoryGroup.latitude
            longitude = this@MemoryGroup.longitude
        }
        val loc2 = Location(null).apply {
            latitude = other.latitude
            longitude = other.longitude
        }
        return loc1.distanceTo(loc2) < SAME_LOCATION_METERS_THRESHOLD
    }

    /**
     * A key used for grouping memories by location.
     * Uses [isSameLocationAs] for equality.
     */
    private data class LocationKey(private val group: MemoryGroup) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is LocationKey) return false
            return group.isSameLocationAs(other.group)
        }

        override fun hashCode(): Int = 0
    }

    companion object {
        const val SAME_LOCATION_METERS_THRESHOLD = 20.0f
        private val dateFormatter =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        private val timeFormatter =
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    }
}