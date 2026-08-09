package com.cranebatterytracker.domain.model

enum class EventType {
    BATTERY_INSTALLED,
    BATTERY_REMOVED_DEAD,
    STATE_CONFIRMED,
    STATE_CORRECTED,
    STATE_MARKED_UNKNOWN,
    UNDO_ACTION,
    SYSTEM_TIME_WARNING
}
