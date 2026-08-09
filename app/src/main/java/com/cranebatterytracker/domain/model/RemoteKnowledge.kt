package com.cranebatterytracker.domain.model

/**
 * The reducer's pure, time-independent conclusion about a remote: what the
 * event log says, with no notion of "is this still fresh right now". A
 * [ShiftEngine] later turns this into a [RemoteState] for display by
 * comparing [Known.confirmedAt] against shift boundaries.
 */
sealed interface RemoteKnowledge {
    data class Known(
        val batteryId: Int,
        val installedAt: Long,
        val confirmedAt: Long
    ) : RemoteKnowledge

    data class Unknown(
        val sinceAt: Long?
    ) : RemoteKnowledge
}

/** Display-ready tri-state used by the UI. See spec section 24. */
sealed interface RemoteState {
    data class Confirmed(
        val batteryId: Int,
        val installedAt: Long,
        val confirmedAt: Long
    ) : RemoteState

    data class Stale(
        val batteryId: Int,
        val installedAt: Long,
        val confirmedAt: Long
    ) : RemoteState

    data class Unknown(
        val sinceAt: Long?
    ) : RemoteState
}

val RemoteState.batteryIdOrNull: Int?
    get() = when (this) {
        is RemoteState.Confirmed -> batteryId
        is RemoteState.Stale -> batteryId
        is RemoteState.Unknown -> null
    }
