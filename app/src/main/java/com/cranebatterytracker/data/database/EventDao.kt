package com.cranebatterytracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY timestampEpochMillis, eventId")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events ORDER BY timestampEpochMillis, eventId")
    suspend fun getAll(): List<EventEntity>

    @Insert
    suspend fun insertAll(events: List<EventEntity>)
}
