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

class UndoUseCaseTest {

    @Test
    fun `undo restores the prior state and a second replay is stable`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val changeUseCase = BatteryChangeUseCase(repository, "test")
        val undoUseCase = UndoUseCase(repository, "test")

        changeUseCase(RemoteId.WEST, 1, now = 1_000)
        changeUseCase(RemoteId.WEST, 2, now = 2_000)
        undoUseCase(now = 3_000)

        val knowledge = EventReducer.reduce(repository.allEvents())
        assertEquals(1, (knowledge.getValue(RemoteId.WEST) as RemoteKnowledge.Known).batteryId)

        // "Restart" - reduce again from the same persisted log - must agree.
        val replay = EventReducer.reduce(repository.allEvents())
        assertEquals(knowledge, replay)
    }

    @Test
    fun `undone events remain visible in the raw log`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val changeUseCase = BatteryChangeUseCase(repository, "test")
        val undoUseCase = UndoUseCase(repository, "test")

        changeUseCase(RemoteId.WEST, 1, now = 1_000)
        val countBeforeUndo = repository.allEvents().size
        undoUseCase(now = 2_000)

        // Undo appends an event; it never deletes rows.
        assertTrue(repository.allEvents().size > countBeforeUndo)
        assertTrue(repository.allEvents().any { it.eventType == EventType.BATTERY_INSTALLED })
    }

    @Test
    fun `undoing an already-undone action fails`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val changeUseCase = BatteryChangeUseCase(repository, "test")
        val undoUseCase = UndoUseCase(repository, "test")

        changeUseCase(RemoteId.WEST, 1, now = 1_000)
        val groupId = repository.allEvents().first { it.eventType == EventType.BATTERY_INSTALLED }.actionGroupId!!
        undoUseCase(targetActionGroupId = groupId, now = 2_000)

        val error = runCatching { undoUseCase(targetActionGroupId = groupId, now = 3_000) }.exceptionOrNull()
        assertTrue(error is BatteryTrackerException.ActionAlreadyUndone)
    }

    @Test
    fun `undo with nothing to undo fails cleanly`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val undoUseCase = UndoUseCase(repository, "test")

        val error = runCatching { undoUseCase(now = 1_000) }.exceptionOrNull()
        assertTrue(error is BatteryTrackerException.NothingToUndo)
    }
}
