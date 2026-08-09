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
        Index("actionGroupId"),
        Index("timestampEpochMillis"),
        Index("remoteId"),
        Index("batteryId"),
        Index("targetActionGroupId")
    ]
)
data class EventEntity(
    @PrimaryKey val eventId: String,
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
    val notes: String?
)

fun EventEntity.toDomain(): DomainEvent = DomainEvent(
    eventId = eventId,
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
    notes = notes
)

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
    notes = notes
)
