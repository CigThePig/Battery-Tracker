package com.cranebatterytracker.domain.usecase

import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteId
import java.util.UUID

/**
 * A SYSTEM_TIME_WARNING event recording that a clock anomaly was detected around this
 * action (spec section 57/67). [remoteId] is nullable because some actions that can
 * detect an anomaly - Undo, in particular - aren't inherently tied to a single remote.
 *
 * This deliberately does NOT belong to [triggeringActionGroupId]'s action group
 * ([DomainEvent.actionGroupId] is left null). Clock-anomaly evidence describes the
 * device timeline, not an operator decision, so undoing the operator action that
 * happened to surface it must never also erase the warning ([EventFiltering] only
 * ever removes events that belong to an undone action group). [triggeringActionGroupId]
 * is still recorded, informationally, in [DomainEvent.targetActionGroupId] so the
 * warning can be traced back to what surfaced it - that field is otherwise only
 * interpreted for UNDO_ACTION events, so it has no effect on undo/replay logic here.
 *
 * [wallClockAnomalyDetected] and [monotonicContinuityBroken] default to the original
 * "wall clock jumped" case, but a caller that only detected a lost monotonic timeline
 * (an ordinary reboot, with no wall-clock jump) should pass `wallClockAnomalyDetected =
 * false, monotonicContinuityBroken = true` instead - a reboot must never be reported as
 * wall-clock tampering.
 */
internal fun systemTimeWarningEvent(
    remoteId: RemoteId?,
    triggeringActionGroupId: String?,
    timestamp: Long,
    appVersion: String,
    wallClockAnomalyDetected: Boolean = true,
    monotonicContinuityBroken: Boolean = false
): DomainEvent = DomainEvent(
    eventId = UUID.randomUUID().toString(),
    actionGroupId = null,
    timestampEpochMillis = timestamp,
    remoteId = remoteId,
    batteryId = null,
    eventType = EventType.SYSTEM_TIME_WARNING,
    previousBatteryId = null,
    newBatteryId = null,
    targetActionGroupId = triggeringActionGroupId,
    createdByAppVersion = appVersion,
    wallClockAnomalyDetected = wallClockAnomalyDetected,
    monotonicContinuityBroken = monotonicContinuityBroken,
    notes = if (wallClockAnomalyDetected) {
        "Wall-clock time disagreed with the device's monotonic clock around this action."
    } else {
        "The device's monotonic clock lost continuity (e.g. a reboot) around this action."
    }
)
