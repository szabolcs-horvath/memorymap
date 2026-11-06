package com.szabolcshorvath.memorymap.util

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The map's date filter. "This week" starts on the day weeks start on for the phone ([firstDayOfWeek]). */
enum class DateFilterOption(
    val label: String,
    private val range: (now: LocalDate, weekStart: DayOfWeek) -> Pair<LocalDate?, LocalDate?>
) {
    LAST_7_DAYS("Last 7 Days", { now, _ -> Pair(now.minusDays(SEVEN_DAYS_MINUS_ONE), now) }),
    LAST_30_DAYS("Last 30 Days", { now, _ -> Pair(now.minusDays(THIRTY_DAYS_MINUS_ONE), now) }),
    LAST_365_DAYS("Last 365 Days", { now, _ -> Pair(now.minusDays(THREE_HUNDRED_SIXTY_FIVE_DAYS_MINUS_ONE), now) }),
    THIS_WEEK("This Week", { now, weekStart -> Pair(now.with(TemporalAdjusters.previousOrSame(weekStart)), now) }),
    THIS_MONTH("This Month", { now, _ -> Pair(now.withDayOfMonth(1), now) }),
    THIS_YEAR("This Year", { now, _ -> Pair(now.withDayOfYear(1), now) }),
    ALL_TIME("All Time", { _, _ -> Pair(null, null) });

    /** The filter's first and last day (null: no limit) when today is [now] and weeks start on [weekStart]. */
    fun dateRange(now: LocalDate, weekStart: DayOfWeek = firstDayOfWeek()): Pair<LocalDate?, LocalDate?> =
        range(now, weekStart)

    companion object {
        val DEFAULT_DATE_FILTER_OPTION = ALL_TIME

        private const val SEVEN_DAYS_MINUS_ONE = 6L
        private const val THIRTY_DAYS_MINUS_ONE = 29L
        private const val THREE_HUNDRED_SIXTY_FIVE_DAYS_MINUS_ONE = 364L

        fun allLabels(): List<String> {
            return entries.map { it.label }
        }

        fun ofLabel(label: String): DateFilterOption {
            val matching = entries.filter { it.label == label }
            if (matching.size == 1) {
                return matching.first()
            } else {
                if (matching.isEmpty()) {
                    throw IllegalArgumentException("No DefaultDateFilterOption found for label: $label")
                } else {
                    throw IllegalArgumentException("Multiple DefaultDateFilterOptions found for label: $label")
                }
            }
        }

        suspend fun getFromDataStore(dataStore: DataStore<Preferences>): DateFilterOption {
            val valueInDataStore = dataStore.data
                .map { it[PreferencesKeys.DEFAULT_DATE_FILTER] }
                .firstOrNull()
            return ofStoredName(valueInDataStore)
        }

        /** The option stored under its enum name, or the default if none is stored or the name is unknown. */
        fun ofStoredName(name: String?): DateFilterOption =
            entries.firstOrNull { it.name == name } ?: DEFAULT_DATE_FILTER_OPTION
    }
}
