package com.cranebatterytracker.ui.feedback

import com.cranebatterytracker.domain.model.DataQualityLevel
import com.cranebatterytracker.domain.model.DerivedCycle
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RuntimeClassification

/**
 * Human-facing receipt for a battery change. The event log remains the source of truth;
 * this model only explains what the newly-added evidence actually contributed.
 */
data class BatteryChangeFeedback(
    val remoteId: RemoteId,
    val remoteShortName: String,
    val fromDisplayNumber: Int?,
    val toDisplayNumber: Int,
    val kind: EvidenceFeedbackKind,
    val runtimeMillis: Long? = null,
    val minimumRuntimeMillis: Long? = null,
    val maximumRuntimeMillis: Long? = null,
    val batteryDisplayNumber: Int? = null,
    val reliableCycleCount: Int = 0,
    val milestone: EvidenceMilestone? = null
) {
    val isMilestone: Boolean get() = milestone != null
}

enum class EvidenceFeedbackKind {
    TRACKING_STARTED,
    RELIABLE_RUN,
    PARTIAL_RUN,
    RUN_HELD_FOR_REVIEW
}

sealed interface EvidenceMilestone {
    data class FirstReliableRun(val batteryDisplayNumber: Int) : EvidenceMilestone
    data class BaselineEstablished(val batteryDisplayNumber: Int) : EvidenceMilestone
    data class QualityImproved(val level: DataQualityLevel) : EvidenceMilestone
}

/** Pure transformation so the UI can never celebrate evidence more strongly than its classification allows. */
object EvidenceFeedbackFactory {
    const val BASELINE_CYCLE_TARGET = 5

    fun create(
        remoteId: RemoteId,
        remoteShortName: String,
        fromBatteryId: Int?,
        fromDisplayNumber: Int?,
        toDisplayNumber: Int,
        beforeCycles: List<DerivedCycle>,
        afterCycles: List<DerivedCycle>,
        beforeQuality: DataQualityLevel,
        afterQuality: DataQualityLevel
    ): BatteryChangeFeedback {
        if (fromBatteryId == null || fromDisplayNumber == null) {
            return BatteryChangeFeedback(
                remoteId = remoteId,
                remoteShortName = remoteShortName,
                fromDisplayNumber = null,
                toDisplayNumber = toDisplayNumber,
                kind = EvidenceFeedbackKind.TRACKING_STARTED
            )
        }

        val priorCycleIds = beforeCycles.mapTo(mutableSetOf(), DerivedCycle::cycleId)
        val completedCycle = afterCycles
            .asSequence()
            .filter { it.batteryId == fromBatteryId && it.cycleId !in priorCycleIds }
            .filter { it.endEventId != null }
            .maxByOrNull(DerivedCycle::startSequenceNumber)

        val beforeReliableCount = beforeCycles.count {
            it.batteryId == fromBatteryId && it.includedInPrimaryStatistics
        }
        val afterReliableCount = afterCycles.count {
            it.batteryId == fromBatteryId && it.includedInPrimaryStatistics
        }

        val milestone = when {
            qualityRank(afterQuality) > qualityRank(beforeQuality) -> EvidenceMilestone.QualityImproved(afterQuality)
            beforeReliableCount < BASELINE_CYCLE_TARGET && afterReliableCount >= BASELINE_CYCLE_TARGET ->
                EvidenceMilestone.BaselineEstablished(fromDisplayNumber)
            beforeReliableCount == 0 && afterReliableCount == 1 ->
                EvidenceMilestone.FirstReliableRun(fromDisplayNumber)
            else -> null
        }

        val kind = when {
            completedCycle == null -> EvidenceFeedbackKind.TRACKING_STARTED
            completedCycle.isHighOutlier -> EvidenceFeedbackKind.RUN_HELD_FOR_REVIEW
            completedCycle.classification == RuntimeClassification.EXACT -> EvidenceFeedbackKind.RELIABLE_RUN
            else -> EvidenceFeedbackKind.PARTIAL_RUN
        }

        return BatteryChangeFeedback(
            remoteId = remoteId,
            remoteShortName = remoteShortName,
            fromDisplayNumber = fromDisplayNumber,
            toDisplayNumber = toDisplayNumber,
            kind = kind,
            runtimeMillis = completedCycle?.takeIf {
                it.classification == RuntimeClassification.EXACT
            }?.minimumActiveRuntimeMillis,
            minimumRuntimeMillis = completedCycle?.minimumActiveRuntimeMillis,
            maximumRuntimeMillis = completedCycle?.maximumActiveRuntimeMillis,
            batteryDisplayNumber = fromDisplayNumber,
            reliableCycleCount = afterReliableCount,
            milestone = milestone
        )
    }

    private fun qualityRank(level: DataQualityLevel): Int = when (level) {
        DataQualityLevel.LIMITED -> 0
        DataQualityLevel.DEVELOPING -> 1
        DataQualityLevel.GOOD -> 2
        DataQualityLevel.STRONG -> 3
    }
}
