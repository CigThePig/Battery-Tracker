package com.cranebatterytracker.domain.model

data class BatteryRemoteComparison(
    val batteryId: Int,
    val displayNumber: Int,
    val westMedianMillis: Long?,
    val westReliableCycleCount: Int,
    val eastMedianMillis: Long?,
    val eastReliableCycleCount: Int
) {
    /** True only when both sides have enough samples and East is meaningfully shorter. */
    fun eastShorterThanWest(minimumDifferenceMillis: Long): Boolean {
        val west = westMedianMillis ?: return false
        val east = eastMedianMillis ?: return false
        return (west - east) >= minimumDifferenceMillis
    }

    fun westShorterThanEast(minimumDifferenceMillis: Long): Boolean {
        val west = westMedianMillis ?: return false
        val east = eastMedianMillis ?: return false
        return (east - west) >= minimumDifferenceMillis
    }
}

enum class RemoteWarningLevel {
    NONE,
    DEVELOPING,
    STRONG
}

data class RemoteDiagnosticSummary(
    val perBattery: List<BatteryRemoteComparison>,
    val warningLevel: RemoteWarningLevel,
    val message: String
)

enum class DataQualityLevel {
    LIMITED,
    DEVELOPING,
    GOOD,
    STRONG
}

data class DataQualitySummary(
    val exactCycles: Int,
    val shiftInterruptedCycles: Int,
    /**
     * Of [shiftInterruptedCycles], how many were downgraded by a wall-clock jump or lost
     * monotonic timeline rather than a genuine shift-boundary crossing. Their bounds are
     * computed from untrustworthy timestamps, so unlike an ordinary shift-interrupted
     * cycle they are not useful partial evidence and must not be added into a "useful"
     * total.
     */
    val clockAnomalyShiftInterruptedCycles: Int,
    /** Cycles with no end evidence at all - genuinely missing data, not incomplete-but-useful data. */
    val unknownGaps: Int,
    /**
     * Cycles with a confirmed lower bound ("still installed" at some later check) but no
     * completed end - useful evidence, not an unknown gap (spec review Issue 13). Grouping
     * these with [unknownGaps] made the data-quality screen paradoxically get more
     * pessimistic the more reliably operators used shift confirmations.
     */
    val confirmedMinimumObservations: Int,
    val corrections: Int,
    val suspiciousHighCount: Int,
    val overallLevel: DataQualityLevel
)
