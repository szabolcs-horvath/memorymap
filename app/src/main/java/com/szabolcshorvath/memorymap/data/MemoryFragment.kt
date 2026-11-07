package com.szabolcshorvath.memorymap.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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

    companion object {
        /**
         * The order a memory shows its fragments in: by `order`, and fragments without one after them, the earliest
         * first (fragments without dates last).
         */
        val DISPLAY_ORDER: Comparator<MemoryFragment> = Comparator { a, b ->
            when {
                a.order != null && b.order != null -> a.order.compareTo(b.order)
                a.order != null -> -1
                b.order != null -> 1
                a.startDate != null && b.startDate != null -> a.startDate.compareTo(b.startDate)
                a.startDate != null -> -1
                b.startDate != null -> 1
                else -> 0
            }
        }
    }
}
