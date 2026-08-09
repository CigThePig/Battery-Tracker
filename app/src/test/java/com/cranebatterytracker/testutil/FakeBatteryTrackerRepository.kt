package com.cranebatterytracker.testutil

import com.cranebatterytracker.domain.model.Battery
import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.Remote
import com.cranebatterytracker.domain.repository.BatteryTrackerRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory repository for fast, Android-free use case tests. */
class FakeBatteryTrackerRepository : BatteryTrackerRepository {
    private val events = mutableListOf<DomainEvent>()
    private val batteriesFlow = MutableStateFlow<List<Battery>>(emptyList())
    private val remotesFlow = MutableStateFlow<List<Remote>>(emptyList())
    private val eventsFlow = MutableStateFlow<List<DomainEvent>>(emptyList())

    override fun observeBatteries() = batteriesFlow
    override fun observeRemotes() = remotesFlow
    override fun observeEvents() = eventsFlow

    override suspend fun currentEventsSnapshot(): List<DomainEvent> = events.toList()

    override suspend fun writeEventGroup(events: List<DomainEvent>) {
        this.events += events
        eventsFlow.value = this.events.toList()
    }

    override suspend fun <T> inTransaction(block: suspend () -> T): T = block()

    fun allEvents(): List<DomainEvent> = events.toList()
}
