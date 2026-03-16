package com.szabolcshorvath.memorymap.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.dateFormatter
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil.timeFormatter
import java.time.ZonedDateTime

@Entity(
    tableName = "memory_fragments",
    foreignKeys = [
        ForeignKey(
            entity = MemoryGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("groupId"),
        Index("startDate"),
        Index("endDate")
    ]
)
data class MemoryFragment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    override val groupId: Int,
    override val latitude: Double,
    override val longitude: Double,
    override val placeName: String?,
    override val address: String?,
    override val startDate: ZonedDateTime?,
    override val endDate: ZonedDateTime?,
    override val isAllDay: Boolean = false,
    @ColumnInfo(defaultValue = "0.0")
    override val markerHue: Float? = 0.0f,
    val order: Int? = null
) : Markerable {
    @Ignore
    override var title: String = ""

    override fun getFormattedDate(): String? {
        if (startDate == null || endDate == null) return null
        val dateFormatter = dateFormatter()
        val timeFormatter = timeFormatter()

        val startDay = startDate.format(dateFormatter)
        val endDay = endDate.format(dateFormatter)

        return if (isAllDay) {
            if (startDay == endDay) {
                startDay
            } else {
                "$startDay - $endDay"
            }
        } else {
            val startTime = startDate.format(timeFormatter)
            val endTime = endDate.format(timeFormatter)

            if (startDay == endDay) {
                "$startDay $startTime - $endTime"
            } else {
                "$startDay $startTime - $endDay $endTime"
            }
        }
    }
}
