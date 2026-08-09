package com.cranebatterytracker.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BatteryEntity::class, RemoteEntity::class, EventEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun batteryDao(): BatteryDao
    abstract fun remoteDao(): RemoteDao
    abstract fun eventDao(): EventDao

    companion object {
        private const val DATABASE_NAME = "crane_battery_tracker.db"

        /**
         * Adds [EventEntity.monotonicContinuityBroken] (spec review Issue 12 follow-up).
         * A Kotlin default value on the entity only affects newly-constructed objects in
         * memory - it does not change the SQLite table an install created under version 1
         * already has, so without this migration Room's schema validation would fail (and
         * the app would crash on open) for any device upgrading from the previous build.
         * DEFAULT 0 backfills every pre-existing row as "not broken", which is correct:
         * none of that history could have been produced by this not-yet-existing check.
         */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN monotonicContinuityBroken INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
