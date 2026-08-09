package com.cranebatterytracker.ui.diagnostics

import com.cranebatterytracker.domain.model.DerivedCycle
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RuntimeClassification
import org.junit.Assert.assertEquals
import org.junit.Test

class CycleRuntimeLabelTest {

    private fun cycle(
        classification: RuntimeClassification,
        minimumMillis: Long,
        maximumMillis: Long = minimumMillis
    ) = DerivedCycle(
        cycleId = "c",
        batteryId = 1,
        remoteId = RemoteId.WEST,
        startTimestamp = 0,
        startSequenceNumber = 0,
        endTimestamp = null,
        minimumActiveRuntimeMillis = minimumMillis,
        maximumActiveRuntimeMillis = maximumMillis,
        classification = classification,
        isHighOutlier = false,
        isShortRuntimeEvent = false,
        includedInPrimaryStatistics = false,
        startEventId = "s",
        endEventId = null
    )

    @Test
    fun `EXACT shows a single duration`() {
        val label = cycleRuntimeLabel(cycle(RuntimeClassification.EXACT, 3 * 3_600_000L + 15 * 60_000L))
        assertEquals("3h 15m", label)
    }

    @Test
    fun `SHIFT_INTERRUPTED shows a bounded active-runtime range`() {
        val label = cycleRuntimeLabel(
            cycle(RuntimeClassification.SHIFT_INTERRUPTED, minimumMillis = 4 * 3_600_000L + 10 * 60_000L, maximumMillis = 5 * 3_600_000L + 5 * 60_000L)
        )
        assertEquals("4h 10m – 5h 5m active runtime", label)
    }

    @Test
    fun `CONFIRMED_MINIMUM is presented as a lower bound, never a range`() {
        val label = cycleRuntimeLabel(cycle(RuntimeClassification.CONFIRMED_MINIMUM, 3 * 3_600_000L + 15 * 60_000L))
        assertEquals("At least 3h 15m", label)
    }

    @Test
    fun `UNKNOWN never claims a duration`() {
        val label = cycleRuntimeLabel(cycle(RuntimeClassification.UNKNOWN, 0))
        assertEquals("Runtime unknown", label)
    }
}
