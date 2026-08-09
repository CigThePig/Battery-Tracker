package com.cranebatterytracker.domain.usecase

import com.cranebatterytracker.domain.analysis.EventReducer
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RemoteKnowledge
import com.cranebatterytracker.testutil.FakeBatteryTrackerRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoTargetSelectionTest {

    @Test
    fun `pressing Undo twice skips a warning-only group and reaches the real prior action`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val changeUseCase = BatteryChangeUseCase(repository, "test")
        val undoUseCase = UndoUseCase(repository, "test")

        // Two real operator actions.
        changeUseCase(RemoteId.WEST, 1, now = 1_000, elapsedRealtimeMillis = 500)
        changeUseCase(RemoteId.WEST, 2, now = 2_000, elapsedRealtimeMillis = 1_000)

        // First Undo (no explicit target) reverses the most recent real action (1 -> 2)
        // and, because the clock happens to jump at that same moment, also detects an
        // anomaly and writes a warning-only group alongside the UNDO_ACTION.
        undoUseCase(now = 2_000 + 3_600_000L, elapsedRealtimeMillis = 1_500)
        assertEquals(
            1,
            (EventReducer.reduce(repository.allEvents()).getValue(RemoteId.WEST) as RemoteKnowledge.Known).batteryId
        )
        assertTrue(repository.allEvents().any { it.eventType == EventType.SYSTEM_TIME_WARNING })

        // Second Undo (still no explicit target) must reach past the warning-only group
        // and reverse the original install of battery 1, not silently "undo" the warning.
        undoUseCase(now = 2_000 + 3_600_000L + 100, elapsedRealtimeMillis = 1_600)

        val knowledge = EventReducer.reduce(repository.allEvents())
        assertTrue(knowledge.getValue(RemoteId.WEST) is RemoteKnowledge.Unknown)
    }

    @Test
    fun `undo with nothing left to undo fails cleanly rather than targeting a warning`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val changeUseCase = BatteryChangeUseCase(repository, "test")
        val undoUseCase = UndoUseCase(repository, "test")

        changeUseCase(RemoteId.WEST, 1, now = 1_000, elapsedRealtimeMillis = 500)
        undoUseCase(now = 1_000 + 3_600_000L, elapsedRealtimeMillis = 1_500)

        val error = runCatching { undoUseCase(now = 1_000 + 3_600_000L + 100, elapsedRealtimeMillis = 1_600) }.exceptionOrNull()

        assertTrue(error is BatteryTrackerException.NothingToUndo)
    }
}
