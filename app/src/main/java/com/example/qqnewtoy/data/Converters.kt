package com.example.qqnewtoy.data

import androidx.room.TypeConverter
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromDayState(value: DayState): String {
        return value.name
    }

    @TypeConverter
    fun toDayState(value: String): DayState {
        return DayState.valueOf(value)
    }
} 