package com.cranebatterytracker.domain.model

enum class RemoteId {
    WEST,
    EAST;

    companion object {
        fun fromStorageKey(key: String): RemoteId = valueOf(key)
    }
}

fun RemoteId.toStorageKey(): String = name

fun RemoteId.other(): RemoteId = if (this == RemoteId.WEST) RemoteId.EAST else RemoteId.WEST
