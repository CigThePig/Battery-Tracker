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
        assertFalse(cycle.clockAnomalyDetected)
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

    @Test
    fun `a clock jump detected on one remote poisons an interval open on the other remote`() {
        val westStart = millisAt(2024, 1, 1, 6, 0)
        val eastStart = millisAt(2024, 1, 1, 6, 5)
        val anomalyTime = millisAt(2024, 1, 1, 7, 0)
        val eastEnd = millisAt(2024, 1, 1, 10, 0)

        val westInstall = testBatteryChange(westStart, RemoteId.WEST, null, 2)
        val eastInstall = testBatteryChange(eastStart, RemoteId.EAST, null, 5)
        val anomalousWestConfirmation = testEvent(
            timestamp = anomalyTime,
            remoteId = RemoteId.WEST,
            batteryId = 2,
            eventType = EventType.STATE_CONFIRMED
        ).copy(wallClockAnomalyDetected = true)
        val eastClose = testBatteryChange(eastEnd, RemoteId.EAST, 5, 6)

        val cycles = engine.deriveCycles(westInstall + eastInstall + listOf(anomalousWestConfirmation) + eastClose)
        val eastCycle = cycles.single { it.batteryId == 5 }

        // The jump was only *detected* by a West event, but it's the same device clock for
        // both remotes - East's interval, open at the same instant, must be poisoned too.
        assertFalse(eastCycle.classification == RuntimeClassification.EXACT)
        assertFalse(eastCycle.includedInPrimaryStatistics)
    }

    @Test
    fun `openIntervalClockAnomalies reports true for a still-open interval poisoned by a clock jump`() {
        val start = millisAt(2024, 1, 1, 6, 0)
        val anomalyTime = millisAt(2024, 1, 1, 7, 0)
        val install = testBatteryChange(start, RemoteId.WEST, null, 2)
        val anomalousConfirm = testEvent(
            timestamp = anomalyTime,
            remoteId = RemoteId.WEST,
            batteryId = 2,
            eventType = EventType.STATE_CONFIRMED
        ).copy(wallClockAnomalyDetected = true)

        val anomalies = engine.openIntervalClockAnomalies(install + listOf(anomalousConfirm))

        assertTrue(anomalies[RemoteId.WEST] == true)
    }

    @Test
    fun `openIntervalClockAnomalies is false for a clean still-open interval`() {
        val start = millisAt(2024, 1, 1, 6, 0)
        val install = testBatteryChange(start, RemoteId.WEST, null, 2)

        val anomalies = engine.openIntervalClockAnomalies(install)

        assertFalse(anomalies[RemoteId.WEST] == true)
    }

    @Test
    fun `a still-open interval confirmed under a clock anomaly is not presented as a reliable minimum`() {
        val start = millisAt(2024, 1, 1, 6, 0)
        val confirmTime = millisAt(2024, 1, 1, 9, 15)

        val install = testBatteryChange(start, RemoteId.WEST, null, 3)
        val anomalousConfirm = testEvent(
            timestamp = confirmTime,
            remoteId = RemoteId.WEST,
            batteryId = 3,
            eventType = EventType.STATE_CONFIRMED
        ).copy(wallClockAnomalyDetected = true)

        val cycles = engine.deriveCycles(install + listOf(anomalousConfirm))
        val openCycle = cycles.single { it.batteryId == 3 }

        // The confirmation happened, but under a clock known to have jumped - its elapsed
        // time can't be trusted as a lower bound, so this must not read as reliable.
        assertEquals(RuntimeClassification.UNKNOWN, openCycle.classification)
        assertFalse(openCycle.includedInPrimaryStatistics)
    }

    @Test
    fun `a reboot mid-interval downgrades an otherwise-clean cycle from exact to shift-interrupted`() {
        // The tablet rebooted between install and removal - marked on an *intermediate*
        // confirmation, not the install itself, since monotonicContinuityBroken on an
        // event means the gap happened before that event, not after it. The wall clock
        // never jumped (no tampering, no SYSTEM_TIME_WARNING needed), but the monotonic
        // elapsed-time timeline was broken partway through, so the cycle can no longer be
        // certified as EXACT.
        val start = millisAt(2024, 1, 1, 6, 0)
        val rebootTime = millisAt(2024, 1, 1, 8, 0)
        val end = millisAt(2024, 1, 1, 10, 0)
        val install = testBatteryChange(start, RemoteId.WEST, null, 2)
        val rebootMarker = testEvent(
            timestamp = rebootTime,
            remoteId = RemoteId.WEST,
            batteryId = 2,
            eventType = EventType.STATE_CONFIRMED
        ).copy(monotonicContinuityBroken = true)
        val events = install + listOf(rebootMarker) + testBatteryChange(end, RemoteId.WEST, 2, 3)

        val cycle = engine.deriveCycles(events).single { it.batteryId == 2 }

        assertEquals(RuntimeClassification.SHIFT_INTERRUPTED, cycle.classification)
        assertFalse(cycle.includedInPrimaryStatistics)
        // The whole interval sat inside definitely-active time - min == max - so nothing
        // about a shift boundary caused the downgrade; the clock anomaly flag must say so.
        assertEquals(cycle.minimumActiveRuntimeMillis, cycle.maximumActiveRuntimeMillis)
        assertTrue(cycle.clockAnomalyDetected)
    }

    @Test
    fun `an interval opened by the very event that detected the reboot is not tainted by its own start`() {
        // monotonicContinuityBroken on an event means continuity was lost *before* that
        // event - it says nothing about the brand-new interval the same event opens,
        // which runs entirely forward on the post-reboot monotonic clock with no internal
        // gap. Only whatever was already open at that instant (poisoned via the top-of-
        // loop check) should be tainted; the freshly-opened interval must not inherit it.
        val start = millisAt(2024, 1, 1, 6, 0)
        val end = millisAt(2024, 1, 1, 10, 0)
        val rebootInstall = testBatteryChange(start, RemoteId.WEST, null, 2).map {
            if (it.eventType == EventType.BATTERY_INSTALLED) it.copy(monotonicContinuityBroken = true) else it
        }
        val events = rebootInstall + testBatteryChange(end, RemoteId.WEST, 2, 3)

        val cycle = engine.deriveCycles(events).single { it.batteryId == 2 }

        assertEquals(RuntimeClassification.EXACT, cycle.classification)
        assertTrue(cycle.includedInPrimaryStatistics)
    }

    @Test
    fun `a reboot on one remote poisons an interval open on the other remote`() {
        val westStart = millisAt(2024, 1, 1, 6, 0)
        val eastStart = millisAt(2024, 1, 1, 6, 5)
        val rebootTime = millisAt(2024, 1, 1, 7, 0)
        val eastEnd = millisAt(2024, 1, 1, 10, 0)

        val westInstall = testBatteryChange(westStart, RemoteId.WEST, null, 2)
        val eastInstall = testBatteryChange(eastStart, RemoteId.EAST, null, 5)
        val rebootMarker = testEvent(
            timestamp = rebootTime,
            remoteId = RemoteId.WEST,
            batteryId = 2,
            eventType = EventType.STATE_CONFIRMED
        ).copy(monotonicContinuityBroken = true)
        val eastClose = testBatteryChange(eastEnd, RemoteId.EAST, 5, 6)

        val cycles = engine.deriveCycles(westInstall + eastInstall + listOf(rebootMarker) + eastClose)
        val eastCycle = cycles.single { it.batteryId == 5 }

        assertFalse(eastCycle.classification == RuntimeClassification.EXACT)
        assertFalse(eastCycle.includedInPrimaryStatistics)
    }

    @Test
    fun `a fresh interval started entirely after a reboot can still become exact`() {
        // The reboot is stamped on the install that starts battery 2's interval - the
        // interval itself begins after continuity was already lost at its own start point,
        // matching an operator action recorded shortly after the tablet powers back on.
        // A *new* interval, started with no anomaly at all, must not inherit that taint.
        val rebootInstallTime = millisAt(2024, 1, 1, 6, 0)
        val cleanStart = millisAt(2024, 1, 1, 6, 30)
        val cleanEnd = millisAt(2024, 1, 1, 10, 0)

        val rebootInstall = testBatteryChange(rebootInstallTime, RemoteId.WEST, null, 2).map {
            if (it.eventType == EventType.BATTERY_INSTALLED) it.copy(monotonicContinuityBroken = true) else it
        }
        val cleanCycle = testBatteryChange(cleanStart, RemoteId.WEST, 2, 3) + testBatteryChange(cleanEnd, RemoteId.WEST, 3, 4)

        val cycles = engine.deriveCycles(rebootInstall + cleanCycle)
        val freshCycle = cycles.single { it.batteryId == 3 }

        assertEquals(RuntimeClassification.EXACT, freshCycle.classification)
        assertTrue(freshCycle.includedInPrimaryStatistics)
    }
}
