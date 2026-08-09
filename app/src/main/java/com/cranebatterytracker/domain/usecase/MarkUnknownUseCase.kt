package com.cranebatterytracker.domain.usecase

import com.cranebatterytracker.domain.analysis.ClockAnomalyDetector
import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.repository.BatteryTrackerRepository
import java.util.UUID

/** "I DON'T KNOW" - explicit, first-class uncertainty (spec section 20). */
class MarkUnknownUseCase(
    private val repository: BatteryTrackerRepository,
    private val appVersion: String
) {
    suspend operator fun invoke(
        remoteId: RemoteId,
        now: Long = System.currentTimeMillis(),
        elapsedRealtimeMillis: Long = now
    ) {
        repository.inTransaction {
            val priorEvents = repository.currentEventsSnapshot()
            val anomalyDetected = ClockAnomalyDetector.detect(priorEvents, now, elapsedRealtimeMillis)
            val groupId = UUID.randomUUID().toString()

            repository.writeEventGroup(
                buildList {
                    add(
                        DomainEvent(
                            eventId = UUID.randomUUID().toString(),
                            actionGroupId = groupId,
                            timestampEpochMillis = now,
                            remoteId = remoteId,
                            batteryId = null,
                            eventType = EventType.STATE_MARKED_UNKNOWN,
                            previousBatteryId = null,
                            newBatteryId = null,
                            targetActionGroupId = null,
                            createdByAppVersion = appVersion,
                            wallClockAnomalyDetected = anomalyDetected,
                            elapsedRealtimeMillis = elapsedRealtimeMillis
                        )
                    )
                    if (anomalyDetected) add(systemTimeWarningEvent(remoteId, groupId, now, appVersion))
                }
            )
        }
    }
}
