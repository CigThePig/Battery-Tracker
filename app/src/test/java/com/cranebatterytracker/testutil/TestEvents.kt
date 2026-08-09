package com.cranebatterytracker.testutil

import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteId
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Mimics Room's autoGenerate row id: each call gets the next value, matching construction order. */
private val sequenceCounter = AtomicLong(1)

fun testEvent(
    eventId: String = UUID.randomUUID().toString(),
    sequenceNumber: Long = sequenceCounter.getAndIncrement(),
    actionGroupId: String? = UUID.randomUUID().toString(),
    timestamp: Long,
    remoteId: RemoteId? = null,
    batteryId: Int? = null,
    eventType: EventType,
    previousBatteryId: Int? = null,
    newBatteryId: Int? = null,
    targetActionGroupId: String? = null
): DomainEvent = DomainEvent(
    eventId = eventId,
    sequenceNumber = sequenceNumber,
    actionGroupId = actionGroupId,
    timestampEpochMillis = timestamp,
    remoteId = remoteId,
    batteryId = batteryId,
    eventType = eventType,
    previousBatteryId = previousBatteryId,
    newBatteryId = newBatteryId,
    targetActionGroupId = targetActionGroupId,
    createdByAppVersion = "test",
    wallClockAnomalyDetected = false,
    notes = null
)

/** A normal battery change: removal of [oldBatteryId] (if any) paired with installation of [newBatteryId]. */
fun testBatteryChange(
    timestamp: Long,
    remoteId: RemoteId,
    oldBatteryId: Int?,
    newBatteryId: Int,
    groupId: String = UUID.randomUUID().toString()
): List<DomainEvent> = buildList {
    if (oldBatteryId != null) {
        add(
            testEvent(
                actionGroupId = groupId,
                timestamp = timestamp,
                remoteId = remoteId,
                batteryId = oldBatteryId,
                eventType = EventType.BATTERY_REMOVED_DEAD,
                previousBatteryId = oldBatteryId
            )
        )
    }
    add(
        testEvent(
            actionGroupId = groupId,
            timestamp = timestamp,
            remoteId = remoteId,
            batteryId = newBatteryId,
            eventType = EventType.BATTERY_INSTALLED,
            previousBatteryId = oldBatteryId,
            newBatteryId = newBatteryId
        )
    )
}
