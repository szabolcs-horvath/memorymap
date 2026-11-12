package com.szabolcshorvath.memorymap.ui.add

import java.time.ZonedDateTime
import java.util.UUID

/**
 * A memory fragment as the Add form edits it. [localId] tells fragments apart while the form is open (a new one has no
 * [id] yet); [isTimeVisible] is whether it has dates of its own, and [isColorExpanded] whether its colour sliders show.
 */
data class FragmentEditState(
    val id: Int = 0,
    val localId: String = UUID.randomUUID().toString(),
    val lat: Double? = null,
    val lng: Double? = null,
    val placeName: String? = null,
    val address: String? = null,
    val startDate: ZonedDateTime? = null,
    val endDate: ZonedDateTime? = null,
    val isAllDay: Boolean = false,
    val markerHue: Float = 0.0f,
    val markerSaturation: Float = 1.0f,
    val markerBrightness: Float = 1.0f,
    val isTimeVisible: Boolean = false,
    val isColorExpanded: Boolean = false,
    val order: Int? = null
)
