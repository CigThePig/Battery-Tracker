package com.cranebatterytracker.domain.usecase

import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteId
import java.util.UUID

/** A SYSTEM_TIME_WARNING event recording that a clock anomaly was detected around this action (spec section 57/67). */
internal fun systemTimeWarningEvent(
    remoteId: RemoteId,
    actionGroupId: String,
    timestamp: Long,
    appVersion: String
): DomainEvent = DomainEvent(
    eventId = UUID.randomUUID().toString(),
    actionGroupId = actionGroupId,
    timestampEpochMillis = timestamp,
    remoteId = remoteId,
    batteryId = null,
    eventType = EventType.SYSTEM_TIME_WARNING,
    previousBatteryId = null,
    newBatteryId = null,
    targetActionGroupId = null,
    createdByAppVersion = appVersion,
    wallClockAnomalyDetected = true,
    notes = "Wall-clock time disagreed with the device's monotonic clock around this action."
)
