package com.cranebatterytracker.domain.model

data class Remote(
    val remoteId: RemoteId,
    val displayName: String,
    val shortName: String,
    val physicalPosition: ScreenPosition,
    val active: Boolean
)

data class Battery(
    val batteryId: Int,
    val displayNumber: Int,
    val active: Boolean,
    val createdAt: Long,
    val retiredAt: Long?
)
