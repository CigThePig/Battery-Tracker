package com.cranebatterytracker.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "crane_battery_tracker_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val DAY_START = stringPreferencesKey("day_start")
        val DAY_AMBIGUOUS_START = stringPreferencesKey("day_ambiguous_start")
        val DAY_AMBIGUOUS_END = stringPreferencesKey("day_ambiguous_end")
        val NIGHT_START = stringPreferencesKey("night_start")
        val NIGHT_AMBIGUOUS_START = stringPreferencesKey("night_ambiguous_start")
        val NIGHT_AMBIGUOUS_END = stringPreferencesKey("night_ambiguous_end")
        val WORKING_DAYS = stringPreferencesKey("working_days")
        val ADMIN_PIN = stringPreferencesKey("admin_pin")
        val ADMIN_PIN_ENABLED = booleanPreferencesKey("admin_pin_enabled")
        val DAILY_BACKUP_RETENTION = intPreferencesKey("daily_backup_retention")
        val ARCHIVE_BACKUP_RETENTION = intPreferencesKey("archive_backup_retention")
        val WEST_DISPLAY_NAME = stringPreferencesKey("west_display_name")
        val EAST_DISPLAY_NAME = stringPreferencesKey("east_display_name")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            dayStart = parseTimeOrDefault(prefs[Keys.DAY_START], defaults.dayStart),
            dayAmbiguousStart = parseTimeOrDefault(prefs[Keys.DAY_AMBIGUOUS_START], defaults.dayAmbiguousStart),
            dayAmbiguousEnd = parseTimeOrDefault(prefs[Keys.DAY_AMBIGUOUS_END], defaults.dayAmbiguousEnd),
            nightStart = parseTimeOrDefault(prefs[Keys.NIGHT_START], defaults.nightStart),
            nightAmbiguousStart = parseTimeOrDefault(prefs[Keys.NIGHT_AMBIGUOUS_START], defaults.nightAmbiguousStart),
            nightAmbiguousEnd = parseTimeOrDefault(prefs[Keys.NIGHT_AMBIGUOUS_END], defaults.nightAmbiguousEnd),
            workingDays = prefs[Keys.WORKING_DAYS]?.let { parseWorkingDays(it) ?: defaults.workingDays } ?: defaults.workingDays,
            adminPin = if (prefs[Keys.ADMIN_PIN_ENABLED] == true) prefs[Keys.ADMIN_PIN] else null,
            dailyBackupRetentionCount = prefs[Keys.DAILY_BACKUP_RETENTION]?.takeIf { it > 0 } ?: defaults.dailyBackupRetentionCount,
            archiveBackupRetentionCount = prefs[Keys.ARCHIVE_BACKUP_RETENTION]?.takeIf { it > 0 } ?: defaults.archiveBackupRetentionCount,
            westDisplayName = prefs[Keys.WEST_DISPLAY_NAME] ?: defaults.westDisplayName,
            eastDisplayName = prefs[Keys.EAST_DISPLAY_NAME] ?: defaults.eastDisplayName
        )
    }

    /**
     * Every stored preference is parsed defensively (spec review Issue 16): a malformed
     * value - from an interrupted future migration, an older app version, manual
     * debugging, or plain storage corruption - must fall back to a known-good default
     * rather than throwing out of this flow's map{}, which every screen that reads
     * settings (main screen, diagnostics, battery picker, correction, backup retention)
     * depends on.
     */
    private fun parseTimeOrDefault(raw: String?, default: LocalTime): LocalTime {
        if (raw == null) return default
        return runCatching { LocalTime.parse(raw) }.getOrDefault(default)
    }

    /** Null return (not the default) only when [raw] is present but fails to parse, so the caller can fall back. */
    private fun parseWorkingDays(raw: String): Set<DayOfWeek>? = runCatching {
        raw.split(",").filter { it.isNotBlank() }.map { DayOfWeek.valueOf(it.trim()) }.toSet()
    }.getOrNull()

    suspend fun updateShiftTimes(
        dayStart: LocalTime,
        dayAmbiguousStart: LocalTime,
        dayAmbiguousEnd: LocalTime,
        nightStart: LocalTime,
        nightAmbiguousStart: LocalTime,
        nightAmbiguousEnd: LocalTime
    ) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DAY_START] = dayStart.toString()
            prefs[Keys.DAY_AMBIGUOUS_START] = dayAmbiguousStart.toString()
            prefs[Keys.DAY_AMBIGUOUS_END] = dayAmbiguousEnd.toString()
            prefs[Keys.NIGHT_START] = nightStart.toString()
            prefs[Keys.NIGHT_AMBIGUOUS_START] = nightAmbiguousStart.toString()
            prefs[Keys.NIGHT_AMBIGUOUS_END] = nightAmbiguousEnd.toString()
        }
    }

    suspend fun updateWorkingDays(days: Set<DayOfWeek>) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.WORKING_DAYS] = days.joinToString(",") { it.name }
        }
    }

    suspend fun updateAdminPin(pin: String?) {
        context.settingsDataStore.edit { prefs ->
            if (pin.isNullOrBlank()) {
                prefs[Keys.ADMIN_PIN_ENABLED] = false
            } else {
                prefs[Keys.ADMIN_PIN] = pin
                prefs[Keys.ADMIN_PIN_ENABLED] = true
            }
        }
    }

    suspend fun updateBackupRetention(dailyCount: Int, archiveCount: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DAILY_BACKUP_RETENTION] = dailyCount
            prefs[Keys.ARCHIVE_BACKUP_RETENTION] = archiveCount
        }
    }

    suspend fun updateRemoteDisplayNames(west: String, east: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.WEST_DISPLAY_NAME] = west
            prefs[Keys.EAST_DISPLAY_NAME] = east
        }
    }
}
