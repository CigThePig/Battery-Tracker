package com.cranebatterytracker.domain.model

/**
 * Pure, framework-free representation of a raw historical event. Mirrors
 * EventEntity but carries no Room/Android dependency so the reducer and
 * analysis engines can be unit tested on a plain JVM.
 */
data class DomainEvent(
    val eventId: String,
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
    val notes: String? = null
)
