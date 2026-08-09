package com.cranebatterytracker.domain.usecase

import com.cranebatterytracker.domain.analysis.EventFiltering
import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.repository.BatteryTrackerRepository
import java.util.UUID

/**
 * "WRONG BUTTON / UNDO" (spec section 22). Never deletes rows - it writes
 * an UNDO_ACTION event that logically reverses the whole target action
 * group on every future replay.
 */
class UndoUseCase(
    private val repository: BatteryTrackerRepository,
    private val appVersion: String
) {
    /** Pass [targetActionGroupId] to undo a specific just-shown action; omit to undo the most recent one. */
    suspend operator fun invoke(
        targetActionGroupId: String? = null,
        now: Long = System.currentTimeMillis(),
        elapsedRealtimeMillis: Long = now
    ) {
        repository.inTransaction {
            val allEvents = repository.currentEventsSnapshot()
            val undoneGroups = EventFiltering.undoneActionGroupIds(allEvents)

            val target = targetActionGroupId ?: allEvents
                .filter { it.eventType != EventType.UNDO_ACTION && it.actionGroupId != null && it.actionGroupId !in undoneGroups }
                .maxByOrNull { it.timestampEpochMillis }
                ?.actionGroupId
                ?: throw BatteryTrackerException.NothingToUndo

            if (target in undoneGroups) throw BatteryTrackerException.ActionAlreadyUndone

            repository.writeEventGroup(
                listOf(
                    DomainEvent(
                        eventId = UUID.randomUUID().toString(),
                        actionGroupId = UUID.randomUUID().toString(),
                        timestampEpochMillis = now,
                        remoteId = null,
                        batteryId = null,
                        eventType = EventType.UNDO_ACTION,
                        previousBatteryId = null,
                        newBatteryId = null,
                        targetActionGroupId = target,
                        createdByAppVersion = appVersion,
                        wallClockAnomalyDetected = false,
                        elapsedRealtimeMillis = elapsedRealtimeMillis
                    )
                )
            )
        }
    }
}
