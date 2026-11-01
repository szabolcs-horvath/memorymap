package com.szabolcshorvath.memorymap.fragment

import android.net.Uri
import com.szabolcshorvath.memorymap.adapter.MemoryFragmentEditAdapter.FragmentEditState
import com.szabolcshorvath.memorymap.data.MediaType
import com.szabolcshorvath.memorymap.fragment.AddMemoryGroupFragment.SelectedMedia
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.time.ZoneId
import java.time.ZonedDateTime

/** The Add form's draft is kept in the saved state as a Bundle, so it survives process death. */
@ExtendWith(RobolectricExtension::class)
class FormContentTest {

    @Test
    fun `a full draft comes back the same`() {
        val start = ZonedDateTime.of(2026, 10, 20, 14, 30, 0, 0, ZoneId.of("Europe/Budapest"))
        val form = FormContent(
            title = "Vienna trip",
            description = "With friends",
            lat = 48.2082,
            lng = 16.3738,
            placeName = "Stephansplatz",
            address = "1010 Wien",
            startDateTime = start,
            endDateTime = start.plusHours(3),
            isAllDay = false,
            markerHue = 120f,
            markerSaturation = 0.5f,
            markerBrightness = 0.8f,
            media = listOf(
                SelectedMedia(Uri.parse("content://media/picker/0/1"), MediaType.IMAGE, "device", 1234, 5678, "1234_abc"),
                SelectedMedia(Uri.parse("content://media/picker/0/2"), MediaType.VIDEO, "other device")
            ),
            fragments = listOf(
                FragmentEditState(
                    id = 7,
                    lat = 48.1,
                    lng = 16.2,
                    placeName = "Prater",
                    startDate = start,
                    endDate = start.plusHours(1),
                    isTimeVisible = true,
                    isDateExpanded = true,
                    order = 1
                ),
                FragmentEditState(lat = 47.5, lng = 19.0, isAllDay = true, markerHue = 30f)
            )
        )

        assertEquals(form, form.toBundle().toFormContent())
    }

    @Test
    fun `an empty draft comes back empty, without a location`() {
        val now = ZonedDateTime.now()
        val form = FormContent(null, null, null, null, null, null, now, now, true, 0f, 1f, 1f, emptyList(), emptyList())

        assertEquals(form, form.toBundle().toFormContent())
    }
}
