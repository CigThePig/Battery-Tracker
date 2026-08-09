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
 * The two-tap normal operator workflow (spec section 12): "which battery
 * did you put in?" Also covers setting a battery from an UNKNOWN remote,
 * since that case simply has no prior battery to mark dead.
 */
class BatteryChangeUseCase(
    private val repository: BatteryTrackerRepository,
    private val appVersion: String
) {
    suspend operator fun invoke(remoteId: RemoteId, newBatteryId: Int, now: Long = System.currentTimeMillis()) {
        repository.inTransaction {
            val knowledge = EventReducer.reduce(repository.currentEventsSnapshot())

            val otherRemote = remoteId.other()
            val otherKnowledge = knowledge[otherRemote]
            if (otherKnowledge is RemoteKnowledge.Known && otherKnowledge.batteryId == newBatteryId) {
                throw BatteryTrackerException.BatteryOwnedByOtherRemote(otherRemote, newBatteryId)
            }

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
                            wallClockAnomalyDetected = false
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
                        wallClockAnomalyDetected = false
                    )
                )
            }

            repository.writeEventGroup(events)
        }
    }
}
