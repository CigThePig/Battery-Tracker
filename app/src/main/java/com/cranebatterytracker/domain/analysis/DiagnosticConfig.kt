package com.cranebatterytracker.domain.analysis

data class DiagnosticConfig(
    val baselineGroupSize: Int = 5,
    val minCyclesForBaseline: Int = 5,
    val recentGroupSizeSmall: Int = 5,
    val recentGroupSizeLarge: Int = 10,
    val minCyclesForTrend: Int = 5,
    val watchChangePercent: Double = -15.0,
    val decliningChangePercent: Double = -30.0,
    val strongChangePercent: Double = -45.0,
    val watchShortCountLast10: Int = 2,
    val strongShortCountLast10: Int = 5,
    val minPairedCyclesPerRemote: Int = 3,
    val minDifferenceMillisForDirectionalMatch: Long = 10 * 60 * 1000L,
    val developingWarningBatteryCount: Int = 2,
    val strongWarningBatteryCount: Int = 3,
    val limitedExactCycleThreshold: Int = 10,
    val developingExactCycleThreshold: Int = 30,
    val goodExactCycleThreshold: Int = 60,
    val highUnknownRatioThreshold: Double = 0.5,
    val moderateUnknownRatioThreshold: Double = 0.3
)
