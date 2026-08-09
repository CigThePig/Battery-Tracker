package com.cranebatterytracker.domain.analysis

import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RuntimeClassification
import com.cranebatterytracker.testutil.testBatteryChange
import com.cranebatterytracker.testutil.testEvent
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeAnalysisEngineTest {

    private val zone = ZoneOffset.UTC
    private val shiftEngine = ShiftEngine(ShiftSchedule(), zone)
    private val engine = RuntimeAnalysisEngine(shiftEngine)

    private fun millisAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    /**
     * One clean, same-day EXACT cycle (05:00 -> 05:00+[durationMillis], both within the
     * day shift's definitely-active window) per entry in [durationsMillis], all for
     * [measuredBatteryId]. Each day's clean cycle is bridged to the next by an overnight
     * placeholder interval on [placeholderBatteryId], which always crosses downtime and
     * is therefore SHIFT_INTERRUPTED - present in the log but irrelevant to this test.
     */
    private fun buildDailyCycles(durationsMillis: List<Long>, measuredBatteryId: Int, placeholderBatteryId: Int): List<DomainEvent> {
        val events = mutableListOf<DomainEvent>()
        var previous: Int? = null
        // 2024-01-01 is a Monday; stepping by 7 days keeps every cycle on the same
        // weekday so none of them accidentally land on a non-working Saturday/Sunday.
        var date = LocalDate.of(2024, 1, 1)
        for (duration in durationsMillis) {
            val dayStart = date.atTime(5, 0).atZone(zone).toInstant().toEpochMilli()
            val dayEnd = dayStart + duration
            events += testBatteryChange(dayStart, RemoteId.WEST, previous, measuredBatteryId)
            events += testBatteryChange(dayEnd, RemoteId.WEST, measuredBatteryId, placeholderBatteryId)
            previous = placeholderBatteryId
            date = date.plusWeeks(1)
        }
        return events
    }

    private val fiveHoursMillis = 5 * 60 * 60 * 1000L
    private val eightHoursMillis = 8 * 60 * 60 * 1000L

    @Test
    fun `install then remove within a shift yields an exact cycle`() {
        val start = millisAt(2024, 1, 1, 6, 0)
        val end = millisAt(2024, 1, 1, 10, 0)
        val events = testBatteryChange(start, RemoteId.WEST, null, 2) +
            testBatteryChange(end, RemoteId.WEST, 2, 3)

        val cycles = engine.deriveCycles(events)
        val cycle = cycles.single { it.batteryId == 2 }
        assertEquals(RuntimeClassification.EXACT, cycle.classification)
        assertEquals(end - start, cycle.minimumActiveRuntimeMillis)
        assertTrue(cycle.includedInPrimaryStatistics)
    }

    @Test
    fun `cycle crossing scheduled downtime is shift-interrupted with a min-max range`() {
        val start = millisAt(2024, 1, 1, 12, 30)
        val end = millisAt(2024, 1, 2, 5, 45)
        val events = testBatteryChange(start, RemoteId.WEST, null, 2) +
            testBatteryChange(end, RemoteId.WEST, 2, 3)

        val cycles = engine.deriveCycles(events)
        val cycle = cycles.single { it.batteryId == 2 }
        assertEquals(RuntimeClassification.SHIFT_INTERRUPTED, cycle.classification)
        assertTrue(cycle.minimumActiveRuntimeMillis < cycle.maximumActiveRuntimeMillis)
        assertFalse(cycle.includedInPrimaryStatistics)
    }

    @Test
    fun `unknown gap produces no exact runtime when a correction follows with no confirmation`() {
        val installTime = millisAt(2024, 1, 1, 6, 0)
        val correctionTime = millisAt(2024, 1, 1, 17, 0)
        val events = testBatteryChange(installTime, RemoteId.WEST, null, 1) +
            listOf(
                testEvent(
                    timestamp = correctionTime,
                    remoteId = RemoteId.WEST,
                    batteryId = 4,
                    eventType = EventType.STATE_CORRECTED,
                    previousBatteryId = 1,
                    newBatteryId = 4
                )
            )

        val cycles = engine.deriveCycles(events)
        val batteryOneCycle = cycles.single { it.batteryId == 1 }
        assertEquals(RuntimeClassification.UNKNOWN, batteryOneCycle.classification)
        assertFalse(batteryOneCycle.includedInPrimaryStatistics)
    }

    @Test
    fun `confirmation before a correction yields a confirmed minimum`() {
        val installTime = millisAt(2024, 1, 1, 6, 0)
        val confirmTime = millisAt(2024, 1, 1, 9, 15)
        val correctionTime = millisAt(2024, 1, 1, 17, 0)
        val events = testBatteryChange(installTime, RemoteId.WEST, null, 3) +
            listOf(
                testEvent(timestamp = confirmTime, remoteId = RemoteId.WEST, batteryId = 3, eventType = EventType.STATE_CONFIRMED),
                testEvent(
                    timestamp = correctionTime,
                    remoteId = RemoteId.WEST,
                    batteryId = 4,
                    eventType = EventType.STATE_CORRECTED,
                    previousBatteryId = 3,
                    newBatteryId = 4
                )
            )

        val cycles = engine.deriveCycles(events)
        val batteryThreeCycle = cycles.single { it.batteryId == 3 }
        assertEquals(RuntimeClassification.CONFIRMED_MINIMUM, batteryThreeCycle.classification)
        assertEquals(confirmTime - installTime, batteryThreeCycle.minimumActiveRuntimeMillis)
        assertFalse(batteryThreeCycle.includedInPrimaryStatistics)
    }

    @Test
    fun `no outlier classification before enough reliable samples`() {
        val durations = List(3) { fiveHoursMillis } + eightHoursMillis
        val events = buildDailyCycles(durations, measuredBatteryId = 1, placeholderBatteryId = 2)
        val cycles = engine.deriveCycles(events)
        assertTrue(cycles.none { it.isHighOutlier })
    }

    @Test
    fun `a high cycle after a stable baseline is flagged and excluded from primary statistics`() {
        val durations = List(6) { fiveHoursMillis } + eightHoursMillis
        val events = buildDailyCycles(durations, measuredBatteryId = 1, placeholderBatteryId = 2)
        val batteryOneCycles = engine.deriveCycles(events).filter { it.batteryId == 1 && it.classification == RuntimeClassification.EXACT }

        val outliers = batteryOneCycles.filter { it.isHighOutlier }
        assertEquals(1, outliers.size)
        assertFalse(outliers.first().includedInPrimaryStatistics)
        // The raw cycle is preserved regardless of statistical classification.
        assertTrue(batteryOneCycles.contains(outliers.first()))
    }

    @Test
    fun `short runtimes are preserved and flagged, not converted to unknown`() {
        val oneHourMillis = 60 * 60 * 1000L
        val durations = List(5) { fiveHoursMillis } + oneHourMillis
        val events = buildDailyCycles(durations, measuredBatteryId = 1, placeholderBatteryId = 2)
        val batteryOneCycles = engine.deriveCycles(events).filter { it.batteryId == 1 && it.classification == RuntimeClassification.EXACT }

        val shortCycle = batteryOneCycles.single { it.minimumActiveRuntimeMillis == oneHourMillis }
        assertTrue(shortCycle.isShortRuntimeEvent)
        assertTrue(shortCycle.includedInPrimaryStatistics)
    }

    @Test
    fun `re-deriving the same event log produces the same cycle ids`() {
        val start = millisAt(2024, 1, 1, 6, 0)
        val end = millisAt(2024, 1, 1, 10, 0)
        val events = testBatteryChange(start, RemoteId.WEST, null, 2) +
            testBatteryChange(end, RemoteId.WEST, 2, 3)

        val first = engine.deriveCycles(events).single { it.batteryId == 2 }
        val second = engine.deriveCycles(events).single { it.batteryId == 2 }

        assertEquals(first.cycleId, second.cycleId)
    }

    @Test
    fun `a clock anomaly on either endpoint excludes the cycle from exact statistics`() {
        val start = millisAt(2024, 1, 1, 6, 0)
        val end = millisAt(2024, 1, 1, 10, 0)
        val installEvent = testBatteryChange(start, RemoteId.WEST, null, 2).map {
            if (it.eventType == EventType.BATTERY_INSTALLED) it.copy(wallClockAnomalyDetected = true) else it
        }
        val events = installEvent + testBatteryChange(end, RemoteId.WEST, 2, 3)

        val cycle = engine.deriveCycles(events).single { it.batteryId == 2 }
        assertFalse(cycle.classification == RuntimeClassification.EXACT)
        assertFalse(cycle.includedInPrimaryStatistics)
    }

    @Test
    fun `a clock anomaly on an intermediate confirmation also excludes the cycle from exact statistics`() {
        val start = millisAt(2024, 1, 1, 6, 0)
        val confirmTime = millisAt(2024, 1, 1, 8, 0)
        val end = millisAt(2024, 1, 1, 10, 0)

        val install = testBatteryChange(start, RemoteId.WEST, null, 2)
        val anomalousConfirmation = testEvent(
            timestamp = confirmTime,
            remoteId = RemoteId.WEST,
            batteryId = 2,
            eventType = EventType.STATE_CONFIRMED
        ).copy(wallClockAnomalyDetected = true)
        val removeAndInstallNext = testBatteryChange(end, RemoteId.WEST, 2, 3)

        val cycle = engine.deriveCycles(install + listOf(anomalousConfirmation) + removeAndInstallNext).single { it.batteryId == 2 }
        assertFalse(cycle.classification == RuntimeClassification.EXACT)
        assertFalse(cycle.includedInPrimaryStatistics)
    }

    @Test
    fun `a still-open interval that was confirmed produces a confirmed minimum`() {
        val start = millisAt(2024, 1, 1, 6, 0)
        val confirmTime = millisAt(2024, 1, 1, 9, 15)

        val install = testBatteryChange(start, RemoteId.WEST, null, 3)
        val confirm = testEvent(timestamp = confirmTime, remoteId = RemoteId.WEST, batteryId = 3, eventType = EventType.STATE_CONFIRMED)

        val cycles = engine.deriveCycles(install + listOf(confirm))
        val openCycle = cycles.single { it.batteryId == 3 }
        assertEquals(RuntimeClassification.CONFIRMED_MINIMUM, openCycle.classification)
        assertEquals(confirmTime - start, openCycle.minimumActiveRuntimeMillis)
        assertFalse(openCycle.includedInPrimaryStatistics)
    }

    @Test
    fun `a still-open interval with no confirmation produces no cycle at all`() {
        val start = millisAt(2024, 1, 1, 6, 0)
        val install = testBatteryChange(start, RemoteId.WEST, null, 3)

        val cycles = engine.deriveCycles(install)
        assertTrue(cycles.none { it.batteryId == 3 })
    }
}
