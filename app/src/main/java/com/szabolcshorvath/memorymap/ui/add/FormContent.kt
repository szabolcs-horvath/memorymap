package com.szabolcshorvath.memorymap.ui.add

import android.net.Uri
import android.os.Bundle
import com.szabolcshorvath.memorymap.data.MediaType
import java.time.ZonedDateTime
import java.util.UUID

/** What a save of the Add form would store: to compare the form with how it was, and to keep it through process death. */
internal data class FormContent(
    val title: String?,
    val description: String?,
    val lat: Double?,
    val lng: Double?,
    val placeName: String?,
    val address: String?,
    val startDateTime: ZonedDateTime,
    val endDateTime: ZonedDateTime,
    val isAllDay: Boolean,
    val markerHue: Float,
    val markerSaturation: Float,
    val markerBrightness: Float,
    val media: List<SelectedMedia>,
    val fragments: List<FragmentEditState>
)

/** Writes the form to a Bundle for the saved state (read back with [toFormContent]). */
internal fun FormContent.toBundle() = Bundle().apply {
    putString(TITLE, title)
    putString(DESCRIPTION, description)
    lat?.let { putDouble(LAT, it) }
    lng?.let { putDouble(LNG, it) }
    putString(PLACE_NAME, placeName)
    putString(ADDRESS, address)
    putSerializable(START, startDateTime)
    putSerializable(END, endDateTime)
    putBoolean(ALL_DAY, isAllDay)
    putFloat(HUE, markerHue)
    putFloat(SATURATION, markerSaturation)
    putFloat(BRIGHTNESS, markerBrightness)
    putParcelableArrayList(MEDIA, media.mapTo(ArrayList()) { it.toBundle() })
    putParcelableArrayList(FRAGMENTS, fragments.mapTo(ArrayList()) { it.toBundle() })
}

/** Reads a form written by [toBundle]. */
internal fun Bundle.toFormContent() = FormContent(
    title = getString(TITLE),
    description = getString(DESCRIPTION),
    lat = if (containsKey(LAT)) getDouble(LAT) else null,
    lng = if (containsKey(LNG)) getDouble(LNG) else null,
    placeName = getString(PLACE_NAME),
    address = getString(ADDRESS),
    startDateTime = getSerializable(START, ZonedDateTime::class.java) ?: ZonedDateTime.now(),
    endDateTime = getSerializable(END, ZonedDateTime::class.java) ?: ZonedDateTime.now(),
    isAllDay = getBoolean(ALL_DAY),
    markerHue = getFloat(HUE),
    markerSaturation = getFloat(SATURATION),
    markerBrightness = getFloat(BRIGHTNESS),
    media = getParcelableArrayList(MEDIA, Bundle::class.java).orEmpty().mapNotNull { it.toSelectedMedia() },
    fragments = getParcelableArrayList(FRAGMENTS, Bundle::class.java).orEmpty().map { it.toFragmentEditState() }
)

private fun SelectedMedia.toBundle() = Bundle().apply {
    putParcelable(URI, uri)
    putString(TYPE, type.name)
    putString(DEVICE_ID, deviceId)
    putLong(FILE_SIZE, fileSize)
    putLong(DATE_TAKEN, dateTaken)
    putString(SIGNATURE, mediaSignature)
}

private fun Bundle.toSelectedMedia(): SelectedMedia? {
    val uri = getParcelable(URI, Uri::class.java)
    val type = MediaType.entries.firstOrNull { it.name == getString(TYPE) }
    if (uri == null || type == null) return null
    return SelectedMedia(uri, type, getString(DEVICE_ID).orEmpty(), getLong(FILE_SIZE), getLong(DATE_TAKEN), getString(SIGNATURE).orEmpty())
}

private fun FragmentEditState.toBundle() = Bundle().apply {
    putInt(ID, id)
    putString(LOCAL_ID, localId)
    lat?.let { putDouble(LAT, it) }
    lng?.let { putDouble(LNG, it) }
    putString(PLACE_NAME, placeName)
    putString(ADDRESS, address)
    putSerializable(START, startDate)
    putSerializable(END, endDate)
    putBoolean(ALL_DAY, isAllDay)
    putFloat(HUE, markerHue)
    putFloat(SATURATION, markerSaturation)
    putFloat(BRIGHTNESS, markerBrightness)
    putBoolean(TIME_VISIBLE, isTimeVisible)
    putBoolean(COLOR_EXPANDED, isColorExpanded)
    order?.let { putInt(ORDER, it) }
}

private fun Bundle.toFragmentEditState() = FragmentEditState(
    id = getInt(ID),
    localId = getString(LOCAL_ID) ?: UUID.randomUUID().toString(),
    lat = if (containsKey(LAT)) getDouble(LAT) else null,
    lng = if (containsKey(LNG)) getDouble(LNG) else null,
    placeName = getString(PLACE_NAME),
    address = getString(ADDRESS),
    startDate = getSerializable(START, ZonedDateTime::class.java),
    endDate = getSerializable(END, ZonedDateTime::class.java),
    isAllDay = getBoolean(ALL_DAY),
    markerHue = getFloat(HUE),
    markerSaturation = getFloat(SATURATION),
    markerBrightness = getFloat(BRIGHTNESS),
    isTimeVisible = getBoolean(TIME_VISIBLE),
    isColorExpanded = getBoolean(COLOR_EXPANDED),
    order = if (containsKey(ORDER)) getInt(ORDER) else null
)

private const val TITLE = "title"
private const val DESCRIPTION = "description"
private const val LAT = "lat"
private const val LNG = "lng"
private const val PLACE_NAME = "place_name"
private const val ADDRESS = "address"
private const val START = "start"
private const val END = "end"
private const val ALL_DAY = "all_day"
private const val HUE = "hue"
private const val SATURATION = "saturation"
private const val BRIGHTNESS = "brightness"
private const val MEDIA = "media"
private const val FRAGMENTS = "fragments"
private const val URI = "uri"
private const val TYPE = "type"
private const val DEVICE_ID = "device_id"
private const val FILE_SIZE = "file_size"
private const val DATE_TAKEN = "date_taken"
private const val SIGNATURE = "signature"
private const val ID = "id"
private const val LOCAL_ID = "local_id"
private const val TIME_VISIBLE = "time_visible"
private const val COLOR_EXPANDED = "color_expanded"
private const val ORDER = "order"
