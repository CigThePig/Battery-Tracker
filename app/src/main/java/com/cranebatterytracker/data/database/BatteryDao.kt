package com.cranebatterytracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {
    @Query("SELECT * FROM batteries ORDER BY displayNumber")
    fun observeAll(): Flow<List<BatteryEntity>>

    @Query("SELECT * FROM batteries ORDER BY displayNumber")
    suspend fun getAll(): List<BatteryEntity>

    @Query("SELECT COUNT(*) FROM batteries")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(batteries: List<BatteryEntity>)
}
