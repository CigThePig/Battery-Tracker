package com.cranebatterytracker.domain.usecase

import com.cranebatterytracker.domain.analysis.ClockAnomalyDetector
import com.cranebatterytracker.domain.analysis.EventReducer
import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RemoteKnowledge
import com.cranebatterytracker.domain.repository.BatteryTrackerRepository
import java.util.UUID

/**
 * Refreshes confidence in the currently-known battery/batteries without
 * changing them - the stale card's "STILL 2" tap, and the shift-start
 * banner's "BOTH STILL CORRECT" (spec sections 27-28). Confirming more than
 * one remote at once writes every confirmation under a single action group
 * so the permanent Undo control reverses the whole button press atomically,
 * not just one remote's half of it.
 */
class ConfirmStateUseCase(
    private val repository: BatteryTrackerRepository,
    private val appVersion: String
) {
    suspend operator fun invoke(
        vararg remoteIds: RemoteId,
        now: Long = System.currentTimeMillis(),
        elapsedRealtimeMillis: Long = now
    ) {
        require(remoteIds.isNotEmpty()) { "At least one remote must be confirmed" }
        repository.inTransaction {
            val priorEvents = repository.currentEventsSnapshot()
            val knowledge = EventReducer.reduce(priorEvents)
            val anomalyDetected = ClockAnomalyDetector.detect(priorEvents, now, elapsedRealtimeMillis)
            val monotonicContinuityBroken = ClockAnomalyDetector.monotonicContinuityLost(priorEvents, elapsedRealtimeMillis)
            val groupId = UUID.randomUUID().toString()

            val events = buildList {
                for (remoteId in remoteIds) {
                    val known = knowledge[remoteId] as? RemoteKnowledge.Known
                        ?: throw BatteryTrackerException.NothingToConfirm
                    add(
                        DomainEvent(
                            eventId = UUID.randomUUID().toString(),
                            actionGroupId = groupId,
                            timestampEpochMillis = now,
                            remoteId = remoteId,
                            batteryId = known.batteryId,
                            eventType = EventType.STATE_CONFIRMED,
                            previousBatteryId = known.batteryId,
                            newBatteryId = known.batteryId,
                            targetActionGroupId = null,
                            createdByAppVersion = appVersion,
                            wallClockAnomalyDetected = anomalyDetected,
                            elapsedRealtimeMillis = elapsedRealtimeMillis,
                            monotonicContinuityBroken = monotonicContinuityBroken
                        )
                    )
                }
                if (anomalyDetected) add(systemTimeWarningEvent(remoteIds.first(), groupId, now, appVersion))
            }

            repository.writeEventGroup(events)
        }
    }
}
