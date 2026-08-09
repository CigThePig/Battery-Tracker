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

class BatteryChangeUseCaseTest {

    @Test
    fun `selecting the currently installed battery as its own replacement is rejected`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val changeUseCase = BatteryChangeUseCase(repository, "test")

        changeUseCase(RemoteId.WEST, 2, now = 1_000)
        val eventCountBeforeMistake = repository.allEvents().size

        val error = runCatching { changeUseCase(RemoteId.WEST, 2, now = 2_000) }.exceptionOrNull()

        assertTrue(error is BatteryTrackerException.BatteryAlreadyInThisRemote)
        val events = repository.allEvents()
        // No new events were written at all: no fabricated dead event, no re-install.
        assertEquals(eventCountBeforeMistake, events.size)
        assertTrue(events.none { it.eventType == EventType.BATTERY_REMOVED_DEAD })
        assertEquals(1, events.count { it.eventType == EventType.BATTERY_INSTALLED })

        val knowledge = EventReducer.reduce(events)
        assertEquals(2, (knowledge.getValue(RemoteId.WEST) as RemoteKnowledge.Known).batteryId)
    }

    @Test
    fun `changing to a genuinely different battery still succeeds`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val changeUseCase = BatteryChangeUseCase(repository, "test")

        changeUseCase(RemoteId.WEST, 2, now = 1_000)
        changeUseCase(RemoteId.WEST, 3, now = 2_000)

        val knowledge = EventReducer.reduce(repository.allEvents())
        assertEquals(3, (knowledge.getValue(RemoteId.WEST) as RemoteKnowledge.Known).batteryId)
        assertTrue(repository.allEvents().any { it.eventType == EventType.BATTERY_REMOVED_DEAD })
    }
}
