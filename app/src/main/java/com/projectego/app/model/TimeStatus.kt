package com.projectego.app.model

enum class DayType {
    NORMAL_DAY,
    ZERO_ENTERTAINMENT_DAY // Recovery day following a failed day
}

enum class TimeStatus {
    ACTIVE,
    CRITICAL,
    LOCKED,
    ZERO_ENTERTAINMENT_DAY
}
