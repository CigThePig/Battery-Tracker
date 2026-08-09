package com.cranebatterytracker.domain.usecase

import com.cranebatterytracker.domain.analysis.RuntimeAnalysisEngine
import com.cranebatterytracker.domain.analysis.ShiftEngine
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RuntimeClassification
import com.cranebatterytracker.testutil.FakeBatteryTrackerRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the scenario in the review: a clock warning must remain
 * effective (and keep poisoning every interval open at that instant) even after the
 * operator action that happened to surface it is undone.
 */
class ClockWarningSurvivesUndoTest {

    @Test
    fun `undoing the action that detected a clock jump does not erase the warning`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val changeUseCase = BatteryChangeUseCase(repository, "test")
        val undoUseCase = UndoUseCase(repository, "test")
        val runtimeEngine = RuntimeAnalysisEngine(ShiftEngine())

        // Install West and East, anchoring the monotonic clock.
        changeUseCase(RemoteId.WEST, 1, now = 1_000, elapsedRealtimeMillis = 500)
        changeUseCase(RemoteId.EAST, 3, now = 1_000, elapsedRealtimeMillis = 500)

        // Clock jumps forward an hour; changing West's battery detects and records it.
        changeUseCase(RemoteId.WEST, 2, now = 1_000 + 3_600_000L, elapsedRealtimeMillis = 1_500)
        val mistakenGroupId = repository.allEvents()
            .last { it.eventType == EventType.BATTERY_INSTALLED && it.remoteId == RemoteId.WEST }
            .actionGroupId!!
        assertTrue(repository.allEvents().any { it.eventType == EventType.SYSTEM_TIME_WARNING })

        // Operator realizes the mistake and undoes the West battery change. Undo itself
        // runs after the clock has already stabilized at its new value, so it detects no
        // fresh discrepancy.
        undoUseCase(targetActionGroupId = mistakenGroupId, now = 1_000 + 3_600_000L + 100, elapsedRealtimeMillis = 1_600)

        val eventsAfterUndo = repository.allEvents()
        // The original warning event is still present and still effective (not undone) -
        // its actionGroupId is null, so EventFiltering can never strip it out.
        val warning = eventsAfterUndo.single { it.eventType == EventType.SYSTEM_TIME_WARNING }
        assertEquals(null, warning.actionGroupId)

        // East's battery was never touched by the mistaken action or its undo, but its
        // still-open interval spans the clock discontinuity, so it must remain
        // contaminated: closing it later can never be classified as EXACT.
        changeUseCase(RemoteId.EAST, 4, now = 1_000 + 3_600_000L + 200, elapsedRealtimeMillis = 1_700)
        val cycles = runtimeEngine.deriveCycles(repository.allEvents())
        val eastCycle = cycles.single { it.remoteId == RemoteId.EAST && it.batteryId == 3 }
        assertNotEquals(RuntimeClassification.EXACT, eastCycle.classification)
    }
}
