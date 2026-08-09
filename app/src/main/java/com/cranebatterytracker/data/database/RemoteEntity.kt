package com.cranebatterytracker.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cranebatterytracker.domain.model.Remote
import com.cranebatterytracker.domain.model.RemoteId
import com.cranebatterytracker.domain.model.ScreenPosition

@Entity(tableName = "remotes")
data class RemoteEntity(
    @PrimaryKey val remoteId: String,
    val displayName: String,
    val shortName: String,
    val physicalPosition: String,
    val active: Boolean
)

fun RemoteEntity.toDomain(): Remote = Remote(
    remoteId = RemoteId.fromStorageKey(remoteId),
    displayName = displayName,
    shortName = shortName,
    physicalPosition = ScreenPosition.valueOf(physicalPosition),
    active = active
)

fun Remote.toEntity(): RemoteEntity = RemoteEntity(
    remoteId = remoteId.name,
    displayName = displayName,
    shortName = shortName,
    physicalPosition = physicalPosition.name,
    active = active
)
