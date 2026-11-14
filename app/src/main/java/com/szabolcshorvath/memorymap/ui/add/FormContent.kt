package com.szabolcshorvath.memorymap.ui.add

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import com.szabolcshorvath.memorymap.data.MediaItem
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.data.MemoryFragment
import com.szabolcshorvath.memorymap.data.MemoryGroup
import com.szabolcshorvath.memorymap.data.MemoryGroupWithMedia
import com.szabolcshorvath.memorymap.ui.color.Hsv
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_BRIGHTNESS
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_HUE
import com.szabolcshorvath.memorymap.util.ColorUtil.DEFAULT_MARKER_SATURATION
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * What a save of the Add form would store: the form's content, as the ViewModel keeps it, compares it with how it was,
 * and keeps it through process death. The defaults are an empty form: starting now, for an hour, in the default colour.
 */
internal data class FormContent(
    val title: String? = null,
    val description: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val placeName: String? = null,
    val address: String? = null,
    val startDateTime: ZonedDateTime = ZonedDateTime.now(),
    val endDateTime: ZonedDateTime = startDateTime.plusHours(1),
    val isAllDay: Boolean = false,
    val markerHue: Float = DEFAULT_MARKER_HUE,
    val markerSaturation: Float = DEFAULT_MARKER_SATURATION,
    val markerBrightness: Float = DEFAULT_MARKER_BRIGHTNESS,
    val media: List<SelectedMedia> = emptyList(),
    val fragments: List<FragmentEditState> = emptyList()
) {
    val place: FormPlace? get() = FormPlace.of(lat, lng, placeName, address)

    val color: Hsv get() = Hsv(markerHue, markerSaturation, markerBrightness)

    /** Whether a save has everything it needs: a title, and a place for the memory and each fragment. */
    val isComplete: Boolean get() = !title.isNullOrBlank() && place != null && fragments.all { it.lat != null && it.lng != null }

    fun withPlace(lat: Double, lng: Double, name: String?, address: String?) =
        copy(lat = lat, lng = lng, placeName = name, address = address)

    fun withColor(color: Hsv) = copy(markerHue = color.hue, markerSaturation = color.saturation, markerBrightness = color.brightness)

    /** The form with [change] applied to fragment [localId]. The list can change while a picker is open, so never by position. */
    fun withFragment(localId: String, change: (FragmentEditState) -> FragmentEditState) =
        copy(fragments = fragments.map { if (it.localId == localId) change(it) else it })

    /** Only what a save stores: whether a fragment's colour sliders are open is only how the form looks. */
    fun withoutLooks() = copy(fragments = fragments.map { it.copy(isColorExpanded = false) })

    /** The memory as it is stored, with id [id] (0 for a new one); an all-day memory spans its days from start to end. */
    fun toGroup(id: Int): MemoryGroup = MemoryGroup(
        id = id,
        title = title.orEmpty(),
        description = description,
        latitude = checkNotNull(lat),
        longitude = checkNotNull(lng),
        placeName = placeName,
        address = address,
        startDate = if (isAllDay) startOfDay(startDateTime) else startDateTime,
        endDate = if (isAllDay) endOfDay(endDateTime) else endDateTime,
        isAllDay = isAllDay,
        markerHue = markerHue,
        markerSaturation = markerSaturation,
        markerBrightness = markerBrightness
    )

    /** The fragments as they are stored (group id still 0), numbered in the form's order. A fragment without dates of its own saves none. */
    fun fragmentEntities(): List<MemoryFragment> = fragments.mapIndexed { index, f ->
        val allDay = f.isAllDay && f.isTimeVisible
        MemoryFragment(
            groupId = 0,
            latitude = checkNotNull(f.lat),
            longitude = checkNotNull(f.lng),
            placeName = f.placeName,
            address = f.address,
            startDate = if (!f.isTimeVisible) null else if (allDay) f.startDate?.let(::startOfDay) else f.startDate,
            endDate = if (!f.isTimeVisible) null else if (allDay) f.endDate?.let(::endOfDay) else f.endDate,
            isAllDay = allDay,
            markerHue = f.markerHue,
            markerSaturation = f.markerSaturation,
            markerBrightness = f.markerBrightness,
            order = index + 1
        )
    }

    companion object {
        /** The form for memory [data], opened for editing: its media and fragments in the order the memory shows them. */
        fun of(data: MemoryGroupWithMedia): FormContent {
            val group = data.group
            return FormContent(
                title = group.title,
                description = group.description,
                lat = group.latitude,
                lng = group.longitude,
                placeName = group.placeName,
                address = group.address,
                startDateTime = group.startDate,
                endDateTime = group.endDate,
                isAllDay = group.isAllDay,
                markerHue = group.markerHue ?: DEFAULT_MARKER_HUE,
                markerSaturation = group.markerSaturation ?: DEFAULT_MARKER_SATURATION,
                markerBrightness = group.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS,
                media = data.mediaItems.sortedWith(MediaItem.DISPLAY_ORDER).map {
                    SelectedMedia(it.uri.toUri(), it.type, it.deviceId, it.fileSize, it.dateTaken, it.mediaSignature)
                },
                fragments = data.fragments.sortedWith(MemoryFragment.DISPLAY_ORDER).map {
                    FragmentEditState(
                        id = it.id,
                        lat = it.latitude,
                        lng = it.longitude,
                        placeName = it.placeName,
                        address = it.address,
                        startDate = it.startDate,
                        endDate = it.endDate,
                        isAllDay = it.isAllDay,
                        markerHue = it.markerHue ?: DEFAULT_MARKER_HUE,
                        markerSaturation = it.markerSaturation ?: DEFAULT_MARKER_SATURATION,
                        markerBrightness = it.markerBrightness ?: DEFAULT_MARKER_BRIGHTNESS,
                        isTimeVisible = it.startDate != null,
                        order = it.order
                    )
                }
            )
        }

        private fun startOfDay(time: ZonedDateTime) = time.toLocalDate().atStartOfDay(ZoneId.systemDefault())

        private fun endOfDay(time: ZonedDateTime) = time.toLocalDate().atTime(LocalTime.MAX).atZone(ZoneId.systemDefault())
    }
}

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
