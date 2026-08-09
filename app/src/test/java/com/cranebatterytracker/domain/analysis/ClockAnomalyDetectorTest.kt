package com.cranebatterytracker.domain.analysis

import com.cranebatterytracker.testutil.testEvent
import com.cranebatterytracker.domain.model.EventType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockAnomalyDetectorTest {

    private fun eventWithClock(wallClock: Long, elapsedRealtime: Long) = testEvent(
        timestamp = wallClock,
        eventType = EventType.BATTERY_INSTALLED
    ).copy(elapsedRealtimeMillis = elapsedRealtime)

    @Test
    fun `no anomaly when there is nothing to compare against`() {
        val anomaly = ClockAnomalyDetector.detect(emptyList(), newWallClockMillis = 10_000, newElapsedRealtimeMillis = 5_000)
        assertFalse(anomaly)
    }

    @Test
    fun `no anomaly when wall clock and monotonic clock agree`() {
        val prior = listOf(eventWithClock(wallClock = 1_000, elapsedRealtime = 500))
        // Both clocks advance by 4000ms.
        val anomaly = ClockAnomalyDetector.detect(prior, newWallClockMillis = 5_000, newElapsedRealtimeMillis = 4_500)
        assertFalse(anomaly)
    }

    @Test
    fun `anomaly when wall clock jumps far ahead of the monotonic clock`() {
        val prior = listOf(eventWithClock(wallClock = 1_000, elapsedRealtime = 500))
        // Wall clock jumps forward by an hour while only 500ms of real time passed.
        val anomaly = ClockAnomalyDetector.detect(prior, newWallClockMillis = 1_000 + 3_600_000L, newElapsedRealtimeMillis = 1_000)
        assertTrue(anomaly)
    }

    @Test
    fun `no anomaly when the monotonic clock goes backwards - a reboot, not tampering`() {
        val prior = listOf(eventWithClock(wallClock = 1_000, elapsedRealtime = 500_000))
        val anomaly = ClockAnomalyDetector.detect(prior, newWallClockMillis = 10_000, newElapsedRealtimeMillis = 1_000)
        assertFalse(anomaly)
    }

    @Test
    fun `small clock drift under the threshold is not an anomaly`() {
        val prior = listOf(eventWithClock(wallClock = 1_000, elapsedRealtime = 500))
        val anomaly = ClockAnomalyDetector.detect(prior, newWallClockMillis = 1_000 + 60_000L, newElapsedRealtimeMillis = 500 + 30_000L)
        assertFalse(anomaly)
    }

    @Test
    fun `compares against the most recently recorded event, not the one with the highest timestamp`() {
        // A real action at 10:00 (higher wall-clock time, but recorded first).
        val preCorrection = eventWithClock(wallClock = 36_000_000L, elapsedRealtime = 1_000_000L)
        // The clock is then corrected back to 09:00 and a second real action is recorded
        // 10ms later in real time - lower wall-clock time, but the truly latest event.
        val postCorrection = eventWithClock(wallClock = 32_400_000L, elapsedRealtime = 1_000_010L)

        // A third, perfectly ordinary action five real minutes after the correction.
        val anomaly = ClockAnomalyDetector.detect(
            priorEvents = listOf(preCorrection, postCorrection),
            newWallClockMillis = 32_400_000L + 5 * 60_000L,
            newElapsedRealtimeMillis = 1_000_010L + 5 * 60_000L
        )

        // Comparing against the pre-correction event (max timestamp) would see a ~55
        // minute wall-clock/monotonic mismatch and wrongly flag this. Comparing against
        // the actually-latest event agrees perfectly.
        assertFalse(anomaly)
    }
}
