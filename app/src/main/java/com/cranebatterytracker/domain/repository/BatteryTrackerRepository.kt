package com.cranebatterytracker.domain.repository

import com.cranebatterytracker.domain.model.Battery
import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.Remote
import kotlinx.coroutines.flow.Flow

/**
 * The single source of truth for persisted state: batteries, remotes, and
 * the append-only event log. Deliberately does not expose "current state" -
 * that is always derived from [observeEvents] via EventReducer so the
 * database stays the one authority (spec section 65).
 */
interface BatteryTrackerRepository {
    fun observeBatteries(): Flow<List<Battery>>
    fun observeRemotes(): Flow<List<Remote>>
    fun observeEvents(): Flow<List<DomainEvent>>

    suspend fun currentEventsSnapshot(): List<DomainEvent>
    suspend fun writeEventGroup(events: List<DomainEvent>)

    /**
     * Runs [block] inside a single database transaction. Use cases read a
     * fresh snapshot, validate, and write from within this block so
     * validation and the resulting write are atomic (spec section 63/64).
     */
    suspend fun <T> inTransaction(block: suspend () -> T): T
}
