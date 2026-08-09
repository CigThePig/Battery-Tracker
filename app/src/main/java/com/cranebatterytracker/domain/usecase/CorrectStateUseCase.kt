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
        elapsedRealtimeMillis: Long = now,
        resolveCollision: Boolean = false
    ) {
        repository.inTransaction {
            val priorEvents = repository.currentEventsSnapshot()
            val knowledge = EventReducer.reduce(priorEvents)
            val previousBatteryId = (knowledge[remoteId] as? RemoteKnowledge.Known)?.batteryId
            val anomalyDetected = ClockAnomalyDetector.detect(priorEvents, now, elapsedRealtimeMillis)
            val monotonicContinuityBroken = ClockAnomalyDetector.monotonicContinuityLost(priorEvents, elapsedRealtimeMillis)

            if (previousBatteryId == newBatteryId) {
                // The operator is confirming the battery the tablet already shows here,
                // not correcting it. Treating this as STATE_CORRECTED would discard an
                // otherwise trustworthy open interval for no reason - refresh confidence
                // instead, exactly like ConfirmStateUseCase.
                val groupId = UUID.randomUUID().toString()
                repository.writeEventGroup(
                    buildList {
                        if (anomalyDetected) {
                            add(systemTimeWarningEvent(remoteId, groupId, now, appVersion))
                        } else if (monotonicContinuityBroken) {
                            add(
                                systemTimeWarningEvent(
                                    remoteId,
                                    groupId,
                                    now,
                                    appVersion,
                                    wallClockAnomalyDetected = false,
                                    monotonicContinuityBroken = true
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
                                eventType = EventType.STATE_CONFIRMED,
                                previousBatteryId = newBatteryId,
                                newBatteryId = newBatteryId,
                                targetActionGroupId = null,
                                createdByAppVersion = appVersion,
                                wallClockAnomalyDetected = anomalyDetected,
                                elapsedRealtimeMillis = elapsedRealtimeMillis,
                                monotonicContinuityBroken = monotonicContinuityBroken
                            )
                        )
                    }
                )
                return@inTransaction
            }

            val otherRemote = remoteId.other()
            val otherKnowledge = knowledge[otherRemote]
            val collision = otherKnowledge is RemoteKnowledge.Known && otherKnowledge.batteryId == newBatteryId
            if (collision && !resolveCollision) {
                throw BatteryTrackerException.BatteryOwnedByOtherRemote(otherRemote, newBatteryId)
            }

            val groupId = UUID.randomUUID().toString()

            val events = buildList {
                // Written first - see BatteryChangeUseCase for why a monotonic-only break
                // needs its own group-independent marker ordered ahead of anything that
                // might open a new interval.
                if (anomalyDetected) {
                    add(systemTimeWarningEvent(remoteId, groupId, now, appVersion))
                } else if (monotonicContinuityBroken) {
                    add(
                        systemTimeWarningEvent(
                            remoteId,
                            groupId,
                            now,
                            appVersion,
                            wallClockAnomalyDetected = false,
                            monotonicContinuityBroken = true
                        )
                    )
                }
                // Also written before STATE_CORRECTED, not after: this event only closes
                // the other remote's interval, but it still carries the same continuity
                // flag, and RuntimeAnalysisEngine poisons whatever is open at the moment it
                // processes any flagged event - if this ran after STATE_CORRECTED, it would
                // retroactively taint the interval STATE_CORRECTED just opened on this
                // remote, even though that interval starts entirely after the reboot.
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
                            wallClockAnomalyDetected = anomalyDetected,
                            elapsedRealtimeMillis = elapsedRealtimeMillis,
                            monotonicContinuityBroken = monotonicContinuityBroken
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
                        eventType = EventType.STATE_CORRECTED,
                        previousBatteryId = previousBatteryId,
                        newBatteryId = newBatteryId,
                        targetActionGroupId = null,
                        createdByAppVersion = appVersion,
                        wallClockAnomalyDetected = anomalyDetected,
                        elapsedRealtimeMillis = elapsedRealtimeMillis,
                        monotonicContinuityBroken = monotonicContinuityBroken
                    )
                )
            }

            repository.writeEventGroup(events)
        }
    }
}
