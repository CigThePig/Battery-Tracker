package com.cranebatterytracker.domain.usecase

import com.cranebatterytracker.domain.analysis.EventReducer
import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RemoteKnowledge
import com.cranebatterytracker.domain.repository.BatteryTrackerRepository
import java.util.UUID

/**
 * Refreshes confidence in the currently-known battery without changing it -
 * the shift-start banner's "BOTH STILL CORRECT", the stale card's
 * "STILL 2" tap (spec sections 27-28).
 */
class ConfirmStateUseCase(
    private val repository: BatteryTrackerRepository,
    private val appVersion: String
) {
    suspend operator fun invoke(remoteId: RemoteId, now: Long = System.currentTimeMillis()) {
        repository.inTransaction {
            val knowledge = EventReducer.reduce(repository.currentEventsSnapshot())
            val known = knowledge[remoteId] as? RemoteKnowledge.Known
                ?: throw BatteryTrackerException.NothingToConfirm

            repository.writeEventGroup(
                listOf(
                    DomainEvent(
                        eventId = UUID.randomUUID().toString(),
                        actionGroupId = UUID.randomUUID().toString(),
                        timestampEpochMillis = now,
                        remoteId = remoteId,
                        batteryId = known.batteryId,
                        eventType = EventType.STATE_CONFIRMED,
                        previousBatteryId = known.batteryId,
                        newBatteryId = known.batteryId,
                        targetActionGroupId = null,
                        createdByAppVersion = appVersion,
                        wallClockAnomalyDetected = false
                    )
                )
            )
        }
    }
}
