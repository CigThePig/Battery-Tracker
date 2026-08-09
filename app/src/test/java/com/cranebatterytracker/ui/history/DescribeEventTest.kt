package com.cranebatterytracker.ui.history

import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DescribeEventTest {

    private fun warning(wallClockAnomalyDetected: Boolean, monotonicContinuityBroken: Boolean) = DomainEvent(
        eventId = "w",
        sequenceNumber = 1,
        actionGroupId = null,
        timestampEpochMillis = 1_000,
        remoteId = RemoteId.WEST,
        batteryId = null,
        eventType = EventType.SYSTEM_TIME_WARNING,
        previousBatteryId = null,
        newBatteryId = null,
        targetActionGroupId = null,
        createdByAppVersion = "test",
        wallClockAnomalyDetected = wallClockAnomalyDetected,
        monotonicContinuityBroken = monotonicContinuityBroken,
        notes = null
    )

    @Test
    fun `a genuine wall-clock anomaly is described as a clock problem`() {
        val text = describeEvent(warning(wallClockAnomalyDetected = true, monotonicContinuityBroken = false))
        assertEquals("Device clock anomaly detected.", text)
    }

    @Test
    fun `a reboot-only marker is not described as a clock anomaly`() {
        val text = describeEvent(warning(wallClockAnomalyDetected = false, monotonicContinuityBroken = true))
        assertNotEquals("Device clock anomaly detected.", text)
        assertEquals("Device restarted around this point.", text)
    }
}
