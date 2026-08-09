package com.cranebatterytracker.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteDao {
    @Query("SELECT * FROM remotes ORDER BY remoteId")
    fun observeAll(): Flow<List<RemoteEntity>>

    @Query("SELECT * FROM remotes ORDER BY remoteId")
    suspend fun getAll(): List<RemoteEntity>

    @Query("SELECT COUNT(*) FROM remotes")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(remotes: List<RemoteEntity>)
}
