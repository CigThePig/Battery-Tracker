package com.cranebatterytracker.domain.analysis

/** Tunable thresholds for cycle classification. Kept as plain constructor
 * defaults so they are easy to override in tests or from settings later. */
data class RuntimeAnalysisConfig(
    val minReliableCyclesForOutlierDetection: Int = 5,
    val suspiciousHighAbsoluteThresholdMillis: Long = 2 * 60 * 60 * 1000L,
    val suspiciousHighModifiedZScoreThreshold: Double = 3.5,
    val minCyclesForShortRuntimeDetection: Int = 3,
    val shortRuntimeRatio: Double = 0.6
)
