package com.szabolcshorvath.memorymap.data

import androidx.room.TypeConverter
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class Converters {

    @TypeConverter
    fun toZonedDateTime(value: String?): ZonedDateTime? {
        return value?.let {
            ZonedDateTime.parse(it, formatter)
        }
    }

    @TypeConverter
    fun fromZonedDateTime(value: ZonedDateTime?): String? {
        return value?.format(formatter)
    }

    @TypeConverter
    fun fromMediaType(value: MediaType): String {
        return value.name
    }

    @TypeConverter
    fun toMediaType(value: String): MediaType {
        return try {
            MediaType.valueOf(value)
        } catch (_: Exception) {
            MediaType.IMAGE
        }
    }

    companion object {
        @JvmStatic
        private val formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME
    }
}