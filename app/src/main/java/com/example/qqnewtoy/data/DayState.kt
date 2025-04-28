package com.example.qqnewtoy.data

import java.util.Date

enum class DayState {
    EMPTY,
    CHECK,
    EXCLAMATION,
    X // Black X state
}

data class DayItem(
    val number: String,
    var state: DayState,
    val date: Date?
) 