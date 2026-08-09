package com.cranebatterytracker.domain.analysis

import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType

/**
 * Shared logic for turning the raw, append-only event log into the
 * chronological sequence that actually governs current state: undone
 * action groups are removed entirely, as if they never happened (spec
 * section 62). Both [EventReducer] and [RuntimeAnalysisEngine] must use
 * this exact same filter so their views of history never disagree.
 */
object EventFiltering {

    fun undoneActionGroupIds(allEvents: List<DomainEvent>): Set<String> =
        allEvents
            .asSequence()
            .filter { it.eventType == EventType.UNDO_ACTION }
            .mapNotNull { it.targetActionGroupId }
            .toSet()

    /**
     * All non-undo events that were not logically undone, in the order they were
     * actually recorded. This replays by [DomainEvent.sequenceNumber] - the
     * database's insertion order - rather than [DomainEvent.timestampEpochMillis]:
     * an operator correcting the tablet's clock backward must never cause older
     * wall-clock timestamps written later to replay ahead of what was already
     * recorded (spec section 67). Event id is a last-resort tie-breaker that
     * should never actually be needed once every persisted event has a unique
     * sequence number.
     */
    fun effectiveChronologicalEvents(allEvents: List<DomainEvent>): List<DomainEvent> {
        val undoneGroups = undoneActionGroupIds(allEvents)
        return allEvents
            .asSequence()
            .filter { it.eventType != EventType.UNDO_ACTION }
            .filter { it.actionGroupId == null || it.actionGroupId !in undoneGroups }
            .sortedWith(compareBy({ it.sequenceNumber }, { it.eventId }))
            .toList()
    }
}
