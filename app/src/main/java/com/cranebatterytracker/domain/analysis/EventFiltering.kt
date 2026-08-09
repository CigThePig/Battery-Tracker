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
     * All non-undo events that were not logically undone, in a stable
     * chronological order. When two events share a timestamp (a normal
     * battery change writes its removal and installation at the same
     * instant), closing/marking events are ordered before opening events so
     * replay never depends on random UUID tie-breaking - only then does the
     * event id break remaining ties, purely for reproducibility.
     */
    fun effectiveChronologicalEvents(allEvents: List<DomainEvent>): List<DomainEvent> {
        val undoneGroups = undoneActionGroupIds(allEvents)
        return allEvents
            .asSequence()
            .filter { it.eventType != EventType.UNDO_ACTION }
            .filter { it.actionGroupId == null || it.actionGroupId !in undoneGroups }
            .sortedWith(compareBy({ it.timestampEpochMillis }, { typeOrderingPriority(it.eventType) }, { it.eventId }))
            .toList()
    }

    private fun typeOrderingPriority(eventType: EventType): Int = when (eventType) {
        EventType.BATTERY_REMOVED_DEAD, EventType.STATE_MARKED_UNKNOWN, EventType.STATE_CORRECTED -> 0
        EventType.STATE_CONFIRMED -> 1
        EventType.BATTERY_INSTALLED -> 2
        EventType.SYSTEM_TIME_WARNING -> 3
        EventType.UNDO_ACTION -> 4
    }
}
