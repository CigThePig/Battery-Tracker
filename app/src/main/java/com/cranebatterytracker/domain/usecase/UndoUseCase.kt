package com.cranebatterytracker.domain.usecase

import com.cranebatterytracker.domain.analysis.ClockAnomalyDetector
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
    companion object {
        /**
         * Event types that represent a real operator mutation and can therefore make an
         * action group Undo-eligible. UNDO_ACTION never belongs to a group worth
         * re-undoing, and SYSTEM_TIME_WARNING is timeline metadata, not an operator
         * decision - it must never make a group independently undoable, and since it no
         * longer shares an actionGroupId with the operator action that surfaced it
         * (see systemTimeWarningEvent), it wouldn't appear here anyway. Listing it out
         * explicitly still protects target selection if that ever changes.
         */
        private val OPERATOR_MUTATION_TYPES = setOf(
            EventType.BATTERY_INSTALLED,
            EventType.BATTERY_REMOVED_DEAD,
            EventType.STATE_CONFIRMED,
            EventType.STATE_CORRECTED,
            EventType.STATE_MARKED_UNKNOWN
        )
    }

    /** Pass [targetActionGroupId] to undo a specific just-shown action; omit to undo the most recent one. */
    suspend operator fun invoke(
        targetActionGroupId: String? = null,
        now: Long = System.currentTimeMillis(),
        elapsedRealtimeMillis: Long = now
    ) {
        repository.inTransaction {
            val allEvents = repository.currentEventsSnapshot()
            val undoneGroups = EventFiltering.undoneActionGroupIds(allEvents)

            // "Most recent" means most recently recorded, not highest wall-clock time - a
            // backward clock correction must not make an older action look newer again
            // (same reasoning as EventFiltering's replay order).
            val target = targetActionGroupId ?: allEvents
                .filter { it.eventType in OPERATOR_MUTATION_TYPES && it.actionGroupId != null && it.actionGroupId !in undoneGroups }
                .maxByOrNull { it.sequenceNumber }
                ?.actionGroupId
                ?: throw BatteryTrackerException.NothingToUndo

            if (target in undoneGroups) throw BatteryTrackerException.ActionAlreadyUndone

            // Undo can be the very first interaction after the device clock changes. If it
            // unconditionally recorded no anomaly, the next normal action would compare
            // against this event, see matching wall-clock/monotonic deltas, and the jump
            // would go undetected entirely - so this needs the same check every other
            // write path runs.
            val anomalyDetected = ClockAnomalyDetector.detect(allEvents, now, elapsedRealtimeMillis)
            val groupId = UUID.randomUUID().toString()
            val targetRemoteId = allEvents.firstOrNull { it.actionGroupId == target }?.remoteId

            val events = buildList {
                add(
                    DomainEvent(
                        eventId = UUID.randomUUID().toString(),
                        actionGroupId = groupId,
                        timestampEpochMillis = now,
                        remoteId = null,
                        batteryId = null,
                        eventType = EventType.UNDO_ACTION,
                        previousBatteryId = null,
                        newBatteryId = null,
                        targetActionGroupId = target,
                        createdByAppVersion = appVersion,
                        wallClockAnomalyDetected = anomalyDetected,
                        elapsedRealtimeMillis = elapsedRealtimeMillis
                    )
                )
                // UNDO_ACTION events are filtered out of the replay stream entirely
                // (EventFiltering), so this warning event is what actually carries the
                // anomaly flag into RuntimeAnalysisEngine and poisons any open interval.
                if (anomalyDetected) add(systemTimeWarningEvent(targetRemoteId, groupId, now, appVersion))
            }

            repository.writeEventGroup(events)
        }
    }
}
