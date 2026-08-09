package com.cranebatterytracker

import android.app.Application
import com.cranebatterytracker.di.AppContainer
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val BACKUP_CHECK_INTERVAL_MILLIS = java.util.concurrent.TimeUnit.HOURS.toMillis(1)

class CraneBatteryTrackerApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        applicationScope.launch {
            container.ensureSeedData()
        }

        // The tablet is meant to stay powered on and running for days at a time (spec
        // section 74), so a single startup-time backup would only ever capture the first
        // day. Re-check hourly for as long as the process is alive: performDailyBackupIfNeeded
        // is a no-op once today's snapshot already exists, so this just catches the date
        // rolling over without needing an external scheduler.
        applicationScope.launch {
            while (true) {
                runCatching {
                    container.backupManager.performDailyBackupIfNeeded(LocalDate.now())
                    val retention = container.settingsRepository.settings.first()
                    container.backupManager.pruneBackups(
                        retention.dailyBackupRetentionCount,
                        retention.archiveBackupRetentionCount
                    )
                }
                delay(BACKUP_CHECK_INTERVAL_MILLIS)
            }
        }
    }
}
