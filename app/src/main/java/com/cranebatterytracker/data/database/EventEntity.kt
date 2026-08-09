package com.cranebatterytracker.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.toStorageKey

@Entity(
    tableName = "events",
    indices = [
        Index("eventId", unique = true),
        Index("actionGroupId"),
        Index("timestampEpochMillis"),
        Index("remoteId"),
        Index("batteryId"),
        Index("targetActionGroupId")
    ]
)
data class EventEntity(
    // Auto-generated, strictly increasing insertion order - the authority for replay
    // order (see DomainEvent.sequenceNumber). eventId remains the stable logical
    // identity referenced elsewhere (targetActionGroupId, DerivedCycle start/end ids).
    @PrimaryKey(autoGenerate = true) val sequenceNumber: Long = 0,
    val eventId: String,
    val actionGroupId: String?,
    val timestampEpochMillis: Long,
    val remoteId: String?,
    val batteryId: Int?,
    val eventType: String,
    val previousBatteryId: Int?,
    val newBatteryId: Int?,
    val targetActionGroupId: String?,
    val createdByAppVersion: String,
    val wallClockAnomalyDetected: Boolean,
    val elapsedRealtimeMillis: Long?,
    val monotonicContinuityBroken: Boolean = false,
    val notes: String?
)

fun EventEntity.toDomain(): DomainEvent = DomainEvent(
    eventId = eventId,
    sequenceNumber = sequenceNumber,
    actionGroupId = actionGroupId,
    timestampEpochMillis = timestampEpochMillis,
    remoteId = remoteId?.let { RemoteId.fromStorageKey(it) },
    batteryId = batteryId,
    eventType = EventType.valueOf(eventType),
    previousBatteryId = previousBatteryId,
    newBatteryId = newBatteryId,
    targetActionGroupId = targetActionGroupId,
    createdByAppVersion = createdByAppVersion,
    wallClockAnomalyDetected = wallClockAnomalyDetected,
    elapsedRealtimeMillis = elapsedRealtimeMillis,
    monotonicContinuityBroken = monotonicContinuityBroken,
    notes = notes
)

/**
 * [DomainEvent.sequenceNumber] is intentionally dropped here: it must come from Room's
 * autoGenerate on insert, not be carried over from a domain object that hasn't been
 * persisted yet. Passing 0 tells Room to assign the next value.
 */
fun DomainEvent.toEntity(): EventEntity = EventEntity(
    eventId = eventId,
    actionGroupId = actionGroupId,
    timestampEpochMillis = timestampEpochMillis,
    remoteId = remoteId?.toStorageKey(),
    batteryId = batteryId,
    eventType = eventType.name,
    previousBatteryId = previousBatteryId,
    newBatteryId = newBatteryId,
    targetActionGroupId = targetActionGroupId,
    createdByAppVersion = createdByAppVersion,
    wallClockAnomalyDetected = wallClockAnomalyDetected,
    elapsedRealtimeMillis = elapsedRealtimeMillis,
    monotonicContinuityBroken = monotonicContinuityBroken,
    notes = notes
)
