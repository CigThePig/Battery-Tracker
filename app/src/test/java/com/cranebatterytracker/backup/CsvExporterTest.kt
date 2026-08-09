package com.cranebatterytracker.backup

import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteId
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {

    private fun event(
        eventId: String,
        sequenceNumber: Long,
        timestampEpochMillis: Long,
        remoteId: RemoteId,
        batteryId: Int,
        eventType: EventType = EventType.BATTERY_INSTALLED
    ) = DomainEvent(
        eventId = eventId,
        sequenceNumber = sequenceNumber,
        actionGroupId = "group-$eventId",
        timestampEpochMillis = timestampEpochMillis,
        remoteId = remoteId,
        batteryId = batteryId,
        eventType = eventType,
        previousBatteryId = null,
        newBatteryId = batteryId,
        targetActionGroupId = null,
        createdByAppVersion = "test",
        wallClockAnomalyDetected = false,
        elapsedRealtimeMillis = null,
        notes = null
    )

    @Test
    fun `raw export is ordered by sequence number, not wall-clock timestamp`() {
        // Battery 1 was installed first (sequence 1) at 10:00. The tablet clock was then
        // corrected backward, so battery 2's install - recorded second (sequence 2), and
        // therefore truly later - carries an earlier timestamp of 09:00.
        val events = listOf(
            event("e1", sequenceNumber = 1, timestampEpochMillis = 10 * 3_600_000L, remoteId = RemoteId.WEST, batteryId = 1),
            event("e2", sequenceNumber = 2, timestampEpochMillis = 9 * 3_600_000L, remoteId = RemoteId.EAST, batteryId = 2)
        )

        val out = ByteArrayOutputStream()
        CsvExporter.exportRawEvents(events, out)
        val lines = out.toString().trim().lines()
        val header = lines.first().split(",")
        val rows = lines.drop(1)

        assertTrue(header.contains("sequence_number"))
        assertEquals("e1", rows[0].split(",")[header.indexOf("event_id")])
        assertEquals("e2", rows[1].split(",")[header.indexOf("event_id")])

        val sequenceNumbers = rows.map { it.split(",")[header.indexOf("sequence_number")].toLong() }
        assertEquals(sequenceNumbers.sorted(), sequenceNumbers)
    }

    @Test
    fun `raw export includes the newer safeguard fields`() {
        val warning = DomainEvent(
            eventId = "w1",
            sequenceNumber = 3,
            actionGroupId = null,
            timestampEpochMillis = 1_000,
            remoteId = RemoteId.WEST,
            batteryId = null,
            eventType = EventType.SYSTEM_TIME_WARNING,
            previousBatteryId = null,
            newBatteryId = null,
            targetActionGroupId = "group-e1",
            createdByAppVersion = "1.2.3",
            wallClockAnomalyDetected = true,
            elapsedRealtimeMillis = 42_000,
            notes = "clock jumped"
        )

        val out = ByteArrayOutputStream()
        CsvExporter.exportRawEvents(listOf(warning), out)
        val lines = out.toString().trim().lines()
        val header = lines.first().split(",")
        val row = lines[1].split(",")

        assertEquals("group-e1", row[header.indexOf("target_action_group_id")])
        assertEquals("true", row[header.indexOf("wall_clock_anomaly_detected")])
        assertEquals("42000", row[header.indexOf("elapsed_realtime_millis")])
        assertEquals("1.2.3", row[header.indexOf("created_by_app_version")])
        assertEquals("clock jumped", row[header.indexOf("notes")])
    }
}
