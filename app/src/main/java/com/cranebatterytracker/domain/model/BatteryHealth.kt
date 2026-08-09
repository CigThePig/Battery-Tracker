package com.cranebatterytracker.domain.model

enum class BatteryTrend {
    NOT_ENOUGH_DATA,
    STABLE,
    WATCH,
    DECLINING,
    STRONG_REPLACEMENT_CANDIDATE
}

data class BatteryHealth(
    val batteryId: Int,
    val displayNumber: Int,
    val lifetimeReliableMedianMillis: Long?,
    val recentReliableMedianMillis: Long?,
    val reliableCycleCount: Int,
    val deadEventCount: Int,
    val shortRuntimeEventCount: Int,
    val suspiciousHighCount: Int,
    val westMedianMillis: Long?,
    val eastMedianMillis: Long?,
    val historicalBaselineMillis: Long?,
    val trend: BatteryTrend,
    val recentChangePercent: Double?,
    val assessment: String
)
