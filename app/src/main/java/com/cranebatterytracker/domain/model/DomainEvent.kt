package com.cranebatterytracker.domain.model

/**
 * Pure, framework-free representation of a raw historical event. Mirrors
 * EventEntity but carries no Room/Android dependency so the reducer and
 * analysis engines can be unit tested on a plain JVM.
 */
data class DomainEvent(
    val eventId: String,
    /**
     * A strictly increasing, database-assigned insertion order (Room's
     * autoGenerate row id). This - not [timestampEpochMillis] - is what
     * [com.cranebatterytracker.domain.analysis.EventFiltering] replays
     * events by: wall-clock time can jump backward (an operator corrects
     * the tablet's clock) without the real order actions happened in ever
     * changing, and only the insertion sequence is guaranteed monotonic.
     * Zero for an event that hasn't been persisted yet.
     */
    val sequenceNumber: Long = 0,
    val actionGroupId: String?,
    val timestampEpochMillis: Long,
    val remoteId: RemoteId?,
    val batteryId: Int?,
    val eventType: EventType,
    val previousBatteryId: Int?,
    val newBatteryId: Int?,
    val targetActionGroupId: String?,
    val createdByAppVersion: String,
    val wallClockAnomalyDetected: Boolean,
    /**
     * Device monotonic clock reading (e.g. SystemClock.elapsedRealtime())
     * at the moment this event was recorded, alongside [timestampEpochMillis].
     * Comparing how these two clocks drift between consecutive events is
     * what lets [com.cranebatterytracker.domain.analysis.ClockAnomalyDetector]
     * notice a wall-clock jump instead of trusting elapsed wall-clock time
     * blindly (spec section 67). Null when no monotonic reading is available.
     */
    val elapsedRealtimeMillis: Long? = null,
    val notes: String? = null
)
