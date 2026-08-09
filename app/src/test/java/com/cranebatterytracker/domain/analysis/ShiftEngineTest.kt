package com.cranebatterytracker.domain.analysis

import com.cranebatterytracker.domain.model.ShiftActivityLevel
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftEngineTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val engine = ShiftEngine(ShiftSchedule(), zone)

    private fun millisAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `day shift exact cycle is fully within definitely active window`() {
        // Monday 2024-01-01
        val start = millisAt(2024, 1, 1, 6, 0)
        val end = millisAt(2024, 1, 1, 10, 0)
        assertTrue(engine.isFullyWithinDefinitelyActive(start, end))
    }

    @Test
    fun `night shift crossing midnight stays exact when inside active window`() {
        val start = millisAt(2024, 1, 1, 19, 0)
        val end = millisAt(2024, 1, 2, 0, 30)
        assertTrue(engine.isFullyWithinDefinitelyActive(start, end))
    }

    @Test
    fun `cycle crossing scheduled downtime is not exact`() {
        val start = millisAt(2024, 1, 1, 12, 30)
        val end = millisAt(2024, 1, 2, 5, 45)
        assertFalse(engine.isFullyWithinDefinitelyActive(start, end))
        val (minimum, maximum) = engine.activeRuntimeRange(start, end)
        assertTrue(minimum < maximum)
        assertTrue(minimum < (end - start))
    }

    @Test
    fun `friday night into saturday only counts friday night active time`() {
        // Friday 2024-01-05
        val start = millisAt(2024, 1, 5, 18, 0)
        val end = millisAt(2024, 1, 6, 12, 0) // Saturday noon, no Saturday shift
        val (_, maximum) = engine.activeRuntimeRange(start, end)
        // Friday night active+ambiguous window is at most ~9h30m (17:00->02:30)
        assertTrue(maximum <= 10 * 60 * 60 * 1000L)
    }

    @Test
    fun `weekend gap contributes no active time`() {
        // Saturday 2024-01-06 to Sunday 2024-01-07
        val start = millisAt(2024, 1, 6, 8, 0)
        val end = millisAt(2024, 1, 7, 8, 0)
        val (minimum, maximum) = engine.activeRuntimeRange(start, end)
        assertEquals(0L, minimum)
        assertEquals(0L, maximum)
    }

    @Test
    fun `monday restart after weekend classifies as definitely active`() {
        // Monday 2024-01-08, 6am
        val instant = millisAt(2024, 1, 8, 6, 0)
        assertEquals(ShiftActivityLevel.DEFINITELY_ACTIVE, engine.classify(instant))
    }

    @Test
    fun `stale detection uses most recent shift boundary`() {
        val confirmedAt = millisAt(2024, 1, 1, 6, 0) // Monday day shift
        val laterSameShift = millisAt(2024, 1, 1, 10, 0)
        val nextShift = millisAt(2024, 1, 1, 18, 0) // Monday night shift has started

        assertFalse(engine.isStale(confirmedAt, laterSameShift))
        assertTrue(engine.isStale(confirmedAt, nextShift))
    }
}
