package com.cranebatterytracker.domain.model

/**
 * A derived, recalculable measurement of one battery's time in one remote.
 * Raw events remain authoritative; DerivedCycle records may be regenerated
 * at any time from the event log (spec section 59).
 */
data class DerivedCycle(
    val cycleId: String,
    val batteryId: Int,
    val remoteId: RemoteId,
    val startTimestamp: Long,
    val endTimestamp: Long?,
    val minimumActiveRuntimeMillis: Long,
    val maximumActiveRuntimeMillis: Long,
    val classification: RuntimeClassification,
    val isHighOutlier: Boolean,
    val isShortRuntimeEvent: Boolean,
    val includedInPrimaryStatistics: Boolean,
    val startEventId: String,
    val endEventId: String?
) {
    /** EXACT cycles always have minimum == maximum. */
    val exactRuntimeMillisOrNull: Long?
        get() = if (classification == RuntimeClassification.EXACT) minimumActiveRuntimeMillis else null

    /** What a graph/history row should label this point as, folding the outlier flag in. */
    val displayClassification: RuntimeClassification
        get() = if (isHighOutlier) RuntimeClassification.SUSPICIOUS_HIGH else classification
}
