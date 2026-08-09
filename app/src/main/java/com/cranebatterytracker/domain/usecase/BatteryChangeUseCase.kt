package com.cranebatterytracker.domain.usecase

import com.cranebatterytracker.domain.analysis.ClockAnomalyDetector
import com.cranebatterytracker.domain.analysis.EventReducer
import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RemoteKnowledge
import com.cranebatterytracker.domain.model.other
import com.cranebatterytracker.domain.repository.BatteryTrackerRepository
import java.util.UUID

/**
 * The two-tap normal operator workflow (spec section 12): "which battery
 * did you put in?" Also covers setting a battery from an UNKNOWN remote,
 * since that case simply has no prior battery to mark dead.
 */
class BatteryChangeUseCase(
    private val repository: BatteryTrackerRepository,
    private val appVersion: String
) {
    /**
     * [elapsedRealtimeMillis] should be the device's monotonic clock (e.g.
     * SystemClock.elapsedRealtime()) at the same moment as [now]; defaulting
     * it to [now] simply disables anomaly detection for callers that don't
     * have a monotonic reading available (such as tests).
     */
    suspend operator fun invoke(
        remoteId: RemoteId,
        newBatteryId: Int,
        now: Long = System.currentTimeMillis(),
        elapsedRealtimeMillis: Long = now
    ) {
        repository.inTransaction {
            val priorEvents = repository.currentEventsSnapshot()
            val knowledge = EventReducer.reduce(priorEvents)

            val otherRemote = remoteId.other()
            val otherKnowledge = knowledge[otherRemote]
            if (otherKnowledge is RemoteKnowledge.Known && otherKnowledge.batteryId == newBatteryId) {
                throw BatteryTrackerException.BatteryOwnedByOtherRemote(otherRemote, newBatteryId)
            }

            val anomalyDetected = ClockAnomalyDetector.detect(priorEvents, now, elapsedRealtimeMillis)
            val currentBatteryId = (knowledge[remoteId] as? RemoteKnowledge.Known)?.batteryId
            val groupId = UUID.randomUUID().toString()
            val events = buildList {
                if (currentBatteryId != null) {
                    add(
                        DomainEvent(
                            eventId = UUID.randomUUID().toString(),
                            actionGroupId = groupId,
                            timestampEpochMillis = now,
                            remoteId = remoteId,
                            batteryId = currentBatteryId,
                            eventType = EventType.BATTERY_REMOVED_DEAD,
                            previousBatteryId = currentBatteryId,
                            newBatteryId = null,
                            targetActionGroupId = null,
                            createdByAppVersion = appVersion,
                            wallClockAnomalyDetected = anomalyDetected,
                            elapsedRealtimeMillis = elapsedRealtimeMillis
                        )
                    )
                }
                add(
                    DomainEvent(
                        eventId = UUID.randomUUID().toString(),
                        actionGroupId = groupId,
                        timestampEpochMillis = now,
                        remoteId = remoteId,
                        batteryId = newBatteryId,
                        eventType = EventType.BATTERY_INSTALLED,
                        previousBatteryId = currentBatteryId,
                        newBatteryId = newBatteryId,
                        targetActionGroupId = null,
                        createdByAppVersion = appVersion,
                        wallClockAnomalyDetected = anomalyDetected,
                        elapsedRealtimeMillis = elapsedRealtimeMillis
                    )
                )
                if (anomalyDetected) {
                    add(systemTimeWarningEvent(remoteId, groupId, now, appVersion))
                }
            }

            repository.writeEventGroup(events)
        }
    }
}
