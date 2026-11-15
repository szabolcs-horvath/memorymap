package com.szabolcshorvath.memorymap.util

import java.util.Locale

/**
 * A place's name where it is shown next to its address. A spot picked on the map without a name of its own is named
 * after its street and house number ("Dér u. 42"), which its address says again ("Budapest, Dér u. 42, 1104"): shown
 * together, the address alone is enough. Where the name is shown on its own (the map's card, the timeline) it stays.
 */
object PlaceName {
    private val NOT_A_WORD = Regex("[^\\p{L}\\p{N}]+")

    /** [name] to show above or before [address], or null when there is none or the address already says it. */
    fun besideAddress(name: String?, address: String?): String? {
        val shown = name?.takeIf { it.isNotBlank() } ?: return null
        return shown.takeUnless { address != null && repeats(address, shown) }
    }

    /**
     * Whether one comma-separated part of [address] has every word of [name], in any order: the street and number
     * name it written the phone's way ("Dér u. 42") or the other way round ("1600 Amphitheatre Pkwy").
     */
    internal fun repeats(address: String, name: String): Boolean {
        val nameWords = words(name)
        return nameWords.isNotEmpty() && address.split(',').any { part -> words(part).containsAll(nameWords) }
    }

    private fun words(text: String): Set<String> =
        text.lowercase(Locale.ROOT).split(NOT_A_WORD).filter { it.isNotEmpty() }.toSet()
}
