package com.cranebatterytracker.domain.analysis

import com.cranebatterytracker.domain.model.Battery
import com.cranebatterytracker.domain.model.BatteryHealth
import com.cranebatterytracker.domain.model.BatteryRemoteComparison
import com.cranebatterytracker.domain.model.BatteryTrend
import com.cranebatterytracker.domain.model.DataQualityLevel
import com.cranebatterytracker.domain.model.DataQualitySummary
import com.cranebatterytracker.domain.model.DerivedCycle
import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteDiagnosticSummary
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RemoteWarningLevel
import com.cranebatterytracker.domain.model.RuntimeClassification

/**
 * Converts derived cycles into the honest, hedged conclusions the UI shows:
 * battery health, West-vs-East comparison, and overall data quality. Never
 * turns weak evidence into a strong claim (spec section 5.10 / 49).
 */
class DiagnosticEngine(private val config: DiagnosticConfig = DiagnosticConfig()) {

    fun batteryHealth(battery: Battery, cyclesForBattery: List<DerivedCycle>, deadEventCount: Int): BatteryHealth {
        val exactCycles = cyclesForBattery
            .filter { it.classification == RuntimeClassification.EXACT }
            .sortedBy { it.startTimestamp }
        val reliableCycles = exactCycles.filterNot { it.isHighOutlier }

        val lifetimeMedian = Statistics.median(reliableCycles.map { it.minimumActiveRuntimeMillis })?.toLong()

        val recentGroup = when {
            reliableCycles.size >= config.recentGroupSizeLarge -> reliableCycles.takeLast(config.recentGroupSizeLarge)
            else -> reliableCycles.takeLast(config.recentGroupSizeSmall)
        }
        val recentMedian = Statistics.median(recentGroup.map { it.minimumActiveRuntimeMillis })?.toLong()

        val baseline = if (reliableCycles.size >= config.minCyclesForBaseline) {
            Statistics.median(reliableCycles.take(config.baselineGroupSize).map { it.minimumActiveRuntimeMillis })?.toLong()
        } else {
            null
        }

        val westMedian = Statistics.median(
            reliableCycles.filter { it.remoteId == RemoteId.WEST }.map { it.minimumActiveRuntimeMillis }
        )?.toLong()
        val eastMedian = Statistics.median(
            reliableCycles.filter { it.remoteId == RemoteId.EAST }.map { it.minimumActiveRuntimeMillis }
        )?.toLong()

        val shortCount = exactCycles.count { it.isShortRuntimeEvent }
        val suspiciousCount = exactCycles.count { it.isHighOutlier }
        val shortCountLast10 = exactCycles.takeLast(10).count { it.isShortRuntimeEvent }

        val recentChangePercent = if (baseline != null && baseline > 0 && recentMedian != null) {
            ((recentMedian - baseline).toDouble() / baseline) * 100.0
        } else {
            null
        }

        val (trend, assessment) = computeTrend(reliableCycles.size, recentChangePercent, shortCountLast10)

        return BatteryHealth(
            batteryId = battery.batteryId,
            displayNumber = battery.displayNumber,
            lifetimeReliableMedianMillis = lifetimeMedian,
            recentReliableMedianMillis = recentMedian,
            reliableCycleCount = reliableCycles.size,
            deadEventCount = deadEventCount,
            shortRuntimeEventCount = shortCount,
            suspiciousHighCount = suspiciousCount,
            westMedianMillis = westMedian,
            eastMedianMillis = eastMedian,
            historicalBaselineMillis = baseline,
            trend = trend,
            recentChangePercent = recentChangePercent,
            assessment = assessment
        )
    }

    private fun computeTrend(
        reliableCycleCount: Int,
        recentChangePercent: Double?,
        shortCountLast10: Int
    ): Pair<BatteryTrend, String> {
        if (reliableCycleCount < config.minCyclesForTrend) {
            return BatteryTrend.NOT_ENOUGH_DATA to
                "Not enough reliable cycles yet to draw a conclusion about this battery."
        }
        val change = recentChangePercent
        val severeDecline = change != null && change <= config.strongChangePercent
        val frequentShortCycles = shortCountLast10 >= config.strongShortCountLast10

        return when {
            severeDecline || frequentShortCycles -> {
                // State only the evidence that actually triggered this - claiming both a
                // severe decline and frequent short cycles when only one is true would
                // put a "short cycles are frequent" claim next to a count of zero.
                val reason = when {
                    severeDecline && frequentShortCycles ->
                        "Recent runtime is far below this battery's historical baseline and short cycles are frequent."
                    severeDecline ->
                        "Recent runtime is far below this battery's historical baseline."
                    else ->
                        "Short cycles are frequent in this battery's recent history."
                }
                BatteryTrend.STRONG_REPLACEMENT_CANDIDATE to "$reason Strong replacement candidate."
            }

            change != null && change <= config.decliningChangePercent ->
                BatteryTrend.DECLINING to
                    "Recent runtime is well below this battery's historical baseline. Declining."

            (change != null && change <= config.watchChangePercent) || shortCountLast10 >= config.watchShortCountLast10 ->
                BatteryTrend.WATCH to
                    "Recent runtime is somewhat below baseline, or short cycles are starting to appear. Worth watching."

            else ->
                BatteryTrend.STABLE to
                    "Recent runtime is consistent with this battery's historical baseline. Stable."
        }
    }

    fun remoteDiagnostics(batteries: List<Battery>, cyclesByBattery: Map<Int, List<DerivedCycle>>): RemoteDiagnosticSummary {
        val comparisons = batteries.map { battery ->
            val cycles = (cyclesByBattery[battery.batteryId] ?: emptyList())
                .filter { it.classification == RuntimeClassification.EXACT && !it.isHighOutlier }
            val west = cycles.filter { it.remoteId == RemoteId.WEST }
            val east = cycles.filter { it.remoteId == RemoteId.EAST }
            BatteryRemoteComparison(
                batteryId = battery.batteryId,
                displayNumber = battery.displayNumber,
                westMedianMillis = if (west.size >= config.minPairedCyclesPerRemote) {
                    Statistics.median(west.map { it.minimumActiveRuntimeMillis })?.toLong()
                } else null,
                westReliableCycleCount = west.size,
                eastMedianMillis = if (east.size >= config.minPairedCyclesPerRemote) {
                    Statistics.median(east.map { it.minimumActiveRuntimeMillis })?.toLong()
                } else null,
                eastReliableCycleCount = east.size
            )
        }

        val eastWorseCount = comparisons.count { it.eastShorterThanWest(config.minDifferenceMillisForDirectionalMatch) }
        val westWorseCount = comparisons.count { it.westShorterThanEast(config.minDifferenceMillisForDirectionalMatch) }

        // A direction only counts as evidence of a remote-specific effect if it isn't
        // matched by equally strong evidence pointing the other way - four batteries
        // could split two-and-two, which is a battery-specific story, not a remote one.
        val (level, message) = when {
            eastWorseCount > westWorseCount && eastWorseCount >= config.strongWarningBatteryCount ->
                RemoteWarningLevel.STRONG to
                    "Batteries are consistently lasting less time in the East / Back remote. This pattern appears across multiple batteries. Inspect the East remote or related hardware."

            westWorseCount > eastWorseCount && westWorseCount >= config.strongWarningBatteryCount ->
                RemoteWarningLevel.STRONG to
                    "Batteries are consistently lasting less time in the West / Front remote. This pattern appears across multiple batteries. Inspect the West remote or related hardware."

            eastWorseCount > westWorseCount && eastWorseCount >= config.developingWarningBatteryCount ->
                RemoteWarningLevel.DEVELOPING to
                    "East currently shows shorter runtime, but more measurements are needed."

            westWorseCount > eastWorseCount && westWorseCount >= config.developingWarningBatteryCount ->
                RemoteWarningLevel.DEVELOPING to
                    "West currently shows shorter runtime, but more measurements are needed."

            eastWorseCount == westWorseCount && eastWorseCount >= config.developingWarningBatteryCount ->
                RemoteWarningLevel.NONE to
                    "Evidence points in different directions for different batteries. This looks more like a battery-specific issue than a remote-specific one."

            else -> RemoteWarningLevel.NONE to "Not enough data to compare remotes."
        }

        return RemoteDiagnosticSummary(perBattery = comparisons, warningLevel = level, message = message)
    }

    fun dataQuality(cycles: List<DerivedCycle>, correctionCount: Int): DataQualitySummary {
        val exactCycles = cycles.count { it.classification == RuntimeClassification.EXACT }
        val shiftInterruptedCycles = cycles.count { it.classification == RuntimeClassification.SHIFT_INTERRUPTED }
        val unknownGaps = cycles.count {
            it.classification == RuntimeClassification.UNKNOWN || it.classification == RuntimeClassification.CONFIRMED_MINIMUM
        }
        val suspiciousHighCount = cycles.count { it.isHighOutlier }

        val totalCoverage = (exactCycles + shiftInterruptedCycles + unknownGaps).coerceAtLeast(1)
        val unknownRatio = unknownGaps.toDouble() / totalCoverage

        val overallLevel = when {
            exactCycles < config.limitedExactCycleThreshold -> DataQualityLevel.LIMITED
            exactCycles < config.developingExactCycleThreshold || unknownRatio > config.highUnknownRatioThreshold -> DataQualityLevel.DEVELOPING
            exactCycles < config.goodExactCycleThreshold || unknownRatio > config.moderateUnknownRatioThreshold -> DataQualityLevel.GOOD
            else -> DataQualityLevel.STRONG
        }

        return DataQualitySummary(
            exactCycles = exactCycles,
            shiftInterruptedCycles = shiftInterruptedCycles,
            unknownGaps = unknownGaps,
            corrections = correctionCount,
            suspiciousHighCount = suspiciousHighCount,
            overallLevel = overallLevel
        )
    }

    fun deadEventCountsByBattery(effectiveEvents: List<DomainEvent>): Map<Int, Int> =
        effectiveEvents
            .filter { it.eventType == EventType.BATTERY_REMOVED_DEAD }
            .mapNotNull { it.batteryId }
            .groupingBy { it }
            .eachCount()

    fun correctionCount(effectiveEvents: List<DomainEvent>): Int =
        effectiveEvents.count { it.eventType == EventType.STATE_CORRECTED }
}
