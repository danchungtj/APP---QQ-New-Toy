package com.example.qqnewtoy.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "toy_days")
data class ToyDay(
    @PrimaryKey
    val date: Date,
    val state: DayState
) 