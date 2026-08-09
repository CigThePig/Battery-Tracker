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
    val unknownGaps: Int,
    val corrections: Int,
    val suspiciousHighCount: Int,
    val overallLevel: DataQualityLevel
)
