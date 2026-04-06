package com.szabolcshorvath.memorymap.util

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.szabolcshorvath.memorymap.MainActivity
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class DateFilterOption(val label: String, val dateRangeProvider: (now: LocalDate) -> Pair<LocalDate?, LocalDate?>) {
    LAST_7_DAYS("Last 7 Days", { now -> Pair(now.minusDays(6), now) }),
    LAST_30_DAYS("Last 30 Days", { now -> Pair(now.minusDays(29), now) }),
    LAST_365_DAYS("Last 365 Days", { now -> Pair(now.minusDays(364), now) }),
    THIS_WEEK("This Week", { now -> Pair(now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), now) }),
    THIS_MONTH("This Month", { now -> Pair(now.withDayOfMonth(1), now) }),
    THIS_YEAR("This Year", { now -> Pair(now.withDayOfYear(1), now) }),
    ALL_TIME("All Time", { _ -> Pair(null, null) });

    companion object {
        val DEFAULT_DATE_FILTER_OPTION = ALL_TIME

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
                .map { it[MainActivity.DEFAULT_DATE_FILTER] }
                .firstOrNull()
            return if (valueInDataStore != null) DateFilterOption.valueOf(valueInDataStore) else DEFAULT_DATE_FILTER_OPTION
        }
    }
}
