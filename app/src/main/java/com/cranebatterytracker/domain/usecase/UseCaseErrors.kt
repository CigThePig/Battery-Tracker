package com.cranebatterytracker.domain.usecase

import com.cranebatterytracker.domain.model.RemoteId

sealed class BatteryTrackerException(message: String) : Exception(message) {
    data class BatteryOwnedByOtherRemote(val otherRemote: RemoteId, val batteryDisplayNumber: Int) :
        BatteryTrackerException("Battery $batteryDisplayNumber is currently recorded in $otherRemote")

    data class BatteryAlreadyInThisRemote(val batteryDisplayNumber: Int) :
        BatteryTrackerException("Battery $batteryDisplayNumber is already installed in this remote")

    data object NothingToConfirm : BatteryTrackerException("This remote has no known battery to confirm")

    data object NothingToUndo : BatteryTrackerException("There is no recent action to undo")

    data object ActionAlreadyUndone : BatteryTrackerException("That action was already undone")
}
