package com.cranebatterytracker.domain.model

enum class RuntimeClassification {
    EXACT,
    SHIFT_INTERRUPTED,
    CONFIRMED_MINIMUM,
    SUSPICIOUS_HIGH,
    UNKNOWN
}

enum class ShiftActivityLevel {
    DEFINITELY_ACTIVE,
    AMBIGUOUS,
    LIKELY_INACTIVE
}
