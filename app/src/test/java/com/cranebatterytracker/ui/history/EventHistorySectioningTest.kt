package com.cranebatterytracker.ui.history

import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.ui.common.formatDayHeader
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class EventHistorySectioningTest {

    // Matches sectionEventsByDayHeader's use of formatDayHeader's default zone, so this
    // test's day boundaries agree with the production code's regardless of which
    // timezone the test happens to run in.
    private val zone: ZoneId = ZoneId.systemDefault()

    private fun millisAt(daysAgo: Long): Long =
        LocalDate.now(zone).minusDays(daysAgo).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun event(sequenceNumber: Long, timestamp: Long) = DomainEvent(
        eventId = "e$sequenceNumber",
        sequenceNumber = sequenceNumber,
        actionGroupId = null,
        timestampEpochMillis = timestamp,
        remoteId = RemoteId.WEST,
        batteryId = 1,
        eventType = EventType.STATE_CONFIRMED,
        previousBatteryId = null,
        newBatteryId = null,
        targetActionGroupId = null,
        createdByAppVersion = "test",
        wallClockAnomalyDetected = false,
        notes = null
    )

    @Test
    fun `a day label that recurs non-contiguously produces two separate sections, not one merged section`() {
        // Far enough in the past that neither collides with formatDayHeader's TODAY /
        // YESTERDAY special-casing - both fall through to the plain formatted-date label.
        val dayA = millisAt(daysAgo = 100)
        val dayB = millisAt(daysAgo = 99)

        // Sequence order (already sorted, as the screen passes it in): day B, day A, day B
        // again - e.g. a backward date correction moved the middle event's wall-clock
        // timestamp behind the first, even though it was recorded between the two dayB
        // events.
        val sortedEvents = listOf(
            event(3, dayB),
            event(2, dayA),
            event(1, dayB)
        )

        val sections = sectionEventsByDayHeader(sortedEvents)

        // A plain groupBy would merge both dayB occurrences into a single section ahead of
        // dayA, silently reordering event 1 (recorded first) after event 3 and above
        // event 2. The correct result keeps three contiguous runs in recorded order.
        assertEquals(3, sections.size)
        assertEquals(formatDayHeader(dayB, zone), sections[0].first)
        assertEquals(listOf("e3"), sections[0].second.map { it.eventId })
        assertEquals(formatDayHeader(dayA, zone), sections[1].first)
        assertEquals(listOf("e2"), sections[1].second.map { it.eventId })
        assertEquals(formatDayHeader(dayB, zone), sections[2].first)
        assertEquals(listOf("e1"), sections[2].second.map { it.eventId })
    }

    @Test
    fun `consecutive events on the same day share one section`() {
        val day = millisAt(daysAgo = 50)
        val sortedEvents = listOf(event(2, day + 60_000), event(1, day))

        val sections = sectionEventsByDayHeader(sortedEvents)

        assertEquals(1, sections.size)
        assertEquals(listOf("e2", "e1"), sections[0].second.map { it.eventId })
    }
}
