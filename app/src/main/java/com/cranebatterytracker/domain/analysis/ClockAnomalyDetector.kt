package com.cranebatterytracker.domain.analysis

import com.cranebatterytracker.domain.model.DomainEvent
import kotlin.math.abs

/**
 * Compares wall-clock time against the device's monotonic clock between
 * consecutive events so an ordinary device-time correction (someone moves
 * the tablet's clock) can be told apart from real elapsed time (spec
 * section 67). A cycle whose endpoints disagree this way must never be
 * silently trusted as an exact runtime.
 */
object ClockAnomalyDetector {

    private const val DEFAULT_THRESHOLD_MILLIS = 2 * 60_000L

    /**
     * True when [newWallClockMillis]/[newElapsedRealtimeMillis] disagree with the most
     * recently *recorded* prior event that also captured a monotonic reading, by more
     * than [thresholdMillis]. "Most recently recorded" is by insertion sequence, not by
     * wall-clock time: after a backward clock correction, the event with the highest
     * timestamp is the stale pre-correction one, and comparing against it would flag
     * every ordinary event for a while as anomalous until wall time caught back up.
     * Returns false (no anomaly) when there is nothing to compare against, or when the
     * monotonic clock went backwards - that means the device rebooted, which is an
     * ordinary event, not clock tampering.
     */
    fun detect(
        priorEvents: List<DomainEvent>,
        newWallClockMillis: Long,
        newElapsedRealtimeMillis: Long,
        thresholdMillis: Long = DEFAULT_THRESHOLD_MILLIS
    ): Boolean {
        val previous = priorEvents
            .filter { it.elapsedRealtimeMillis != null }
            .maxByOrNull { it.sequenceNumber } ?: return false

        val previousElapsedRealtime = previous.elapsedRealtimeMillis ?: return false
        val monotonicDelta = newElapsedRealtimeMillis - previousElapsedRealtime
        if (monotonicDelta < 0) return false

        val wallClockDelta = newWallClockMillis - previous.timestampEpochMillis
        return abs(wallClockDelta - monotonicDelta) > thresholdMillis
    }

    /**
     * True when the device's monotonic clock moved backward relative to the most
     * recently *recorded* prior event - almost always an ordinary reboot (spec review
     * Issue 12). [detect] deliberately treats this as "no wall-clock anomaly" so a reboot
     * never falsely implies clock tampering, but a reboot still breaks the continuous
     * monotonic timeline an EXACT classification depends on: elapsed time can no longer
     * be compared across the gap. This is a distinct, milder fact from a wall-clock jump -
     * "exact elapsed-time verification was unavailable across this point," not "the clock
     * is wrong" - so it is reported separately rather than folded into [detect]'s result.
     */
    fun monotonicContinuityLost(priorEvents: List<DomainEvent>, newElapsedRealtimeMillis: Long): Boolean {
        val previousElapsedRealtime = priorEvents
            .filter { it.elapsedRealtimeMillis != null }
            .maxByOrNull { it.sequenceNumber }
            ?.elapsedRealtimeMillis ?: return false
        return newElapsedRealtimeMillis < previousElapsedRealtime
    }
}
