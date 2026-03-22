package com.szabolcshorvath.memorymap.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.dateFormatter
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.timeFormatter
import java.time.ZonedDateTime

@Entity(
    tableName = "memory_groups",
    indices = [
        Index("startDate"),
        Index("endDate")
    ]
)
data class MemoryGroup(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    override val title: String,
    val description: String?,
    override val latitude: Double,
    override val longitude: Double,
    override val placeName: String?,
    override val address: String?,
    override val startDate: ZonedDateTime,
    override val endDate: ZonedDateTime,
    override val isAllDay: Boolean,
    @ColumnInfo(defaultValue = "0.0")
    override val markerHue: Float? = 0.0f,
    @ColumnInfo(defaultValue = "1.0")
    override val markerSaturation: Float? = 1.0f,
    @ColumnInfo(defaultValue = "1.0")
    override val markerValue: Float? = 1.0f
) : Markerable {
    override val groupId: Int get() = id

    override fun getFormattedDate(): String {
        val dateFormatter = dateFormatter()
        val timeFormatter = timeFormatter()

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
}
