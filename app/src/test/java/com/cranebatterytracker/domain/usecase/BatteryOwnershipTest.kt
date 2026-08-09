package com.cranebatterytracker.domain.usecase

import com.cranebatterytracker.domain.analysis.EventReducer
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.RemoteKnowledge
import com.cranebatterytracker.testutil.FakeBatteryTrackerRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryOwnershipTest {

    @Test
    fun `the same battery cannot be confirmed in both remotes`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val changeUseCase = BatteryChangeUseCase(repository, "test")

        changeUseCase(RemoteId.WEST, 1, now = 1_000)

        val error = runCatching { changeUseCase(RemoteId.EAST, 1, now = 2_000) }.exceptionOrNull()
        assertTrue(error is BatteryTrackerException.BatteryOwnedByOtherRemote)

        val knowledge = EventReducer.reduce(repository.allEvents())
        assertEquals(1, (knowledge.getValue(RemoteId.WEST) as RemoteKnowledge.Known).batteryId)
        assertTrue(knowledge.getValue(RemoteId.EAST) is RemoteKnowledge.Unknown)
    }

    @Test
    fun `collision correction makes the displaced remote unknown`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val changeUseCase = BatteryChangeUseCase(repository, "test")
        val correctUseCase = CorrectStateUseCase(repository, "test")

        changeUseCase(RemoteId.WEST, 1, now = 1_000)
        changeUseCase(RemoteId.EAST, 2, now = 1_000)

        correctUseCase(RemoteId.EAST, newBatteryId = 1, now = 5_000, resolveCollision = true)

        val knowledge = EventReducer.reduce(repository.allEvents())
        assertEquals(1, (knowledge.getValue(RemoteId.EAST) as RemoteKnowledge.Known).batteryId)
        assertTrue(knowledge.getValue(RemoteId.WEST) is RemoteKnowledge.Unknown)
    }

    @Test
    fun `unrelated batteries remain unchanged after a collision resolution`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val changeUseCase = BatteryChangeUseCase(repository, "test")
        val correctUseCase = CorrectStateUseCase(repository, "test")

        changeUseCase(RemoteId.WEST, 1, now = 1_000)
        changeUseCase(RemoteId.EAST, 2, now = 1_000)
        correctUseCase(RemoteId.EAST, newBatteryId = 1, now = 5_000, resolveCollision = true)

        // Battery 2 (now orphaned) and battery 3/4 (never touched) should not spuriously
        // appear anywhere in current remote state.
        val knowledge = EventReducer.reduce(repository.allEvents())
        val knownBatteryIds = knowledge.values.mapNotNull { (it as? RemoteKnowledge.Known)?.batteryId }
        assertEquals(listOf(1), knownBatteryIds)
    }

    @Test
    fun `collision without explicit resolution is rejected transactionally`() = runTest {
        val repository = FakeBatteryTrackerRepository()
        val changeUseCase = BatteryChangeUseCase(repository, "test")

        changeUseCase(RemoteId.WEST, 4, now = 1_000)
        val eventCountBefore = repository.allEvents().size

        runCatching { changeUseCase(RemoteId.EAST, 4, now = 2_000) }

        // A rejected attempt must not have written any partial state.
        assertEquals(eventCountBefore, repository.allEvents().size)
    }
}
