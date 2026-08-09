package com.cranebatterytracker.domain.usecase

import com.cranebatterytracker.domain.analysis.EventReducer
import com.cranebatterytracker.domain.analysis.RuntimeAnalysisEngine
import com.cranebatterytracker.domain.analysis.ShiftEngine
import com.cranebatterytracker.domain.model.EventType
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RemoteKnowledge
import com.cranebatterytracker.domain.model.RuntimeClassification
import com.cranebatterytracker.testutil.FakeBatteryTrackerRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectStateUseCaseTest {

    @Test
    fun `correcting current state never fabricates a dead event`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val changeUseCase = BatteryChangeUseCase(repository, "test")
        val correctUseCase = CorrectStateUseCase(repository, "test")

        changeUseCase(RemoteId.WEST, 2, now = 1_000)
        correctUseCase(RemoteId.WEST, newBatteryId = 4, now = 50_000)

        assertFalse(repository.allEvents().any { it.eventType == EventType.BATTERY_REMOVED_DEAD })
        assertTrue(repository.allEvents().any { it.eventType == EventType.STATE_CORRECTED })
    }

    @Test
    fun `the new battery becomes a clean, trustworthy anchor`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val changeUseCase = BatteryChangeUseCase(repository, "test")
        val correctUseCase = CorrectStateUseCase(repository, "test")

        changeUseCase(RemoteId.WEST, 2, now = 1_000)
        correctUseCase(RemoteId.WEST, newBatteryId = 4, now = 50_000)

        val knowledge = EventReducer.reduce(repository.allEvents())
        val known = knowledge.getValue(RemoteId.WEST) as RemoteKnowledge.Known
        assertEquals(4, known.batteryId)
        assertEquals(50_000L, known.installedAt)
    }

    @Test
    fun `the previous interval becomes unusable for exact statistics`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val changeUseCase = BatteryChangeUseCase(repository, "test")
        val correctUseCase = CorrectStateUseCase(repository, "test")

        changeUseCase(RemoteId.WEST, 2, now = 1_000)
        correctUseCase(RemoteId.WEST, newBatteryId = 4, now = 50_000)

        val engine = RuntimeAnalysisEngine(ShiftEngine())
        val cycles = engine.deriveCycles(repository.allEvents())
        val batteryTwoCycle = cycles.single { it.batteryId == 2 }
        assertFalse(batteryTwoCycle.classification == RuntimeClassification.EXACT)
        assertFalse(batteryTwoCycle.includedInPrimaryStatistics)
    }
}
