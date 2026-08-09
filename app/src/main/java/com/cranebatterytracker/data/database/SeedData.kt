package com.cranebatterytracker.data.database

import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.ScreenPosition

/** Version 1 tracks exactly four batteries and the two fixed remotes (spec sections 3.1/3.2). */
object SeedData {

    fun batteries(now: Long): List<BatteryEntity> = (1..4).map { number ->
        BatteryEntity(batteryId = number, displayNumber = number, active = true, createdAt = now, retiredAt = null)
    }

    fun remotes(): List<RemoteEntity> = listOf(
        RemoteEntity(
            remoteId = RemoteId.WEST.name,
            displayName = "West / Front Crane",
            shortName = "West",
            physicalPosition = ScreenPosition.LEFT.name,
            active = true
        ),
        RemoteEntity(
            remoteId = RemoteId.EAST.name,
            displayName = "East / Back Crane",
            shortName = "East",
            physicalPosition = ScreenPosition.RIGHT.name,
            active = true
        )
    )

    /**
     * Idempotent per fixed entity, not per table (spec review Issue 16): both DAOs insert
     * with [androidx.room.OnConflictStrategy.IGNORE], so re-running this against a table
     * that already has some (but not all) of its rows quietly fills in only what's
     * missing - e.g. Battery 3 alone missing from an otherwise-normal table. Gating on
     * `count() == 0` would never repair that case, since the table is no longer empty.
     */
    suspend fun ensureSeeded(batteryDao: BatteryDao, remoteDao: RemoteDao, now: Long = System.currentTimeMillis()) {
        batteryDao.insertAll(batteries(now))
        remoteDao.insertAll(remotes())
    }
}
