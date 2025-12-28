package com.szabolcshorvath.memorymap.data

import androidx.room.TypeConverter
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class Converters {
    private val formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME

    @TypeConverter
    fun toZonedDateTime(value: String?): ZonedDateTime? {
        return value?.let {
            return formatter.parse(value, ZonedDateTime::from)
        }
    }

    @TypeConverter
    fun fromZonedDateTime(date: ZonedDateTime?): String? {
        return date?.format(formatter)
    }
}