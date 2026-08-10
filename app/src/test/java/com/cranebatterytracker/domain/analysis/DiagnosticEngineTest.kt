package com.cranebatterytracker.domain.analysis

import com.cranebatterytracker.domain.model.Battery
import com.cranebatterytracker.domain.model.BatteryTrend
import com.cranebatterytracker.domain.model.DataQualityLevel
import com.cranebatterytracker.domain.model.DerivedCycle
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RemoteWarningLevel
import com.cranebatterytracker.domain.model.RuntimeClassification
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticEngineTest {

    private val engine = DiagnosticEngine()

    private fun exactCycle(
        batteryId: Int,
        remoteId: RemoteId,
        durationMillis: Long,
        startTimestamp: Long,
        // Defaults to matching startTimestamp, since most tests construct cycles in
        // increasing order of both anyway; pass a distinct value to simulate a backward
        // clock correction, where insertion order and wall-clock order disagree.
        startSequenceNumber: Long = startTimestamp
    ): DerivedCycle =
        DerivedCycle(
            cycleId = UUID.randomUUID().toString(),
            batteryId = batteryId,
            remoteId = remoteId,
            startTimestamp = startTimestamp,
            startSequenceNumber = startSequenceNumber,
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
    fun `clock-anomaly shift-interrupted cycles are counted separately from ordinary ones`() {
        val ordinary = exactCycle(1, RemoteId.WEST, 5 * 3_600_000L, 0L)
            .copy(classification = RuntimeClassification.SHIFT_INTERRUPTED, includedInPrimaryStatistics = false)
        val anomalous = exactCycle(1, RemoteId.WEST, 5 * 3_600_000L, 100_000L)
            .copy(
                classification = RuntimeClassification.SHIFT_INTERRUPTED,
                includedInPrimaryStatistics = false,
                clockAnomalyDetected = true
            )

        val summary = engine.dataQuality(listOf(ordinary, anomalous), correctionCount = 0)

        assertEquals(2, summary.shiftInterruptedCycles)
        assertEquals(1, summary.clockAnomalyShiftInterruptedCycles)
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

    @Test
    fun `tied conflicting directional evidence is treated as inconclusive, not a directional warning`() {
        val batteries = (1..4).map(::battery)
        // Batteries 1-2 run shorter in East; batteries 3-4 run shorter in West - equally
        // strong evidence in both directions, so neither should win.
        val cyclesByBattery = batteries.associate { b ->
            val westDuration = if (b.batteryId in listOf(3, 4)) 3 * 3_600_000L else 5 * 3_600_000L
            val eastDuration = if (b.batteryId in listOf(1, 2)) 3 * 3_600_000L else 5 * 3_600_000L
            val cycles = (0 until 3).flatMap { i ->
                listOf(
                    exactCycle(b.batteryId, RemoteId.WEST, westDuration, i * 1_000_000L),
                    exactCycle(b.batteryId, RemoteId.EAST, eastDuration, i * 1_000_000L + 500_000L)
                )
            }
            b.batteryId to cycles
        }

        val summary = engine.remoteDiagnostics(batteries, cyclesByBattery)
        assertEquals(RemoteWarningLevel.NONE, summary.warningLevel)
        assertTrue(summary.message.contains("battery-specific", ignoreCase = true))
    }

    @Test
    fun `strong assessment only claims the evidence that actually triggered it`() {
        // Ten identical-duration cycles (no meaningful decline: change is ~0%, nowhere
        // near the -45% strong threshold), but five of the most recent ten are flagged
        // as short-runtime events - enough on its own to reach STRONG_REPLACEMENT_CANDIDATE.
        val cycles = List(10) { i -> exactCycle(1, RemoteId.WEST, 5 * 3_600_000L, i * 1_000_000L) }
            .mapIndexed { index, cycle -> if (index >= 5) cycle.copy(isShortRuntimeEvent = true) else cycle }

        val health = engine.batteryHealth(battery(1), cycles, deadEventCount = 0)

        assertEquals(BatteryTrend.STRONG_REPLACEMENT_CANDIDATE, health.trend)
        assertTrue(health.assessment.contains("short cycles are frequent", ignoreCase = true))
        // Must not claim a severe baseline decline that never happened.
        assertFalse(health.assessment.contains("far below", ignoreCase = true))
    }

    @Test
    fun `a battery that has always been weak relative to its peers is not permanently STABLE`() {
        val batteries = (1..4).map(::battery)
        // Battery 1 has always lasted 3.6h; the other three have always lasted 5.2h. No
        // battery declines from its own baseline, so only a fleet-relative comparison can
        // catch battery 1 - this mirrors the documented replacement-candidate scenario.
        val cyclesByBattery = batteries.associate { b ->
            val duration = if (b.batteryId == 1) (3 * 3_600_000L + 36 * 60_000L) else (5 * 3_600_000L + 12 * 60_000L)
            val cycles = (0 until 6).map { i -> exactCycle(b.batteryId, RemoteId.WEST, duration, i * 1_000_000L) }
            b.batteryId to cycles
        }

        val healths = engine.batteryHealths(batteries, cyclesByBattery, emptyMap())
        val weakBattery = healths.single { it.batteryId == 1 }
        val peerBattery = healths.single { it.batteryId == 2 }

        assertEquals(BatteryTrend.STRONG_REPLACEMENT_CANDIDATE, weakBattery.trend)
        assertTrue(weakBattery.assessment.contains("consistently runs far shorter", ignoreCase = true))
        assertEquals(BatteryTrend.STABLE, peerBattery.trend)
    }

    @Test
    fun `a severe decline is still detected after a backward clock correction reverses wall-clock order`() {
        // Truly first (by insertion sequence): 5 healthy 5-hour cycles.
        val baselineCycles = (1..5).map { seq ->
            exactCycle(1, RemoteId.WEST, 5 * 3_600_000L, startTimestamp = (100 + seq) * 1_000_000L, startSequenceNumber = seq.toLong())
        }
        // Truly later: 10 badly declined 2-hour cycles, but recorded with wall-clock
        // timestamps *before* the baseline cycles above - e.g. the tablet clock was reset
        // backward right before these were written.
        val declinedCycles = (6..15).map { seq ->
            exactCycle(1, RemoteId.WEST, 2 * 3_600_000L, startTimestamp = (seq - 5) * 100_000L, startSequenceNumber = seq.toLong())
        }

        val health = engine.batteryHealth(battery(1), baselineCycles + declinedCycles, deadEventCount = 0)

        // Sorting by startTimestamp would put the declined cycles first (as "baseline")
        // and mix them with the healthy ones in "recent", hiding the decline entirely.
        // Sorting by insertion sequence (correct) puts the healthy cycles in baseline and
        // the declined ones in recent, correctly showing a severe decline.
        assertEquals(BatteryTrend.STRONG_REPLACEMENT_CANDIDATE, health.trend)
        assertTrue(health.recentChangePercent != null && health.recentChangePercent!! <= -45.0)
    }

    @Test
    fun `a healthy battery used mostly in a faster-draining remote is not falsely condemned`() {
        val batteries = (1..4).map(::battery)
        // Batteries 1, 2 and 4 are used in both remotes and genuinely run 20 percent
        // shorter in East than in West - an ordinary remote-specific effect, not a
        // battery problem. Battery 3 happens to almost always be used in East, so its
        // lifetime median (mixing in the fast-draining remote) would look weak against a
        // flat, unstratified fleet median even though it matches its East peers exactly.
        val cyclesByBattery = mutableMapOf<Int, List<DerivedCycle>>()
        for (id in listOf(1, 2, 4)) {
            cyclesByBattery[id] = (0 until 5).map { i -> exactCycle(id, RemoteId.WEST, 5 * 3_600_000L, i * 1_000_000L) } +
                (0 until 5).map { i -> exactCycle(id, RemoteId.EAST, 4 * 3_600_000L, i * 1_000_000L + 500_000L) }
        }
        cyclesByBattery[3] = (0 until 5).map { i -> exactCycle(3, RemoteId.EAST, 4 * 3_600_000L, i * 1_000_000L) }

        val healths = engine.batteryHealths(batteries, cyclesByBattery, emptyMap())
        val eastHeavyBattery = healths.single { it.batteryId == 3 }

        assertTrue(
            "Expected battery 3 not to be flagged as a replacement candidate, was ${eastHeavyBattery.trend}",
            eastHeavyBattery.trend != BatteryTrend.STRONG_REPLACEMENT_CANDIDATE
        )
    }

    @Test
    fun `a battery weak in both remotes is still detected despite per-remote normalization`() {
        val batteries = (1..4).map(::battery)
        val cyclesByBattery = mutableMapOf<Int, List<DerivedCycle>>()
        for (id in listOf(1, 2, 4)) {
            cyclesByBattery[id] = (0 until 5).map { i -> exactCycle(id, RemoteId.WEST, 5 * 3_600_000L, i * 1_000_000L) } +
                (0 until 5).map { i -> exactCycle(id, RemoteId.EAST, 4 * 3_600_000L, i * 1_000_000L + 500_000L) }
        }
        // Battery 3 is used evenly across both remotes but runs 30 percent shorter than
        // its peers in each one - a genuine battery-specific weakness that must survive
        // stratifying the comparison by remote, not disappear into remote-specific noise.
        cyclesByBattery[3] = (0 until 5).map { i -> exactCycle(3, RemoteId.WEST, 3 * 3_600_000L + 30 * 60_000L, i * 1_000_000L) } +
            (0 until 5).map { i -> exactCycle(3, RemoteId.EAST, 2 * 3_600_000L + 48 * 60_000L, i * 1_000_000L + 500_000L) }

        val healths = engine.batteryHealths(batteries, cyclesByBattery, emptyMap())
        val weakBattery = healths.single { it.batteryId == 3 }

        assertEquals(BatteryTrend.STRONG_REPLACEMENT_CANDIDATE, weakBattery.trend)
    }

    @Test
    fun `confirmed-minimum observations are reported separately from unknown gaps`() {
        val exact = List(20) { exactCycle(1, RemoteId.WEST, 5 * 3_600_000L, it * 100_000L) }
        val confirmedMinimum = List(5) {
            exactCycle(1, RemoteId.WEST, 4 * 3_600_000L, (20 + it) * 100_000L)
                .copy(classification = RuntimeClassification.CONFIRMED_MINIMUM, includedInPrimaryStatistics = false)
        }
        val unknown = List(2) {
            exactCycle(1, RemoteId.WEST, 0, (30 + it) * 100_000L)
                .copy(classification = RuntimeClassification.UNKNOWN, includedInPrimaryStatistics = false)
        }

        val summary = engine.dataQuality(exact + confirmedMinimum + unknown, correctionCount = 0)

        assertEquals(5, summary.confirmedMinimumObservations)
        assertEquals(2, summary.unknownGaps)
    }
}
