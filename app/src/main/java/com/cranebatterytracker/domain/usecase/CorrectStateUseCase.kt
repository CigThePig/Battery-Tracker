package com.cranebatterytracker.domain.usecase

import com.cranebatterytracker.domain.analysis.EventReducer
import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RemoteKnowledge
import com.cranebatterytracker.domain.model.other
import com.cranebatterytracker.domain.repository.BatteryTrackerRepository
import java.util.UUID

/**
 * "The tablet currently says X, what's actually in the remote?" (spec
 * sections 17-19, 21, 64). This never fabricates a dead event for the
 * previous battery - it just becomes uncertain. If the chosen battery is
 * currently recorded in the other remote, the caller must set
 * [resolveCollision] to true (after the operator confirms) to also mark
 * that other remote unknown as part of the same transaction.
 */
class CorrectStateUseCase(
    private val repository: BatteryTrackerRepository,
    private val appVersion: String
) {
    suspend operator fun invoke(
        remoteId: RemoteId,
        newBatteryId: Int,
        now: Long = System.currentTimeMillis(),
        resolveCollision: Boolean = false
    ) {
        repository.inTransaction {
            val knowledge = EventReducer.reduce(repository.currentEventsSnapshot())

            val otherRemote = remoteId.other()
            val otherKnowledge = knowledge[otherRemote]
            val collision = otherKnowledge is RemoteKnowledge.Known && otherKnowledge.batteryId == newBatteryId
            if (collision && !resolveCollision) {
                throw BatteryTrackerException.BatteryOwnedByOtherRemote(otherRemote, newBatteryId)
            }

            val previousBatteryId = (knowledge[remoteId] as? RemoteKnowledge.Known)?.batteryId
            val groupId = UUID.randomUUID().toString()

            val events = buildList {
                add(
                    DomainEvent(
                        eventId = UUID.randomUUID().toString(),
                        actionGroupId = groupId,
                        timestampEpochMillis = now,
                        remoteId = remoteId,
                        batteryId = newBatteryId,
                        eventType = EventType.STATE_CORRECTED,
                        previousBatteryId = previousBatteryId,
                        newBatteryId = newBatteryId,
                        targetActionGroupId = null,
                        createdByAppVersion = appVersion,
                        wallClockAnomalyDetected = false
                    )
                )
                if (collision) {
                    add(
                        DomainEvent(
                            eventId = UUID.randomUUID().toString(),
                            actionGroupId = groupId,
                            timestampEpochMillis = now,
                            remoteId = otherRemote,
                            batteryId = null,
                            eventType = EventType.STATE_MARKED_UNKNOWN,
                            previousBatteryId = newBatteryId,
                            newBatteryId = null,
                            targetActionGroupId = null,
                            createdByAppVersion = appVersion,
                            wallClockAnomalyDetected = false
                        )
                    )
                }
            }

            repository.writeEventGroup(events)
        }
    }
}
