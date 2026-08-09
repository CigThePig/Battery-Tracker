package com.cranebatterytracker.domain.analysis

import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RemoteKnowledge

/**
 * Deterministic reconstruction of "what does each remote currently hold"
 * from the full raw event log. Given the same events, this always produces
 * the same result (spec section 62) - no hidden clock reads, no randomness.
 *
 * Undo and correction handling live in [EventFiltering]; this class only
 * folds the already-filtered chronological sequence into state.
 */
object EventReducer {

    fun reduce(allEvents: List<DomainEvent>): Map<RemoteId, RemoteKnowledge> {
        val state = mutableMapOf<RemoteId, RemoteKnowledge>(
            RemoteId.WEST to RemoteKnowledge.Unknown(null),
            RemoteId.EAST to RemoteKnowledge.Unknown(null)
        )

        for (event in EventFiltering.effectiveChronologicalEvents(allEvents)) {
            apply(state, event)
        }

        return state
    }

    private fun apply(state: MutableMap<RemoteId, RemoteKnowledge>, event: DomainEvent) {
        when (event.eventType) {
            EventType.BATTERY_INSTALLED -> {
                val remoteId = event.remoteId ?: return
                val batteryId = event.newBatteryId ?: event.batteryId ?: return
                state[remoteId] = RemoteKnowledge.Known(
                    batteryId = batteryId,
                    installedAt = event.timestampEpochMillis,
                    confirmedAt = event.timestampEpochMillis
                )
            }

            EventType.BATTERY_REMOVED_DEAD -> {
                // A normal battery change always pairs this with a BATTERY_INSTALLED
                // in the same action group, which supersedes state immediately after.
                // If a removal is ever recorded without a paired install, the remote's
                // identity is no longer known.
                val remoteId = event.remoteId ?: return
                state[remoteId] = RemoteKnowledge.Unknown(event.timestampEpochMillis)
            }

            EventType.STATE_CONFIRMED -> {
                val remoteId = event.remoteId ?: return
                val batteryId = event.batteryId ?: return
                val current = state[remoteId]
                val installedAt = when (current) {
                    is RemoteKnowledge.Known -> if (current.batteryId == batteryId) current.installedAt else event.timestampEpochMillis
                    else -> event.timestampEpochMillis
                }
                state[remoteId] = RemoteKnowledge.Known(
                    batteryId = batteryId,
                    installedAt = installedAt,
                    confirmedAt = event.timestampEpochMillis
                )
            }

            EventType.STATE_CORRECTED -> {
                val remoteId = event.remoteId ?: return
                val batteryId = event.newBatteryId ?: return
                state[remoteId] = RemoteKnowledge.Known(
                    batteryId = batteryId,
                    installedAt = event.timestampEpochMillis,
                    confirmedAt = event.timestampEpochMillis
                )
            }

            EventType.STATE_MARKED_UNKNOWN -> {
                val remoteId = event.remoteId ?: return
                state[remoteId] = RemoteKnowledge.Unknown(event.timestampEpochMillis)
            }

            EventType.UNDO_ACTION, EventType.SYSTEM_TIME_WARNING -> {
                // UNDO_ACTION events are filtered out before folding (they cause their
                // target group to be removed, they don't themselves mutate state).
                // SYSTEM_TIME_WARNING is an annotation only.
            }
        }
    }
}
