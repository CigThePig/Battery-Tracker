package com.cranebatterytracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY sequenceNumber")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events ORDER BY sequenceNumber")
    suspend fun getAll(): List<EventEntity>

    @Insert
    suspend fun insertAll(events: List<EventEntity>)
}
