package com.cranebatterytracker.ui.feedback

import com.cranebatterytracker.domain.model.DataQualityLevel
import com.cranebatterytracker.domain.model.DerivedCycle
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RuntimeClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceFeedbackFactoryTest {

    @Test
    fun `exact completion explains the runtime and celebrates first reliable run`() {
        val completed = cycle(id = "new", classification = RuntimeClassification.EXACT, included = true)

        val feedback = create(before = emptyList(), after = listOf(completed))

        assertEquals(EvidenceFeedbackKind.RELIABLE_RUN, feedback.kind)
        assertEquals(4 * 60 * 60 * 1_000L, feedback.runtimeMillis)
        assertEquals(1, feedback.reliableCycleCount)
        assertEquals(EvidenceMilestone.FirstReliableRun(2), feedback.milestone)
    }

    @Test
    fun `shift interrupted completion is described as useful partial evidence`() {
        val completed = cycle(
            id = "partial",
            classification = RuntimeClassification.SHIFT_INTERRUPTED,
            included = false,
            minimum = 3 * 60 * 60 * 1_000L,
            maximum = 4 * 60 * 60 * 1_000L
        )

        val feedback = create(before = emptyList(), after = listOf(completed))

        assertEquals(EvidenceFeedbackKind.PARTIAL_RUN, feedback.kind)
        assertEquals(3 * 60 * 60 * 1_000L, feedback.minimumRuntimeMillis)
        assertEquals(4 * 60 * 60 * 1_000L, feedback.maximumRuntimeMillis)
        assertEquals(0, feedback.reliableCycleCount)
        assertNull(feedback.milestone)
    }

    @Test
    fun `fifth reliable run establishes a battery baseline`() {
        val before = (1..4).map { cycle(id = "old-$it", classification = RuntimeClassification.EXACT, included = true) }
        val after = before + cycle(id = "new", classification = RuntimeClassification.EXACT, included = true)

        val feedback = create(before = before, after = after)

        assertEquals(5, feedback.reliableCycleCount)
        assertEquals(EvidenceMilestone.BaselineEstablished(2), feedback.milestone)
    }

    @Test
    fun `outlier is preserved for review without calling it reliable`() {
        val outlier = cycle(id = "new", classification = RuntimeClassification.EXACT, included = false, highOutlier = true)

        val feedback = create(before = emptyList(), after = listOf(outlier))

        assertEquals(EvidenceFeedbackKind.RUN_HELD_FOR_REVIEW, feedback.kind)
        assertEquals(0, feedback.reliableCycleCount)
        assertNull(feedback.milestone)
    }

    @Test
    fun `setting a previously unknown remote only promises that tracking started`() {
        val feedback = EvidenceFeedbackFactory.create(
            remoteId = RemoteId.WEST,
            remoteShortName = "West",
            fromBatteryId = null,
            fromDisplayNumber = null,
            toDisplayNumber = 4,
            beforeCycles = emptyList(),
            afterCycles = emptyList(),
            beforeQuality = DataQualityLevel.LIMITED,
            afterQuality = DataQualityLevel.LIMITED
        )

        assertEquals(EvidenceFeedbackKind.TRACKING_STARTED, feedback.kind)
        assertNull(feedback.runtimeMillis)
        assertTrue(feedback.milestone == null)
    }

    private fun create(before: List<DerivedCycle>, after: List<DerivedCycle>) = EvidenceFeedbackFactory.create(
        remoteId = RemoteId.WEST,
        remoteShortName = "West",
        fromBatteryId = 2,
        fromDisplayNumber = 2,
        toDisplayNumber = 4,
        beforeCycles = before,
        afterCycles = after,
        beforeQuality = DataQualityLevel.LIMITED,
        afterQuality = DataQualityLevel.LIMITED
    )

    private fun cycle(
        id: String,
        classification: RuntimeClassification,
        included: Boolean,
        minimum: Long = 4 * 60 * 60 * 1_000L,
        maximum: Long = minimum,
        highOutlier: Boolean = false
    ) = DerivedCycle(
        cycleId = id,
        batteryId = 2,
        remoteId = RemoteId.WEST,
        startTimestamp = 1_000L,
        startSequenceNumber = id.hashCode().toLong(),
        endTimestamp = 2_000L,
        minimumActiveRuntimeMillis = minimum,
        maximumActiveRuntimeMillis = maximum,
        classification = classification,
        isHighOutlier = highOutlier,
        isShortRuntimeEvent = false,
        includedInPrimaryStatistics = included,
        startEventId = "start-$id",
        endEventId = "end-$id"
    )
}
