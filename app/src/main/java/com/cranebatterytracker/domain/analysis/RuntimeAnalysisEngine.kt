package com.cranebatterytracker.domain.analysis

import com.cranebatterytracker.domain.model.DerivedCycle
import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RuntimeClassification

/**
 * Turns the raw event log into [DerivedCycle] records (spec sections 29-39,
 * 55-59). Raw events remain authoritative; this can be re-run at any time
 * and will always reproduce the same cycles - including the same
 * [DerivedCycle.cycleId] values - for the same event log, so repeated
 * exports/recomputations of an unchanged log can be joined and deduplicated.
 */
class RuntimeAnalysisEngine(
    private val shiftEngine: ShiftEngine,
    private val config: RuntimeAnalysisConfig = RuntimeAnalysisConfig()
) {

    private class OpenInterval(
        val batteryId: Int,
        val startTimestamp: Long,
        val startEventId: String,
        val startSequenceNumber: Long,
        var hasClockAnomaly: Boolean,
        var lastConfirmedAt: Long,
        var lastConfirmedEventId: String
    )

    /** Stable across re-derivation: two events at fixed identities always define the same cycle. */
    private fun stableCycleId(startEventId: String, endEventId: String?): String =
        "$startEventId:${endEventId ?: "open"}"

    fun deriveCycles(allEvents: List<DomainEvent>): List<DerivedCycle> {
        val effective = EventFiltering.effectiveChronologicalEvents(allEvents)
        val open = mutableMapOf<RemoteId, OpenInterval>()
        val cycles = mutableListOf<DerivedCycle>()

        for (event in effective) {
            // A clock jump - or a lost monotonic timeline across a reboot - is device-wide:
            // it corrupts the wall-clock duration of every interval open at that instant,
            // not just the one tied to whichever remote's event happened to detect it
            // (e.g. a jump surfaced while changing West must still poison an interval
            // that's open on East). This must run before the remoteId null-check below so
            // it also sees anomaly warnings that aren't themselves tied to a single
            // remote, such as the one Undo can emit.
            if (event.wallClockAnomalyDetected || event.monotonicContinuityBroken) {
                open.values.forEach { it.hasClockAnomaly = true }
            }

            val remoteId = event.remoteId ?: continue
            when (event.eventType) {
                EventType.BATTERY_INSTALLED -> {
                    open[remoteId]?.let { cycles += closeCleanDeath(it, event, remoteId) }
                    val newBatteryId = event.newBatteryId ?: event.batteryId
                    if (newBatteryId != null) {
                        open[remoteId] = OpenInterval(
                            batteryId = newBatteryId,
                            startTimestamp = event.timestampEpochMillis,
                            startEventId = event.eventId,
                            startSequenceNumber = event.sequenceNumber,
                            hasClockAnomaly = event.wallClockAnomalyDetected || event.monotonicContinuityBroken,
                            lastConfirmedAt = event.timestampEpochMillis,
                            lastConfirmedEventId = event.eventId
                        )
                    }
                }

                EventType.BATTERY_REMOVED_DEAD -> {
                    val current = open[remoteId]
                    if (current != null && current.batteryId == event.batteryId) {
                        cycles += closeCleanDeath(current, event, remoteId)
                        open.remove(remoteId)
                    }
                }

                EventType.STATE_CORRECTED -> {
                    open[remoteId]?.let { cycles += closeUncertain(it, remoteId) }
                    val newBatteryId = event.newBatteryId
                    if (newBatteryId != null) {
                        open[remoteId] = OpenInterval(
                            batteryId = newBatteryId,
                            startTimestamp = event.timestampEpochMillis,
                            startEventId = event.eventId,
                            startSequenceNumber = event.sequenceNumber,
                            hasClockAnomaly = event.wallClockAnomalyDetected || event.monotonicContinuityBroken,
                            lastConfirmedAt = event.timestampEpochMillis,
                            lastConfirmedEventId = event.eventId
                        )
                    }
                }

                EventType.STATE_MARKED_UNKNOWN -> {
                    open[remoteId]?.let { cycles += closeUncertain(it, remoteId) }
                    open.remove(remoteId)
                }

                EventType.STATE_CONFIRMED -> {
                    val current = open[remoteId]
                    if (current != null && current.batteryId == event.batteryId) {
                        current.lastConfirmedAt = event.timestampEpochMillis
                        current.lastConfirmedEventId = event.eventId
                    }
                }

                EventType.UNDO_ACTION, EventType.SYSTEM_TIME_WARNING -> Unit
            }
        }

        // Intervals still open when the log ends (the battery is still installed) are not
        // "completed" cycles, but if they were confirmed at least once, that confirmation
        // is still a real lower bound on how long the battery has run so far (spec section
        // 33) and must not be silently dropped just because nothing has closed it yet.
        for ((openRemoteId, interval) in open) {
            if (interval.lastConfirmedAt > interval.startTimestamp) {
                cycles += confirmedMinimum(interval, openRemoteId)
            }
        }

        return applyStatisticalClassification(cycles)
    }

    private fun closeCleanDeath(interval: OpenInterval, endEvent: DomainEvent, remoteId: RemoteId): DerivedCycle {
        val rawDuration = endEvent.timestampEpochMillis - interval.startTimestamp
        val (minimum, maximum) = shiftEngine.activeRuntimeRange(interval.startTimestamp, endEvent.timestampEpochMillis)
        // Section 31: a reliable install and a reliable removal are necessary but not
        // sufficient for EXACT - a wall-clock jump, or a lost monotonic timeline across a
        // reboot, anywhere in the interval (its start, its end, or an intermediate
        // confirmation) means the elapsed time itself can't be trusted, so such a cycle
        // can be at best SHIFT_INTERRUPTED.
        val clockAnomaly = interval.hasClockAnomaly || endEvent.wallClockAnomalyDetected || endEvent.monotonicContinuityBroken
        val classification = if (minimum == rawDuration && !clockAnomaly) {
            RuntimeClassification.EXACT
        } else {
            RuntimeClassification.SHIFT_INTERRUPTED
        }
        return DerivedCycle(
            cycleId = stableCycleId(interval.startEventId, endEvent.eventId),
            batteryId = interval.batteryId,
            remoteId = remoteId,
            startTimestamp = interval.startTimestamp,
            startSequenceNumber = interval.startSequenceNumber,
            endTimestamp = endEvent.timestampEpochMillis,
            minimumActiveRuntimeMillis = minimum,
            maximumActiveRuntimeMillis = if (classification == RuntimeClassification.EXACT) minimum else maximum,
            classification = classification,
            isHighOutlier = false,
            isShortRuntimeEvent = false,
            includedInPrimaryStatistics = classification == RuntimeClassification.EXACT,
            startEventId = interval.startEventId,
            endEventId = endEvent.eventId
        )
    }

    /** Closes an interval whose true end is unknown - a correction or explicit unknown marking. */
    private fun closeUncertain(interval: OpenInterval, remoteId: RemoteId): DerivedCycle {
        val hadIntermediateConfirmation = interval.lastConfirmedAt > interval.startTimestamp
        return if (hadIntermediateConfirmation) {
            confirmedMinimum(interval, remoteId)
        } else {
            unknownCycle(interval, remoteId)
        }
    }

    private fun unknownCycle(interval: OpenInterval, remoteId: RemoteId): DerivedCycle = DerivedCycle(
        cycleId = stableCycleId(interval.startEventId, null),
        batteryId = interval.batteryId,
        remoteId = remoteId,
        startTimestamp = interval.startTimestamp,
        startSequenceNumber = interval.startSequenceNumber,
        endTimestamp = null,
        minimumActiveRuntimeMillis = 0,
        maximumActiveRuntimeMillis = 0,
        classification = RuntimeClassification.UNKNOWN,
        isHighOutlier = false,
        isShortRuntimeEvent = false,
        includedInPrimaryStatistics = false,
        startEventId = interval.startEventId,
        endEventId = null
    )

    /**
     * A reliable lower bound on how long [interval]'s battery has run: from its start
     * to its last confirmation. Used both when a correction/unknown-marking closes an
     * interval that had at least one confirmation, and for intervals that are still
     * open when the log ends but were confirmed at least once (spec section 33).
     *
     * If a clock anomaly touched this interval at any point, that lower bound can't be
     * trusted either - the elapsed time to the confirmation was measured against a wall
     * clock known to have jumped, so this falls back to UNKNOWN rather than presenting
     * an inflated or otherwise wrong duration as a reliable minimum.
     */
    private fun confirmedMinimum(interval: OpenInterval, remoteId: RemoteId): DerivedCycle {
        if (interval.hasClockAnomaly) return unknownCycle(interval, remoteId)
        val (minimum, maximum) = shiftEngine.activeRuntimeRange(interval.startTimestamp, interval.lastConfirmedAt)
        return DerivedCycle(
            cycleId = stableCycleId(interval.startEventId, interval.lastConfirmedEventId),
            batteryId = interval.batteryId,
            remoteId = remoteId,
            startTimestamp = interval.startTimestamp,
            startSequenceNumber = interval.startSequenceNumber,
            endTimestamp = interval.lastConfirmedAt,
            minimumActiveRuntimeMillis = minimum,
            maximumActiveRuntimeMillis = maximum,
            classification = RuntimeClassification.CONFIRMED_MINIMUM,
            isHighOutlier = false,
            isShortRuntimeEvent = false,
            includedInPrimaryStatistics = false,
            startEventId = interval.startEventId,
            endEventId = interval.lastConfirmedEventId
        )
    }

    /** Pass 2: flag statistical outliers and short cycles per battery, never touching raw events. */
    private fun applyStatisticalClassification(cycles: List<DerivedCycle>): List<DerivedCycle> {
        val byBattery = cycles
            .filter { it.classification == RuntimeClassification.EXACT }
            .groupBy { it.batteryId }

        val highOutlierCycleIds = mutableSetOf<String>()
        val shortCycleIds = mutableSetOf<String>()

        for ((_, batteryCycles) in byBattery) {
            // Insertion order, not wall-clock time: a backward clock correction can give a
            // genuinely later cycle an earlier startTimestamp than an older one, which
            // would silently swap which cycles count as "recent" for outlier/short-cycle
            // detection.
            val chronological = batteryCycles.sortedBy { it.startSequenceNumber }
            val durations = chronological.map { it.minimumActiveRuntimeMillis }

            if (durations.size >= config.minReliableCyclesForOutlierDetection) {
                val median = Statistics.median(durations) ?: continue
                val mad = Statistics.medianAbsoluteDeviation(durations) ?: 0.0
                for (cycle in chronological) {
                    val duration = cycle.minimumActiveRuntimeMillis
                    val beyondMedian = duration - median > config.suspiciousHighAbsoluteThresholdMillis
                    if (!beyondMedian) continue
                    val z = Statistics.modifiedZScore(duration, durations)
                    val robustlyExtreme = mad == 0.0 || (z != null && z > config.suspiciousHighModifiedZScoreThreshold)
                    if (robustlyExtreme) highOutlierCycleIds += cycle.cycleId
                }
            }

            val nonOutlierDurations = chronological
                .filterNot { it.cycleId in highOutlierCycleIds }
                .map { it.minimumActiveRuntimeMillis }
            if (nonOutlierDurations.size >= config.minCyclesForShortRuntimeDetection) {
                val baseline = Statistics.median(nonOutlierDurations) ?: continue
                for (cycle in chronological) {
                    if (cycle.cycleId in highOutlierCycleIds) continue
                    if (cycle.minimumActiveRuntimeMillis < baseline * config.shortRuntimeRatio) {
                        shortCycleIds += cycle.cycleId
                    }
                }
            }
        }

        return cycles.map { cycle ->
            if (cycle.classification != RuntimeClassification.EXACT) return@map cycle
            val isHigh = cycle.cycleId in highOutlierCycleIds
            val isShort = cycle.cycleId in shortCycleIds
            cycle.copy(
                isHighOutlier = isHigh,
                isShortRuntimeEvent = isShort,
                includedInPrimaryStatistics = !isHigh
            )
        }
    }
}
