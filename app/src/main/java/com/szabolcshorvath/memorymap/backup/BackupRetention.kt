package com.szabolcshorvath.memorymap.backup

import androidx.annotation.StringRes
import com.szabolcshorvath.memorymap.R

private const val QUARTER_MONTHS = 3L
private const val YEAR_MONTHS = 12L

/**
 * How long automatic backups are kept before they are moved to Drive's trash ([BackupTrimming]): never trimmed, or
 * kept for [months]. Stored by its enum name: never rename the entries.
 */
enum class BackupRetention(@param:StringRes val label: Int, val months: Long?) {
    NEVER(R.string.backup_retention_never, null),
    ONE_MONTH(R.string.backup_retention_one_month, 1),
    THREE_MONTHS(R.string.backup_retention_three_months, QUARTER_MONTHS),
    ONE_YEAR(R.string.backup_retention_one_year, YEAR_MONTHS);

    companion object {
        /** Nothing is trimmed unless the user chose to: a backup in Drive's trash is gone for good once Drive empties it. */
        val DEFAULT = NEVER

        /** The retention stored under its enum name, or the default if none is stored or the name is unknown. */
        fun ofStoredName(name: String?): BackupRetention = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
