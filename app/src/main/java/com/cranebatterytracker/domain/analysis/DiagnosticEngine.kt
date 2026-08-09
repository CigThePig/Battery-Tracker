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

    private data class BatteryStats(
        val battery: Battery,
        val lifetimeMedian: Long?,
        val recentMedian: Long?,
        val reliableCycleCount: Int,
        val deadEventCount: Int,
        val shortCount: Int,
        val suspiciousCount: Int,
        val shortCountLast10: Int,
        val westMedian: Long?,
        val eastMedian: Long?,
        val baseline: Long?,
        val recentChangePercent: Double?
    )

    /**
     * Health for every battery at once, so each one's trend can also be judged against
     * how its peers are performing - a battery that has always been weak relative to the
     * fleet shows no decline of its own to detect (spec section 49). Prefer this over
     * calling [batteryHealth] once per battery.
     */
    fun batteryHealths(
        batteries: List<Battery>,
        cyclesByBattery: Map<Int, List<DerivedCycle>>,
        deadEventCounts: Map<Int, Int>
    ): List<BatteryHealth> {
        val stats = batteries.map { battery ->
            computeStats(battery, cyclesByBattery[battery.batteryId] ?: emptyList(), deadEventCounts[battery.batteryId] ?: 0)
        }
        return stats.map { finalize(it, fleetMedianMillis(it, stats)) }
    }

    fun batteryHealth(battery: Battery, cyclesForBattery: List<DerivedCycle>, deadEventCount: Int): BatteryHealth =
        finalize(computeStats(battery, cyclesForBattery, deadEventCount), fleetMedianMillis = null)

    private fun computeStats(battery: Battery, cyclesForBattery: List<DerivedCycle>, deadEventCount: Int): BatteryStats {
        val exactCycles = cyclesForBattery
            .filter { it.classification == RuntimeClassification.EXACT }
            .sortedBy { it.startSequenceNumber }
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

        return BatteryStats(
            battery = battery,
            lifetimeMedian = lifetimeMedian,
            recentMedian = recentMedian,
            reliableCycleCount = reliableCycles.size,
            deadEventCount = deadEventCount,
            shortCount = shortCount,
            suspiciousCount = suspiciousCount,
            shortCountLast10 = shortCountLast10,
            westMedian = westMedian,
            eastMedian = eastMedian,
            baseline = baseline,
            recentChangePercent = recentChangePercent
        )
    }

    /** Median lifetime runtime of [subject]'s peers - null unless enough of them have their own reliable baseline. */
    private fun fleetMedianMillis(subject: BatteryStats, allStats: List<BatteryStats>): Double? {
        val peerMedians = allStats
            .filter { it.battery.batteryId != subject.battery.batteryId && it.reliableCycleCount >= config.minCyclesForTrend }
            .mapNotNull { it.lifetimeMedian }
        return if (peerMedians.size >= config.minPeerBatteriesForFleetComparison) Statistics.median(peerMedians) else null
    }

    private fun finalize(stats: BatteryStats, fleetMedianMillis: Double?): BatteryHealth {
        val (trend, assessment) = computeTrend(
            stats.reliableCycleCount,
            stats.recentChangePercent,
            stats.shortCountLast10,
            stats.lifetimeMedian,
            fleetMedianMillis
        )
        return BatteryHealth(
            batteryId = stats.battery.batteryId,
            displayNumber = stats.battery.displayNumber,
            lifetimeReliableMedianMillis = stats.lifetimeMedian,
            recentReliableMedianMillis = stats.recentMedian,
            reliableCycleCount = stats.reliableCycleCount,
            deadEventCount = stats.deadEventCount,
            shortRuntimeEventCount = stats.shortCount,
            suspiciousHighCount = stats.suspiciousCount,
            westMedianMillis = stats.westMedian,
            eastMedianMillis = stats.eastMedian,
            historicalBaselineMillis = stats.baseline,
            trend = trend,
            recentChangePercent = stats.recentChangePercent,
            assessment = assessment
        )
    }

    private fun computeTrend(
        reliableCycleCount: Int,
        recentChangePercent: Double?,
        shortCountLast10: Int,
        lifetimeMedianMillis: Long?,
        fleetMedianMillis: Double?
    ): Pair<BatteryTrend, String> {
        if (reliableCycleCount < config.minCyclesForTrend) {
            return BatteryTrend.NOT_ENOUGH_DATA to
                "Not enough reliable cycles yet to draw a conclusion about this battery."
        }
        val change = recentChangePercent
        val severeDecline = change != null && change <= config.strongChangePercent
        val frequentShortCycles = shortCountLast10 >= config.strongShortCountLast10

        // A battery that has always underperformed its peers has nothing of its own to
        // "decline" from, so recentChangePercent alone can never catch it - only a
        // comparison against the other batteries' typical runtime can (spec section 49).
        val fleetRatio = if (lifetimeMedianMillis != null && fleetMedianMillis != null && fleetMedianMillis > 0) {
            lifetimeMedianMillis / fleetMedianMillis
        } else {
            null
        }
        val severelyBelowFleet = fleetRatio != null && fleetRatio <= config.fleetStrongUnderperformanceRatio
        val belowFleet = fleetRatio != null && fleetRatio <= config.fleetWatchUnderperformanceRatio

        return when {
            severeDecline || frequentShortCycles || severelyBelowFleet -> {
                // State only the evidence that actually triggered this - claiming
                // evidence that never applied would put a "short cycles are frequent"
                // claim next to a count of zero, for example.
                val reasons = buildList {
                    if (severeDecline) add("Recent runtime is far below this battery's historical baseline.")
                    if (frequentShortCycles) add("Short cycles are frequent in this battery's recent history.")
                    if (severelyBelowFleet) add("This battery consistently runs far shorter than the other batteries.")
                }
                BatteryTrend.STRONG_REPLACEMENT_CANDIDATE to "${reasons.joinToString(" ")} Strong replacement candidate."
            }

            change != null && change <= config.decliningChangePercent ->
                BatteryTrend.DECLINING to
                    "Recent runtime is well below this battery's historical baseline. Declining."

            change != null && change <= config.watchChangePercent || shortCountLast10 >= config.watchShortCountLast10 -> {
                BatteryTrend.WATCH to
                    "Recent runtime is somewhat below baseline, or short cycles are starting to appear. Worth watching."
            }

            belowFleet ->
                BatteryTrend.WATCH to
                    "This battery consistently runs shorter than the other batteries. Worth watching."

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
