package com.cranebatterytracker.domain.usecase

import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.testutil.FakeBatteryTrackerRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockAnomalyUseCaseTest {

    @Test
    fun `a wall-clock jump is stamped on the new events and recorded as a warning`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val changeUseCase = BatteryChangeUseCase(repository, "test")

        // First event anchors the monotonic clock.
        changeUseCase(RemoteId.WEST, 1, now = 1_000, elapsedRealtimeMillis = 500)
        // Wall clock jumps forward an hour while barely any real time passed.
        changeUseCase(RemoteId.WEST, 2, now = 1_000 + 3_600_000L, elapsedRealtimeMillis = 1_500)

        val events = repository.allEvents()
        val secondInstall = events.last { it.eventType == EventType.BATTERY_INSTALLED }
        assertTrue(secondInstall.wallClockAnomalyDetected)
        assertTrue(events.any { it.eventType == EventType.SYSTEM_TIME_WARNING })
    }

    @Test
    fun `ordinary elapsed time between actions is never flagged`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val changeUseCase = BatteryChangeUseCase(repository, "test")

        changeUseCase(RemoteId.WEST, 1, now = 1_000, elapsedRealtimeMillis = 500)
        changeUseCase(RemoteId.WEST, 2, now = 1_000 + 60_000L, elapsedRealtimeMillis = 500 + 60_000L)

        val events = repository.allEvents()
        assertTrue(events.none { it.wallClockAnomalyDetected })
        assertTrue(events.none { it.eventType == EventType.SYSTEM_TIME_WARNING })
    }

    @Test
    fun `a wall-clock jump detected by Undo is still recorded as a warning`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val changeUseCase = BatteryChangeUseCase(repository, "test")
        val undoUseCase = UndoUseCase(repository, "test")

        // First event anchors the monotonic clock.
        changeUseCase(RemoteId.WEST, 1, now = 1_000, elapsedRealtimeMillis = 500)
        // Undo is the first interaction after the clock jumps forward an hour - if Undo
        // never ran anomaly detection, this jump would go completely unnoticed.
        undoUseCase(now = 1_000 + 3_600_000L, elapsedRealtimeMillis = 1_500)

        val events = repository.allEvents()
        assertTrue(events.any { it.eventType == EventType.UNDO_ACTION && it.wallClockAnomalyDetected })
        assertTrue(events.any { it.eventType == EventType.SYSTEM_TIME_WARNING })
    }
}
