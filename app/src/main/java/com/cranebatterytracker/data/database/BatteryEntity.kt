package com.cranebatterytracker.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cranebatterytracker.domain.model.Battery

@Entity(tableName = "batteries")
data class BatteryEntity(
    @PrimaryKey val batteryId: Int,
    val displayNumber: Int,
    val active: Boolean,
    val createdAt: Long,
    val retiredAt: Long?
)

fun BatteryEntity.toDomain(): Battery = Battery(
    batteryId = batteryId,
    displayNumber = displayNumber,
    active = active,
    createdAt = createdAt,
    retiredAt = retiredAt
)

fun Battery.toEntity(): BatteryEntity = BatteryEntity(
    batteryId = batteryId,
    displayNumber = displayNumber,
    active = active,
    createdAt = createdAt,
    retiredAt = retiredAt
)
