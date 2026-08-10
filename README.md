# Crane Remote Battery Tracker

A dedicated Android app that measures the real-world runtime of the four
interchangeable crane remote batteries and helps determine whether
premature battery drain is caused by an individual battery, one of the two
crane remotes, incomplete charging, or unreliable human tracking.

Full product/UX/technical specification: [`crane_remote_battery_tracker_complete_spec.md`](crane_remote_battery_tracker_complete_spec.md).

## Core design

- **Event-sourced**: every action (battery install, removal, confirmation,
  correction, undo) is an immutable row in `events`. Current state is
  always reconstructed by replaying the log (`EventReducer`), never stored
  directly - restart, undo, and correction all fall out of that one rule.
- **Never turn missing data into bad data**: `RuntimeAnalysisEngine`
  classifies every cycle as `EXACT`, `SHIFT_INTERRUPTED`,
  `CONFIRMED_MINIMUM`, or `UNKNOWN` depending on how reliable its
  endpoints are, and only `EXACT` cycles (minus statistical outliers) feed
  the primary median.
- **Shift-aware**: `ShiftEngine` classifies time into definitely-active,
  ambiguous, and likely-inactive windows per the day/night shift schedule,
  so overnight and weekend gaps can't masquerade as multi-hour runtimes.
- **Local-only**: no `INTERNET` permission, no accounts, no cloud sync.
  Backups and CSV/SQLite export are local-only (`backup/`).
- **Visible evidence**: animated, classification-aware save receipts explain what each
  accurate entry contributed, while fleet and per-battery progress make the growing
  value of the shared data visible without points, accounts, or operator streaks.

## Project layout

```
app/src/main/java/com/cranebatterytracker/
├── data/            Room entities/DAOs, repository impl, DataStore settings
├── domain/
│   ├── model/        Framework-free domain types (DomainEvent, RemoteState, ...)
│   ├── analysis/      EventReducer, ShiftEngine, RuntimeAnalysisEngine, DiagnosticEngine
│   └── usecase/       BatteryChangeUseCase, CorrectStateUseCase, UndoUseCase, ...
├── ui/               Compose screens + ViewModels, one package per screen
├── backup/           BackupManager, CsvExporter
└── di/               Hand-rolled AppContainer (no DI framework)
```

Domain logic (`domain/`) has no Android dependency and is covered by plain
JUnit tests under `app/src/test/`.

## Building

Requires JDK 17+ and the Android SDK (compileSdk 34, minSdk 26).

```
./gradlew assembleDebug   # build the debug APK
./gradlew test            # run domain/use-case unit tests
```
