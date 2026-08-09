package com.cranebatterytracker.data.repository

import androidx.room.withTransaction
import com.cranebatterytracker.data.database.AppDatabase
import com.cranebatterytracker.data.database.toDomain
import com.cranebatterytracker.data.database.toEntity
import com.cranebatterytracker.domain.model.Battery
import com.cranebatterytracker.domain.model.DomainEvent
import com.cranebatterytracker.domain.model.Remote
import com.cranebatterytracker.domain.repository.BatteryTrackerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BatteryTrackerRepositoryImpl(private val database: AppDatabase) : BatteryTrackerRepository {

    private val batteryDao = database.batteryDao()
    private val remoteDao = database.remoteDao()
    private val eventDao = database.eventDao()

    override fun observeBatteries(): Flow<List<Battery>> =
        batteryDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeRemotes(): Flow<List<Remote>> =
        remoteDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeEvents(): Flow<List<DomainEvent>> =
        eventDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun currentEventsSnapshot(): List<DomainEvent> =
        eventDao.getAll().map { it.toDomain() }

    override suspend fun writeEventGroup(events: List<DomainEvent>) {
        eventDao.insertAll(events.map { it.toEntity() })
    }

    override suspend fun <T> inTransaction(block: suspend () -> T): T =
        database.withTransaction { block() }
}
