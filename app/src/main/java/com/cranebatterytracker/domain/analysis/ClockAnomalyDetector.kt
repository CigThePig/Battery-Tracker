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
     * recent prior event that also recorded a monotonic reading by more than
     * [thresholdMillis]. Returns false (no anomaly) when there is nothing to compare
     * against, or when the monotonic clock went backwards - that means the device
     * rebooted, which is an ordinary event, not clock tampering.
     */
    fun detect(
        priorEvents: List<DomainEvent>,
        newWallClockMillis: Long,
        newElapsedRealtimeMillis: Long,
        thresholdMillis: Long = DEFAULT_THRESHOLD_MILLIS
    ): Boolean {
        val previous = priorEvents
            .filter { it.elapsedRealtimeMillis != null }
            .maxByOrNull { it.timestampEpochMillis } ?: return false

        val previousElapsedRealtime = previous.elapsedRealtimeMillis ?: return false
        val monotonicDelta = newElapsedRealtimeMillis - previousElapsedRealtime
        if (monotonicDelta < 0) return false

        val wallClockDelta = newWallClockMillis - previous.timestampEpochMillis
        return abs(wallClockDelta - monotonicDelta) > thresholdMillis
    }
}
