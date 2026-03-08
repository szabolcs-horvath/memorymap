package com.szabolcshorvath.memorymap.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

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

        val startDay = startDate.format(dateFormatter.withLocale(Locale.getDefault()))
        val endDay = endDate.format(dateFormatter.withLocale(Locale.getDefault()))

        return if (isAllDay) {
            if (startDay == endDay) {
                startDay
            } else {
                "$startDay - $endDay"
            }
        } else {
            val startTime = startDate.format(timeFormatter.withLocale(Locale.getDefault()))
            val endTime = endDate.format(timeFormatter.withLocale(Locale.getDefault()))

            if (startDay == endDay) {
                "$startDay $startTime - $endTime"
            } else {
                "$startDay $startTime - $endDay $endTime"
            }
        }
    }

    companion object {
        private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    }
}
