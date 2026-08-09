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

    suspend fun ensureSeeded(batteryDao: BatteryDao, remoteDao: RemoteDao, now: Long = System.currentTimeMillis()) {
        if (batteryDao.count() == 0) {
            batteryDao.insertAll(batteries(now))
        }
        if (remoteDao.count() == 0) {
            remoteDao.insertAll(remotes())
        }
    }
}
