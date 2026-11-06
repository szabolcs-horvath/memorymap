package com.szabolcshorvath.memorymap.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.szabolcshorvath.memorymap.util.DateTimeFormatterUtil
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
    @ColumnInfo(defaultValue = "1.0")
    override val markerSaturation: Float? = 1.0f,
    @ColumnInfo(defaultValue = "1.0")
    override val markerBrightness: Float? = 1.0f,
    val order: Int? = null
) : Markerable {
    /** A fragment has no title of its own: the map shows it under its memory's ([Markerable.FragmentMarker]). */
    override val title: String? get() = null

    override fun getFormattedDate(context: Context): String? {
        if (startDate == null || endDate == null) return null

        val startDay = DateTimeFormatterUtil.date(startDate)
        val endDay = DateTimeFormatterUtil.date(endDate)

        return if (isAllDay) {
            if (startDay == endDay) {
                startDay
            } else {
                "$startDay - $endDay"
            }
        } else {
            val startTime = DateTimeFormatterUtil.time(context, startDate)
            val endTime = DateTimeFormatterUtil.time(context, endDate)

            if (startDay == endDay) {
                "$startDay $startTime - $endTime"
            } else {
                "$startDay $startTime - $endDay $endTime"
            }
        }
    }
}
