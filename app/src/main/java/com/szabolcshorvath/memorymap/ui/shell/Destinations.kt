package com.szabolcshorvath.memorymap.ui.shell

import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import com.szabolcshorvath.memorymap.R
import com.szabolcshorvath.memorymap.fragment.FormPlace

/**
 * A tab of the app. [traceName] names its Firebase Performance trace (`main_activity_navigate_to_tab_<traceName>`),
 * kept from the tab fragments so the trace history continues.
 */
enum class Tab(@StringRes val label: Int, @DrawableRes val icon: Int, @DrawableRes val selectedIcon: Int, val traceName: String) {
    MAP(R.string.tab_map, R.drawable.ic_map, R.drawable.ic_map_filled, "MapFragment"),
    TIMELINE(R.string.tab_timeline, R.drawable.ic_view_timeline, R.drawable.ic_view_timeline_filled, "TimelineFragment"),
    ADD(R.string.tab_add, R.drawable.ic_add_circle, R.drawable.ic_add_circle_filled, "AddMemoryGroupFragment"),
    SETTINGS(R.string.tab_settings, R.drawable.ic_settings, R.drawable.ic_settings_filled, "SettingsFragment"),
}

/**
 * A screen shown over the tabs, in the back stack of the shell's `NavDisplay`. The stack always starts with [None],
 * which shows nothing, so the tabs show through: Back from the first screen over them returns to it.
 */
sealed interface Detail {
    /** Nothing over the tabs. */
    data object None : Detail

    /** The memory pager, opened on memory [memoryId]. */
    data class Memory(val memoryId: Int) : Detail

    /** Memory [memoryId]'s photos and videos, full screen, opened on item [itemId]. */
    data class MediaViewer(val memoryId: Int, val itemId: Int) : Detail

    /** The location picker, opened from the Add form on [place] (null: nothing picked yet). */
    data class PickLocation(val place: FormPlace?) : Detail

    /** The sheet for photo or video [itemId], which needs re-linking: a bottom sheet over the memory. */
    data class MissingMedia(val itemId: Int) : Detail
}

private const val KIND = "kind"
private const val MEMORY_ID = "memory_id"
private const val ITEM_ID = "item_id"
private const val LAT = "lat"
private const val LNG = "lng"
private const val NAME = "name"
private const val ADDRESS = "address"

/** [this] for the saved state; read back by [toDetail]. */
internal fun Detail.toBundle(): Bundle = when (this) {
    Detail.None -> bundleOf(KIND to "none")
    is Detail.Memory -> bundleOf(KIND to "memory", MEMORY_ID to memoryId)
    is Detail.MediaViewer -> bundleOf(KIND to "media_viewer", MEMORY_ID to memoryId, ITEM_ID to itemId)
    is Detail.PickLocation -> bundleOf(KIND to "pick_location").apply {
        place?.let {
            putDouble(LAT, it.lat)
            putDouble(LNG, it.lng)
            putString(NAME, it.name)
            putString(ADDRESS, it.address)
        }
    }
    is Detail.MissingMedia -> bundleOf(KIND to "missing_media", ITEM_ID to itemId)
}

/** The screen [this] saved by [toBundle], or null for one this version doesn't know. */
internal fun Bundle.toDetail(): Detail? = when (getString(KIND)) {
    "none" -> Detail.None
    "memory" -> Detail.Memory(getInt(MEMORY_ID))
    "media_viewer" -> Detail.MediaViewer(getInt(MEMORY_ID), getInt(ITEM_ID))
    "pick_location" -> Detail.PickLocation(
        if (containsKey(LAT)) FormPlace(getDouble(LAT), getDouble(LNG), getString(NAME), getString(ADDRESS)) else null
    )
    "missing_media" -> Detail.MissingMedia(getInt(ITEM_ID))
    else -> null
}
