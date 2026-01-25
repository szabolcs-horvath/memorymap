package com.szabolcshorvath.memorymap.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
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
        val startDay = startDate.format(dateFormatter)
        val endDay = endDate.format(dateFormatter)

        if (isAllDay) {
            return if (startDay == endDay) {
                startDay
            } else {
                "$startDay - $endDay"
            }
        } else {
            val startTime = startDate.format(timeFormatter)
            val endTime = endDate.format(timeFormatter)

            return if (startDay == endDay) {
                "$startDay $startTime - $endTime"
            } else {
                "$startDay $startTime - $endDay $endTime"
            }
        }
    }

    companion object {
        private val dateFormatter =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
        private val timeFormatter =
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
    }
}