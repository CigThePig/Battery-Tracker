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
        val westCount: Int,
        val eastMedian: Long?,
        val eastCount: Int,
        val baseline: Long?,
        val recentChangePercent: Double?
    )

    private data class FleetRemoteMedians(val west: Double?, val east: Double?)

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
        return stats.map { subject -> finalize(subject, normalizedFleetRatio(subject, fleetRemoteMedians(subject, stats))) }
    }

    fun batteryHealth(battery: Battery, cyclesForBattery: List<DerivedCycle>, deadEventCount: Int): BatteryHealth =
        finalize(computeStats(battery, cyclesForBattery, deadEventCount), fleetRatio = null)

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

        val westCycles = reliableCycles.filter { it.remoteId == RemoteId.WEST }
        val eastCycles = reliableCycles.filter { it.remoteId == RemoteId.EAST }
        val westMedian = Statistics.median(westCycles.map { it.minimumActiveRuntimeMillis })?.toLong()
        val eastMedian = Statistics.median(eastCycles.map { it.minimumActiveRuntimeMillis })?.toLong()

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
            westCount = westCycles.size,
            eastMedian = eastMedian,
            eastCount = eastCycles.size,
            baseline = baseline,
            recentChangePercent = recentChangePercent
        )
    }

    /**
     * Peer fleet medians computed separately per remote (spec review Issue 6): comparing
     * a battery's overall lifetime median against the fleet's overall median confounds
     * "this battery is weak" with "this battery happens to run mostly in the
     * faster-draining remote." Only peers with enough of their own cycles in that remote
     * count as evidence for it, mirroring [remoteDiagnostics]'s own remote pairing.
     */
    private fun fleetRemoteMedians(subject: BatteryStats, allStats: List<BatteryStats>): FleetRemoteMedians {
        val peers = allStats.filter { it.battery.batteryId != subject.battery.batteryId }
        val westPeerMedians = peers.filter { it.westCount >= config.minPairedCyclesPerRemote }.mapNotNull { it.westMedian }
        val eastPeerMedians = peers.filter { it.eastCount >= config.minPairedCyclesPerRemote }.mapNotNull { it.eastMedian }
        return FleetRemoteMedians(
            west = if (westPeerMedians.size >= config.minPeerBatteriesForFleetComparison) Statistics.median(westPeerMedians) else null,
            east = if (eastPeerMedians.size >= config.minPeerBatteriesForFleetComparison) Statistics.median(eastPeerMedians) else null
        )
    }

    /**
     * Combines [subject]'s per-remote standing against its peers into one ratio, weighted
     * by how many of the subject's own cycles came from each remote. A battery used
     * mostly in one remote is judged mostly against that remote's peers rather than
     * against a flat fleet-wide figure dominated by the other remote's typical runtime.
     */
    private fun normalizedFleetRatio(subject: BatteryStats, fleet: FleetRemoteMedians): Double? {
        val westComponent = if (
            subject.westCount >= config.minPairedCyclesPerRemote && subject.westMedian != null &&
            fleet.west != null && fleet.west > 0
        ) {
            (subject.westMedian.toDouble() / fleet.west) to subject.westCount
        } else null

        val eastComponent = if (
            subject.eastCount >= config.minPairedCyclesPerRemote && subject.eastMedian != null &&
            fleet.east != null && fleet.east > 0
        ) {
            (subject.eastMedian.toDouble() / fleet.east) to subject.eastCount
        } else null

        val components = listOfNotNull(westComponent, eastComponent)
        if (components.isEmpty()) return null
        val totalWeight = components.sumOf { it.second }
        return components.sumOf { (ratio, weight) -> ratio * weight } / totalWeight
    }

    private fun finalize(stats: BatteryStats, fleetRatio: Double?): BatteryHealth {
        val (trend, assessment) = computeTrend(
            stats.reliableCycleCount,
            stats.recentChangePercent,
            stats.shortCountLast10,
            fleetRatio
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
        fleetRatio: Double?
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
        // [fleetRatio] is already normalized per remote (see normalizedFleetRatio) so a
        // battery isn't penalized merely for running mostly in a faster-draining remote.
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
        val clockAnomalyShiftInterruptedCycles = cycles.count {
            it.classification == RuntimeClassification.SHIFT_INTERRUPTED && it.clockAnomalyDetected
        }
        // A clock-tainted UNKNOWN cycle (RuntimeAnalysisEngine.confirmedMinimum downgrading
        // an anomaly-touched confirmation) has a known cause, unlike a genuine unknown gap -
        // it belongs with the other clock-integrity records, not folded silently into
        // "honest unknown gaps".
        val clockAnomalyUnknownCycles = cycles.count {
            it.classification == RuntimeClassification.UNKNOWN && it.clockAnomalyDetected
        }
        val unknownGaps = cycles.count { it.classification == RuntimeClassification.UNKNOWN && !it.clockAnomalyDetected }
        val confirmedMinimumObservations = cycles.count { it.classification == RuntimeClassification.CONFIRMED_MINIMUM }
        val suspiciousHighCount = cycles.count { it.isHighOutlier }

        // The overall level formula still treats a confirmed-minimum cycle - and a
        // clock-tainted cycle downgraded to UNKNOWN - as incomplete evidence, even though
        // neither is reported to the operator as an ordinary "unknown gap".
        val incompleteCycles = unknownGaps + clockAnomalyUnknownCycles + confirmedMinimumObservations
        val totalCoverage = (exactCycles + shiftInterruptedCycles + incompleteCycles).coerceAtLeast(1)
        val incompleteRatio = incompleteCycles.toDouble() / totalCoverage

        val overallLevel = when {
            exactCycles < config.limitedExactCycleThreshold -> DataQualityLevel.LIMITED
            exactCycles < config.developingExactCycleThreshold || incompleteRatio > config.highUnknownRatioThreshold -> DataQualityLevel.DEVELOPING
            exactCycles < config.goodExactCycleThreshold || incompleteRatio > config.moderateUnknownRatioThreshold -> DataQualityLevel.GOOD
            else -> DataQualityLevel.STRONG
        }

        return DataQualitySummary(
            exactCycles = exactCycles,
            shiftInterruptedCycles = shiftInterruptedCycles,
            clockAnomalyShiftInterruptedCycles = clockAnomalyShiftInterruptedCycles,
            clockAnomalyUnknownCycles = clockAnomalyUnknownCycles,
            unknownGaps = unknownGaps,
            confirmedMinimumObservations = confirmedMinimumObservations,
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
