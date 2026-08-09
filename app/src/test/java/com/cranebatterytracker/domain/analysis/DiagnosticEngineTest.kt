package com.cranebatterytracker.domain.analysis

import com.cranebatterytracker.domain.model.Battery
import com.cranebatterytracker.domain.model.DataQualityLevel
import com.cranebatterytracker.domain.model.DerivedCycle
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RemoteWarningLevel
import com.cranebatterytracker.domain.model.RuntimeClassification
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticEngineTest {

    private val engine = DiagnosticEngine()

    private fun exactCycle(batteryId: Int, remoteId: RemoteId, durationMillis: Long, startTimestamp: Long): DerivedCycle =
        DerivedCycle(
            cycleId = UUID.randomUUID().toString(),
            batteryId = batteryId,
            remoteId = remoteId,
            startTimestamp = startTimestamp,
            endTimestamp = startTimestamp + durationMillis,
            minimumActiveRuntimeMillis = durationMillis,
            maximumActiveRuntimeMillis = durationMillis,
            classification = RuntimeClassification.EXACT,
            isHighOutlier = false,
            isShortRuntimeEvent = false,
            includedInPrimaryStatistics = true,
            startEventId = UUID.randomUUID().toString(),
            endEventId = UUID.randomUUID().toString()
        )

    @Test
    fun `few cycles produce a limited data quality label`() {
        val cycles = List(3) { exactCycle(1, RemoteId.WEST, 5 * 3_600_000L, it * 100_000L) }
        val summary = engine.dataQuality(cycles, correctionCount = 0)
        assertEquals(DataQualityLevel.LIMITED, summary.overallLevel)
    }

    @Test
    fun `broad clean coverage produces a strong data quality label`() {
        val cycles = List(70) { exactCycle(1, RemoteId.WEST, 5 * 3_600_000L, it * 100_000L) }
        val summary = engine.dataQuality(cycles, correctionCount = 0)
        assertEquals(DataQualityLevel.STRONG, summary.overallLevel)
    }

    @Test
    fun `many unknown gaps prevent a strong label even with enough cycles`() {
        val exact = List(70) { exactCycle(1, RemoteId.WEST, 5 * 3_600_000L, it * 100_000L) }
        val unknown = List(80) {
            exactCycle(1, RemoteId.WEST, 0, it * 50_000L).copy(classification = RuntimeClassification.UNKNOWN, includedInPrimaryStatistics = false)
        }
        val summary = engine.dataQuality(exact + unknown, correctionCount = 5)
        assertTrue(summary.overallLevel != DataQualityLevel.STRONG)
    }

    private fun battery(id: Int) = Battery(batteryId = id, displayNumber = id, active = true, createdAt = 0, retiredAt = null)

    @Test
    fun `equal remote performance produces no warning`() {
        val batteries = (1..4).map(::battery)
        val cyclesByBattery = batteries.associate { b ->
            val cycles = (0 until 3).flatMap { i ->
                listOf(
                    exactCycle(b.batteryId, RemoteId.WEST, 5 * 3_600_000L, i * 1_000_000L),
                    exactCycle(b.batteryId, RemoteId.EAST, 5 * 3_600_000L, i * 1_000_000L + 500_000L)
                )
            }
            b.batteryId to cycles
        }
        val summary = engine.remoteDiagnostics(batteries, cyclesByBattery)
        assertEquals(RemoteWarningLevel.NONE, summary.warningLevel)
    }

    @Test
    fun `all batteries consistently worse in east triggers a strong warning`() {
        val batteries = (1..4).map(::battery)
        val cyclesByBattery = batteries.associate { b ->
            val cycles = (0 until 3).flatMap { i ->
                listOf(
                    exactCycle(b.batteryId, RemoteId.WEST, 5 * 3_600_000L, i * 1_000_000L),
                    exactCycle(b.batteryId, RemoteId.EAST, 4 * 3_600_000L, i * 1_000_000L + 500_000L)
                )
            }
            b.batteryId to cycles
        }
        val summary = engine.remoteDiagnostics(batteries, cyclesByBattery)
        assertEquals(RemoteWarningLevel.STRONG, summary.warningLevel)
    }

    @Test
    fun `only one battery worse in east does not trigger a fleet-wide warning`() {
        val batteries = (1..4).map(::battery)
        val cyclesByBattery = batteries.associate { b ->
            val eastDuration = if (b.batteryId == 3) 3 * 3_600_000L else 5 * 3_600_000L
            val cycles = (0 until 3).flatMap { i ->
                listOf(
                    exactCycle(b.batteryId, RemoteId.WEST, 5 * 3_600_000L, i * 1_000_000L),
                    exactCycle(b.batteryId, RemoteId.EAST, eastDuration, i * 1_000_000L + 500_000L)
                )
            }
            b.batteryId to cycles
        }
        val summary = engine.remoteDiagnostics(batteries, cyclesByBattery)
        assertTrue(summary.warningLevel != RemoteWarningLevel.STRONG)
    }
}
