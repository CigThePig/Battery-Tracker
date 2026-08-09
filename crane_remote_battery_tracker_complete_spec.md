# Crane Remote Battery Tracker
## Complete Product, UX, Technical, and Implementation Specification

**Target platform:** Dedicated Android tablet  
**Screen orientation:** Portrait, permanently mounted vertically  
**Operation:** Fully offline / local-only  
**Primary language:** Kotlin  
**UI framework:** Jetpack Compose  
**Persistence:** Room / SQLite  
**Settings:** DataStore  
**Tracked batteries:** 4  
**Tracked remotes:** 2  

---

# 1. Executive Summary

The Crane Remote Battery Tracker is a dedicated Android application designed to measure the real-world runtime of four interchangeable crane remote batteries and determine whether premature battery drain is caused by:

- an individual battery;
- several batteries;
- one of the two crane remotes;
- incomplete charging;
- operational differences;
- or unreliable human tracking.

The app will run on a small Android tablet mounted vertically beside the battery charging area.

The two crane remotes are:

- **West / Front Crane**
- **East / Back Crane**

The app must be extremely simple to use. A person seeing it for the first time should immediately understand what to do.

At the same time, the software must assume that operators will sometimes:

- forget to record battery changes;
- ignore the app;
- press the wrong button;
- select the wrong battery;
- change more than one battery without recording it;
- leave a shift without interacting with the tablet;
- incorrectly assume someone else recorded the change.

The system therefore cannot depend on perfect participation.

The central philosophy of the application is:

> **Never turn missing data into bad data.**

If the app does not know what happened, it should record uncertainty rather than fabricate a precise answer.

Even if only one operator out of five uses the app correctly, the measurements collected by that operator should remain useful.

---

# 2. Why the App Is Needed

The crane remote batteries are expensive to replace, but they appear to be dying faster than expected.

Without measurement, observations such as:

> “Battery 3 seems bad.”

or:

> “The back crane eats batteries.”

are subjective.

Several different problems could produce the same symptoms:

1. One battery may be deteriorating.
2. Several batteries may be deteriorating.
3. All batteries may be near the end of their useful life.
4. The West / Front remote may consume more power than expected.
5. The East / Back remote may consume more power than expected.
6. A charging issue may be contributing.
7. Crane usage intensity may vary between shifts.
8. Human memory may exaggerate failures.
9. Several of these factors may be occurring together.

The app is intended to convert these impressions into real evidence.

Example:

| Battery | Typical Runtime | Reliable Cycles | Trend |
|---|---:|---:|---|
| 1 | 5h 21m | 18 | Stable |
| 2 | 5h 08m | 21 | Stable |
| 3 | 3h 42m | 19 | Declining |
| 4 | 5h 17m | 17 | Stable |

This would make Battery 3 a strong investigation or replacement candidate.

Alternatively:

| Remote | Typical Battery Runtime |
|---|---:|
| West / Front | 5h 19m |
| East / Back | 4h 24m |

If multiple batteries consistently perform worse in the East remote, the problem may be the remote rather than the batteries.

---

# 3. Physical System Being Tracked

## 3.1 Batteries

There are four interchangeable batteries:

- Battery 1
- Battery 2
- Battery 3
- Battery 4

The database should support future batteries, but Version 1 of the interface is explicitly optimized around these four.

## 3.2 Remotes

### West Remote

- Internal ID: `WEST`
- Display name: **West / Front Crane**
- Physical side: West
- Screen position: **Always left**

### East Remote

- Internal ID: `EAST`
- Display name: **East / Back Crane**
- Physical side: East
- Screen position: **Always right**

The app must never reverse or dynamically reorder these positions.

---

# 4. Work Schedule

The workplace runs two shifts Monday through Friday.

## Day Shift

- Start: **5:00 AM**
- Normal earliest finish: **1:30 PM**
- Normal latest finish: **2:30 PM**
- Occasionally earlier, especially Friday

## Night Shift

- Start: **5:00 PM**
- Normal earliest finish: **1:30 AM**
- Normal latest finish: **2:30 AM**

The night shift therefore crosses midnight.

The shift schedule provides context, not proof that the crane was operating continuously.

---

# 5. Product Design Principles

The entire app should follow these rules.

## 5.1 Two-tap normal operation

A normal battery replacement should require:

1. Tap the correct crane.
2. Tap the replacement battery.

## 5.2 No forms

Normal operators should never type.

## 5.3 No login

No employee name, badge number, account, or authentication should be required for normal use.

## 5.4 No internet dependency

Everything must function locally.

## 5.5 No invented information

If the app does not know which battery is installed, it must use:

`UNKNOWN`

It must never guess.

## 5.6 Raw history is permanent

Events are stored permanently.

Analysis may change later.

History should not.

## 5.7 Easy recovery

One correct observation should be enough to restore trustworthy tracking after an uncertain period.

## 5.8 Missing data is acceptable

Unknown intervals are acceptable.

False measurements are not.

## 5.9 Operator mistakes are expected

Undo and correction are core features, not hidden administrative tools.

## 5.10 Diagnostics must express uncertainty

The app should never make strong claims from weak evidence.

---

# 6. Portrait-First Interface

The tablet is installed vertically, so the entire application must be designed portrait-first.

The app should be locked to portrait orientation.

The main screen must not be a landscape interface squeezed into a narrow display.

---

# 7. Preserve the West / East Physical Relationship

Even in portrait orientation, the app should retain the real-world physical mapping:

```text
WEST / FRONT          EAST / BACK
     LEFT                  RIGHT
```

The main screen should use two tall side-by-side columns.

This makes the tablet itself a simple spatial representation of the work area.

---

# 8. Main Screen Layout

Concept:

```text
┌─────────────────────────────────┐
│      CRANE BATTERY TRACKER      │
│                                 │
│   WEST              EAST        │
│   FRONT             BACK        │
│    ←                 →          │
│                                 │
│ ┌─────────────┐ ┌─────────────┐ │
│ │    WEST     │ │    EAST     │ │
│ │    FRONT    │ │    BACK     │ │
│ │             │ │             │ │
│ │  BATTERY    │ │  BATTERY    │ │
│ │             │ │             │ │
│ │      2      │ │      4      │ │
│ │             │ │             │ │
│ │ Since       │ │ Since       │ │
│ │ 7:18 AM     │ │ 8:02 AM     │ │
│ │             │ │             │ │
│ │ BATTERY     │ │ BATTERY     │ │
│ │ DEAD?       │ │ DEAD?       │ │
│ │             │ │             │ │
│ │ [ CHANGE ]  │ │ [ CHANGE ]  │ │
│ │ [ BATTERY ] │ │ [ BATTERY ] │ │
│ └─────────────┘ └─────────────┘ │
│                                 │
│ [ WRONG BUTTON / UNDO ]         │
│                                 │
│ [ BATTERY RESULTS ]             │
└─────────────────────────────────┘
```

The main screen should not scroll.

---

# 9. Main Screen Visual Priority

The most important visual elements are:

1. WEST / EAST
2. Battery number
3. Change Battery button
4. State warning if needed
5. Secondary time information

The battery number should be extremely large.

The live runtime should not dominate the screen because the app is primarily a tracking system, not a stopwatch.

Use wording such as:

`Since 7:18 AM`

rather than making a large timer the visual centerpiece.

---

# 10. Crane Identification

Each side should provide redundant identification.

Left:

```text
← WEST
Front Crane
```

Right:

```text
EAST →
Back Crane
```

The compass direction should be visually stronger than Front/Back.

---

# 11. Button Design

Portrait mode provides less horizontal space, so buttons should become taller rather than smaller.

Preferred:

```text
┌─────────────┐
│   CHANGE    │
│   BATTERY   │
└─────────────┘
```

Touch targets must remain glove-friendly.

No essential button should be reduced to a tiny icon.

---

# 12. Core Operator Workflow

## Step 1: Select the Crane

The operator taps **CHANGE BATTERY** on either the West or East card.

## Step 2: Select the Replacement Battery

The screen becomes:

```text
┌─────────────────────────────────┐
│          WEST / FRONT           │
│                                 │
│        BATTERY 2 DIED           │
│                                 │
│   WHICH BATTERY DID YOU PUT IN? │
│                                 │
│ ┌────────────┐ ┌────────────┐   │
│ │     1      │ │     2      │   │
│ └────────────┘ └────────────┘   │
│                                 │
│ ┌────────────┐ ┌────────────┐   │
│ │     3      │ │     4      │   │
│ └────────────┘ └────────────┘   │
│                                 │
│ [           CANCEL            ] │
└─────────────────────────────────┘
```

The 2 × 2 battery grid should use most of the screen.

There should be:

- no keyboard;
- no Save button;
- no date selection;
- no time selection;
- no shift selection;
- no employee name;
- no dropdown;
- no extra form fields.

The operator only tells the app the one thing it cannot infer:

> **Which physical battery was installed?**

---

# 13. Successful Battery Change

After selection:

```text
WEST

BATTERY 2 → BATTERY 3

SAVED
```

This should display briefly, then return automatically to the main screen.

No additional OK button should be required.

---

# 14. Human Reliability Must Not Be Assumed

The system should treat every interaction as an observation of reality.

Example:

```text
06:12
Battery 2 installed in West.
```

This is a known fact.

Later:

```text
10:48
Battery 2 removed dead.
Battery 4 installed.
```

Now the app has a high-confidence completed cycle:

```text
Battery 2
West
Runtime: 4h 36m
```

However, if nobody interacts for hours and later someone reports a different battery, the app must not assume the original battery remained installed the whole time.

---

# 15. Fundamental Data Rule

The app must never invent a battery identity.

It may infer:

- likely downtime;
- shift boundaries;
- suspicious runtimes;
- incomplete history;
- expected work periods.

It must never infer:

> “Battery 3 must still have been installed.”

If the evidence is insufficient, the state becomes uncertain.

---

# 16. Unknown Gaps

Example:

```text
06:12
Battery 2 installed.

No entries.

17:22
Battery 4 confirmed currently installed.
```

Correct interpretation:

```text
Battery 2
→ UNKNOWN HISTORY →
Battery 4 confirmed at 17:22
```

Incorrect interpretation:

```text
Battery 2 runtime = 11h 10m
```

The unknown section contributes no precise runtime.

---

# 17. Self-Healing Data

Suppose the tablet says:

```text
WEST
BATTERY 2
```

but the actual remote has Battery 4.

The operator should simply correct the current state.

The resulting history becomes:

```text
Battery 2
→ UNKNOWN GAP →
Battery 4 confirmed at 11:32
```

The operator should not have to reconstruct what happened during the missing period.

---

# 18. Correct Current State

The current battery number on the main screen should be tappable.

Screen:

```text
WEST / FRONT CRANE

THE TABLET CURRENTLY SAYS:

BATTERY 2

WHAT BATTERY IS ACTUALLY IN THE REMOTE?

[ 1 ]       [ 2 ]

[ 3 ]       [ 4 ]

[ I DON'T KNOW ]

CANCEL
```

This is a state correction, not a normal battery change.

---

# 19. Correction Logic

If the tablet says Battery 2 but the operator selects Battery 4:

```text
STATE_CORRECTED
Previous battery: 2
New battery: 4
```

The preceding open Battery 2 interval becomes uncertain.

The app must not create a fake event saying Battery 2 died at the correction time.

Battery 4 begins a new trustworthy interval.

---

# 20. Explicit Unknown State

The app must support:

`I DON'T KNOW`

This records:

`STATE_MARKED_UNKNOWN`

The main screen becomes:

```text
WEST / FRONT

BATTERY
?

UNKNOWN

[ SET BATTERY ]
```

Unknown is an acceptable state.

The system must never pressure the operator into guessing.

---

# 21. Prevent Impossible Battery Assignments

A physical battery cannot be in both remotes.

If Battery 4 is in East, the West battery picker should show:

```text
4
IN EAST
```

and make it unavailable for a normal battery change.

If a user insists that Battery 4 is actually in West, the app has detected contradictory state.

It should ask whether the previous East record is wrong.

If the correction is confirmed:

```text
EAST = UNKNOWN
WEST = Battery 4
```

The app must not guess what battery is now in East.

---

# 22. Undo

After a battery change, show a full-width undo bar:

```text
WEST 2 → 4 SAVED

WRONG BUTTON?  UNDO
```

Undo should also remain available through a permanent **WRONG BUTTON** control on the main screen.

Undo should not delete database rows.

Instead it should create an `UNDO_ACTION` event referencing the original action group.

---

# 23. Accidental Input Protection

The app should include:

- large touch targets;
- rapid-tap debounce;
- immediate undo;
- persistent last-action recovery;
- clear feedback after successful saves;
- explicit failure messages if a save fails.

Rapid duplicate taps should not create duplicate battery events.

---

# 24. Remote State Model

Each remote is always in one of three logical states.

## Confirmed

Example:

```text
WEST
Battery 2
Confirmed
```

## Stale

Example:

```text
WEST
Battery 2
Not confirmed this shift
```

Battery 2 is the last known battery, but the observation is no longer fresh.

## Unknown

Example:

```text
WEST
Battery unknown
```

No open exact-runtime interval continues through this state.

---

# 25. State Invariants

The application must enforce:

1. Each remote contains zero or one battery.
2. Each battery belongs to zero or one remote.
3. The same battery cannot be confirmed in both remotes.
4. Unknown state does not create runtime.
5. Corrections do not rewrite history.
6. Undo remains auditable.
7. Current state is reproducible from persistent data.
8. Restart does not change logical equipment state.

---

# 26. Shift-Aware Tracking

Shift times should not be implemented as simple timers.

Instead, create a `ShiftEngine` that classifies time intervals.

## Day Shift

Definitely active:

```text
05:00 → 13:30
```

Ambiguous end window:

```text
13:30 → 14:30
```

Likely inactive afterward:

```text
14:30 → 17:00
```

## Night Shift

Definitely active:

```text
17:00 → 01:30
```

Ambiguous end window:

```text
01:30 → 02:30
```

Likely inactive:

```text
02:30 → 05:00
```

Weekend periods are inactive unless future settings specify otherwise.

---

# 27. Shift Start Confirmation

At approximately 5:00 AM and 5:00 PM on weekdays, the app can show a non-blocking banner:

```text
START OF SHIFT

WEST: 2        EAST: 4

[ BOTH STILL CORRECT ]

[ FIX WEST ]   [ FIX EAST ]
```

This creates fresh anchor observations with very little operator effort.

The banner must not block battery replacement.

If ignored, nothing breaks.

---

# 28. Stale State in Portrait Mode

Example:

```text
┌─────────────┐
│    WEST     │
│    FRONT    │
│             │
│  BATTERY 2  │
│             │
│ NOT         │
│ CONFIRMED   │
│ THIS SHIFT  │
│             │
│ [ STILL 2 ] │
│             │
│ [ CHANGE ]  │
└─────────────┘
```

The operator can restore confidence with one tap.

---

# 29. Why Runtime May Be a Range

Suppose:

```text
Battery 2 installed:
12:30 PM

Next reliable dead event:
5:45 AM next day
```

The app knows some portions of that interval were almost certainly inactive.

It should calculate:

- minimum plausible active runtime;
- maximum plausible active runtime.

It must not invent a fake precise value.

---

# 30. Runtime Classifications

Recommended initial classes:

- `EXACT`
- `SHIFT_INTERRUPTED`
- `CONFIRMED_MINIMUM`
- `SUSPICIOUS_HIGH`
- `UNKNOWN`

A short runtime can still be `EXACT` while also carrying:

`isShortRuntimeEvent = true`

---

# 31. Exact Cycle Definition

A cycle qualifies as `EXACT` when:

- installation is reliably observed;
- removal/death is reliably observed;
- no correction occurs between them;
- no unknown state occurs;
- no contradictory assignment occurs;
- no device-time anomaly affects the interval;
- the cycle does not cross uncertain downtime.

---

# 32. Shift-Interrupted Cycle

If both endpoints are reliable but the interval crosses scheduled downtime:

```text
classification = SHIFT_INTERRUPTED
```

Store:

- minimum active runtime;
- maximum active runtime.

Do not include it in the primary exact median by default.

---

# 33. Confirmed Minimum Runtime

Suppose:

```text
6:00
Battery 3 installed.

9:15
Battery 3 confirmed still installed.
```

Then:

```text
Battery 3 lasted at least 3h 15m.
```

This information remains useful even if the later history becomes unknown.

It is not treated as an exact completed cycle.

---

# 34. Statistical Philosophy

The app is not performing a laboratory battery-capacity test.

Crane usage varies.

One shift may involve heavy continuous crane activity.

Another may include substantial idle time.

The measured quantity is therefore:

> **Observed battery operating lifetime under real workplace conditions.**

Repeated patterns are more important than individual measurements.

---

# 35. Primary Runtime Statistic

The primary battery runtime should use:

> **Median of reliable exact cycles**

not arithmetic mean.

Example:

```text
5h 03m
5h 18m
4h 57m
14h 22m
5h 11m
```

The median remains useful while the arithmetic mean is heavily distorted.

---

# 36. Outlier Detection

Do not classify battery-specific outliers until enough reliable data exists.

Suggested minimum:

```text
5 reliable exact cycles
```

After that, calculate:

- median;
- Median Absolute Deviation (MAD);
- recent historical behavior.

A practical suspicious-high rule can combine:

- roughly two hours beyond historical median;
- robust statistical deviation.

Thresholds should be configurable.

---

# 37. Suspicious High Runtime

If a cycle is suspiciously long:

- preserve raw events;
- show it in history;
- show it on graphs;
- exclude it from the primary median;
- never delete it.

Outlier classification should be recalculated later as more data appears.

---

# 38. Re-Evaluating Old Outliers

Suppose Battery 4 originally records:

```text
5.1h
5.0h
5.3h
7.4h
```

The 7.4-hour result may initially be suspicious.

Later:

```text
7.1h
7.3h
7.2h
7.5h
```

appear.

The analysis engine can reconsider the original classification.

Raw history remains unchanged.

---

# 39. Short Runtime Detection

A short runtime is potentially more important than a long one.

Example:

```text
Battery baseline:
5h 10m

New exact cycle:
1h 48m
```

This should be preserved and flagged.

Repeated short cycles should influence recent battery health.

A single short event should not automatically condemn the battery.

---

# 40. Battery Health Model

For each battery, calculate:

- lifetime reliable median;
- recent reliable median;
- reliable cycle count;
- recorded dead-event count;
- short-runtime event count;
- suspicious-high count;
- West median;
- East median;
- historical baseline;
- trend.

---

# 41. Recent Performance

Suggested initial rolling groups:

- last 5 exact cycles;
- last 10 exact cycles when enough data exists.

This distinguishes lifetime behavior from current behavior.

---

# 42. Battery Baseline

Once enough early reliable cycles exist, establish a historical baseline from an early stable group.

The baseline algorithm should remain replaceable.

Do not create a baseline from only one or two cycles.

---

# 43. Battery Trend Categories

Suggested user-facing states:

- NOT ENOUGH DATA
- STABLE
- WATCH
- DECLINING
- STRONG REPLACEMENT CANDIDATE

These must always be accompanied by supporting evidence.

---

# 44. Example Battery Diagnostic

```text
BATTERY 3

Typical reliable runtime
4h 11m

Recent runtime
3h 37m

Historical baseline
5h 08m

Reliable cycles
31

Recent change
-29%

Short runtimes
6 of last 10

TREND
DECLINING

ASSESSMENT
Strong replacement candidate
```

---

# 45. Dead Battery Event Counts

Even if exact runtime cannot be calculated, a dead-battery report can still be useful.

Example:

```text
Battery 4
Recorded dead event +1
```

Diagnostics should separately track:

- complete reliable cycles;
- total dead events;
- short-runtime events;
- suspicious measurements.

---

# 46. Remote Diagnostics

The app should compare West and East while controlling for battery identity.

A simple overall average can be misleading if weaker batteries happen to be used more often in one remote.

Instead calculate for each battery:

```text
Battery 1
West median
East median

Battery 2
West median
East median
```

and so on.

---

# 47. Example Paired Remote Comparison

```text
Battery 1
West: 5h 18m
East: 4h 31m
Difference: East -47m

Battery 2
West: 5h 11m
East: 4h 29m
Difference: East -42m

Battery 3
West: 5h 04m
East: 4h 16m
Difference: East -48m

Battery 4
West: 5h 23m
East: 4h 37m
Difference: East -46m
```

This is strong evidence of a remote-specific effect.

---

# 48. Remote Warning Thresholds

A conservative starting rule:

- at least 3 reliable cycles for a battery on each remote before using that battery in paired comparison;
- at least 2 batteries showing the same directional difference before showing a developing warning;
- 3 or 4 batteries showing the same pattern for a stronger warning.

---

# 49. Remote Diagnostic Language

Weak evidence:

> Not enough data to compare remotes.

Developing evidence:

> East currently shows shorter runtime, but more measurements are needed.

Stronger repeated evidence:

> Batteries are consistently lasting less time in the East / Back remote. This pattern appears across multiple batteries. Inspect the East remote or related hardware.

The app should never claim it has mechanically proven the remote is defective.

---

# 50. Diagnostics Home Screen

Portrait diagnostics should use full-width vertically stacked cards.

Example:

```text
BATTERY RESULTS

┌─────────────────────────────┐
│ BATTERY 1                   │
│ Typical: 5h 18m             │
│ Stable                      │
│ 24 reliable cycles          │
└─────────────────────────────┘

┌─────────────────────────────┐
│ BATTERY 2                   │
│ Typical: 5h 06m             │
│ Stable                      │
│ 26 reliable cycles          │
└─────────────────────────────┘

┌─────────────────────────────┐
│ BATTERY 3                   │
│ Typical: 3h 52m             │
│ Declining                   │
│ 21 reliable cycles          │
└─────────────────────────────┘

┌─────────────────────────────┐
│ BATTERY 4                   │
│ Typical: 5h 21m             │
│ Stable                      │
└─────────────────────────────┘

[ WEST VS EAST ]

[ DATA QUALITY ]

[ EVENT HISTORY ]

[ EXPORT ]
```

Diagnostic screens may scroll vertically.

---

# 51. Battery Detail Screen

Recommended order:

```text
BATTERY 3

DECLINING

Typical runtime
4h 11m

Recent runtime
3h 37m

Historical baseline
5h 08m

Reliable cycles
31

────────────

RUNTIME OVER TIME

[GRAPH]

────────────

WEST / FRONT
4h 18m

EAST / BACK
4h 03m

────────────

RECENT CYCLES
...
```

---

# 52. Runtime Graph

Graph:

- horizontal axis: date;
- vertical axis: active runtime.

Point shapes should encode confidence:

- solid circle = exact;
- hollow circle = estimated or shift-interrupted;
- X = suspicious-high.

Do not rely on color alone.

The graph should use most of the available screen width and a moderate height.

---

# 53. Data Quality Screen

Example:

```text
DATA QUALITY

Exact cycles                48
Shift-interrupted cycles     7
Unknown gaps                14
Corrections                  5
Suspicious-high records      3
```

Suggested overall labels:

- LIMITED
- DEVELOPING
- GOOD
- STRONG

The app should never present weak evidence as a strong conclusion.

---

# 54. Event History Screen

Portrait list:

```text
TODAY

10:47 AM
WEST / FRONT

Battery 2 died.
Battery 4 installed.

────────────────────

8:16 AM
EAST / BACK

Battery 1 confirmed.

────────────────────

5:04 AM
BOTH REMOTES

Shift-start state confirmed.
```

Corrections should also be shown clearly.

---

# 55. Event-Sourced Architecture

The database should store what happened, not just calculated runtimes.

Preferred:

```text
06:03
Battery 2 installed in West.

11:15
Battery 2 removed dead from West.
```

Derived:

```text
Battery 2
West
Runtime: 5h 12m
```

This allows future analysis algorithms to recalculate all historical statistics.

---

# 56. Event Groups

A normal battery change creates related events.

Example:

```text
Action Group: ABC123

10:57:03
BATTERY_REMOVED_DEAD
Battery 2
West

10:57:03
BATTERY_INSTALLED
Battery 4
West
```

Both share an `action_group_id`.

Undo reverses the whole action group logically.

---

# 57. Event Types

Recommended initial event types:

```text
BATTERY_INSTALLED
BATTERY_REMOVED_DEAD
STATE_CONFIRMED
STATE_CORRECTED
STATE_MARKED_UNKNOWN
UNDO_ACTION
SYSTEM_TIME_WARNING
```

---

# 58. Database Schema

## BatteryEntity

```text
batteryId: Int
displayNumber: Int
active: Boolean
createdAt: Long
retiredAt: Long?
```

## RemoteEntity

```text
remoteId: String
displayName: String
shortName: String
physicalPosition: ScreenPosition
active: Boolean
```

## EventEntity

```text
eventId: UUID
actionGroupId: UUID?
timestampEpochMillis: Long

remoteId: String?
batteryId: Int?

eventType: EventType

previousBatteryId: Int?
newBatteryId: Int?

targetActionGroupId: UUID?

createdByAppVersion: String

wallClockAnomalyDetected: Boolean

notes: String?
```

---

# 59. Derived Cycle Model

```text
DerivedCycle

cycleId
batteryId
remoteId

startTimestamp
endTimestamp

minimumActiveRuntime
maximumActiveRuntime

classification

isHighOutlier
isShortRuntimeEvent

includedInPrimaryStatistics

startEventId
endEventId
```

These records may be generated dynamically or cached.

Raw events remain authoritative.

---

# 60. Recommended Android Architecture

```text
┌───────────────────────────────┐
│        Compose UI             │
└───────────────┬───────────────┘
                │
┌───────────────▼───────────────┐
│          ViewModels           │
└───────────────┬───────────────┘
                │
┌───────────────▼───────────────┐
│        Domain / Use Cases     │
│                               │
│ BatteryChangeUseCase          │
│ CorrectStateUseCase           │
│ UndoUseCase                   │
│ RuntimeAnalysisEngine         │
│ ShiftEngine                   │
│ DiagnosticEngine              │
└───────────────┬───────────────┘
                │
┌───────────────▼───────────────┐
│          Repository           │
└───────────────┬───────────────┘
                │
        ┌───────┴─────────┐
        │                 │
┌───────▼─────┐   ┌───────▼──────┐
│ Room/SQLite │   │   DataStore   │
└─────────────┘   └──────────────┘
```

---

# 61. Suggested Project Structure

```text
com.company.cranebatterytracker

├── data
│   ├── database
│   ├── repository
│   └── settings
│
├── domain
│   ├── model
│   ├── usecase
│   └── analysis
│
├── ui
│   ├── main
│   ├── batterypicker
│   ├── correction
│   ├── diagnostics
│   ├── history
│   └── settings
│
├── backup
│   ├── BackupManager
│   └── CsvExporter
│
└── MainActivity
```

---

# 62. Event Reducer

A deterministic `EventReducer` should reconstruct current state from history.

Initial state:

```text
WEST = UNKNOWN
EAST = UNKNOWN
```

Example transitions:

```text
BATTERY_INSTALLED West 2
→ WEST = 2

STATE_CONFIRMED West 2
→ WEST remains 2, confidence refreshed

STATE_CORRECTED West 2→4
→ prior interval becomes uncertain
→ WEST = 4

STATE_MARKED_UNKNOWN West
→ WEST = UNKNOWN
```

Undo actions logically reverse target action groups before reconstruction.

Given the same database, the reducer must always produce the same current state.

---

# 63. Transaction Safety

A battery change should occur in one Room transaction.

Conceptually:

```text
begin transaction

validate remote state
validate replacement battery availability

insert BATTERY_REMOVED_DEAD
insert BATTERY_INSTALLED

commit
```

If anything fails:

```text
rollback
```

The UI should not display the new battery until the transaction succeeds.

---

# 64. Collision Transaction

If correcting East to Battery 1 while Battery 1 is currently recorded in West:

```text
begin transaction

mark West unknown
confirm Battery 1 in East

commit
```

Either both changes occur or neither does.

There must never be a persisted intermediate state where Battery 1 exists in both remotes.

---

# 65. Restart and Crash Recovery

When the app starts:

1. Open the database.
2. Load events.
3. Apply undo and correction logic.
4. Reconstruct West state.
5. Reconstruct East state.
6. Evaluate freshness.
7. Display the main screen.

No “Resume tracking?” prompt should exist.

The database is the source of truth.

---

# 66. Device Reboot

After reboot, restore the last known battery states.

If a shift boundary has passed, display the state as stale rather than freshly confirmed.

Example:

```text
WEST
BATTERY 3

NOT CONFIRMED THIS SHIFT
```

---

# 67. Device Clock Problems

Runtime depends partly on Android system time.

Where practical, store:

- wall-clock timestamp;
- Android monotonic elapsed time.

If a large unexplained clock jump occurs, affected runtime should be flagged.

The app must never silently generate negative or absurdly long durations.

---

# 68. Local-Only Operation

The app must work without:

- Wi-Fi;
- cellular data;
- cloud services;
- external APIs;
- company servers;
- user accounts.

The Android manifest should ideally omit Internet permission.

---

# 69. Backup

Automatic local backups should be created.

Recommended:

- recent daily backups;
- older periodic archive backups.

Because the database will be small, retention can be generous.

---

# 70. Export

Provide manual export through the Android document picker.

Formats:

- raw event CSV;
- derived cycle CSV;
- SQLite database copy.

---

# 71. CSV Raw Event Fields

Suggested:

```text
event_id
action_group_id
timestamp
remote
battery
event_type
previous_battery
new_battery
undone
```

---

# 72. CSV Derived Cycle Fields

Suggested:

```text
cycle_id
battery
remote
start_time
end_time
minimum_runtime_minutes
maximum_runtime_minutes
classification
high_outlier
short_runtime
included_in_primary_statistics
```

---

# 73. Database Migrations

Production builds must not use destructive migration fallback.

Schema updates must preserve historical data.

Once Version 2 exists, migration tests should become part of the automated suite.

---

# 74. Kiosk / Appliance Behaviour

The tablet should behave like a dedicated tool.

Recommended:

- portrait orientation locked;
- app launches automatically after restart where practical;
- screen remains awake while powered;
- full-screen or near full-screen operation;
- accidental navigation away minimized;
- diagnostics return automatically to tracker after inactivity;
- Android screen pinning or kiosk mode may be used.

The desired experience is:

> Walk to the battery station and the tracker is already there.

---

# 75. Inactivity Return

If diagnostics or history are left open, automatically return to the main tracker after approximately 2–5 minutes of inactivity.

Do not cancel an active battery-change flow after only a few seconds.

---

# 76. No Gesture-Only Controls

Normal operation must not depend on:

- swipes;
- hidden drawers;
- pinch gestures;
- long gesture chains.

Necessary actions must have visible buttons.

---

# 77. No Color-Only Meaning

Always pair color with text.

Use:

- STABLE
- DECLINING
- UNKNOWN
- NOT ENOUGH DATA

rather than only red/green indicators.

---

# 78. Administrative Settings

Settings should be harder to reach accidentally.

Possible path:

`Diagnostics → Settings`

Potential settings:

- shift start times;
- ambiguous shift-end ranges;
- working days;
- battery management;
- remote names;
- backup settings;
- optional admin PIN.

Normal tracking should never require the PIN.

---

# 79. What Version 1 Should Not Require

Do not require:

- operator name;
- employee number;
- shift selection;
- reason for change;
- charger number;
- charging duration;
- battery voltage;
- work order;
- comments.

Every required field reduces adoption.

---

# 80. Failure Scenario: Nobody Uses the App for a Shift

Tablet says:

```text
WEST: Battery 1
```

Nobody records anything.

Next shift sees Battery 4.

Correct result:

```text
Battery 1
→ UNKNOWN GAP →
Battery 4 confirmed now
```

Incorrect:

```text
Battery 1 runtime = 13 hours
```

---

# 81. Failure Scenario: Only One Person Uses It

Data may look like:

```text
UNKNOWN

Battery 2
Exact runtime: 4h 52m

UNKNOWN

Battery 4
Exact runtime: 5h 14m

UNKNOWN

Battery 2
Exact runtime: 4h 46m

UNKNOWN
```

This is successful operation.

Those exact measurements remain useful.

---

# 82. Failure Scenario: Wrong Battery Selected

Operator meant Battery 1 but taps Battery 2.

They press:

`WRONG BUTTON / UNDO`

The original action is logically reversed.

They then record Battery 1 correctly.

---

# 83. Failure Scenario: Wrong Remote Selected

Operator records East but meant West.

Undo restores East.

They then record the correct West change.

The physical left/right layout should already reduce this error.

---

# 84. Failure Scenario: Duplicate Battery

West is recorded as Battery 1.

Someone tries to assign Battery 1 to East.

The app must stop the impossible state and offer a correction path.

---

# 85. Failure Scenario: Unrealistically Long Runtime

Battery 2 normally lasts about five hours.

A cycle appears to last thirteen hours.

The app should:

1. preserve raw events;
2. mark the cycle suspicious;
3. exclude it from primary statistics;
4. show it in diagnostics;
5. reconsider it later if similar measurements repeat.

---

# 86. Failure Scenario: Very Short Runtime

Battery 4 usually lasts about five hours.

It dies after 50 minutes.

Keep the measurement.

If this repeats, Battery 4 should become a likely replacement candidate.

---

# 87. Failure Scenario: Same Batteries Perform Worse in East

Example:

```text
Battery 1
West: 5.2h
East: 4.4h

Battery 2
West: 5.1h
East: 4.3h

Battery 3
West: 5.4h
East: 4.5h

Battery 4
West: 5.3h
East: 4.4h
```

Expected diagnostic:

> East / Back consistently shows shorter runtime across multiple batteries. Inspect the remote or related hardware.

---

# 88. Failure Scenario: One Battery Performs Poorly Everywhere

Example:

```text
Battery 3
West: 3.6h
East: 3.7h

Other batteries:
approximately 5.2h
```

Expected diagnostic:

> Battery 3 consistently underperforms in both remotes and is a strong replacement candidate.

---

# 89. Testing Philosophy

The most important tests are not perfect-usage tests.

They are messy-usage tests.

The key question is:

> What happens after people stop using the app correctly?

---

# 90. Event Reducer Tests

Test:

- initial state unknown;
- install battery;
- confirm battery;
- correct battery;
- mark unknown;
- undo installation;
- undo correction;
- event ordering;
- restart reconstruction.

---

# 91. Battery Ownership Tests

Test:

- same battery cannot exist in both remotes;
- collision correction makes the displaced remote unknown;
- unrelated batteries remain unchanged;
- collision handling is transactional.

---

# 92. Normal Cycle Tests

Test:

- install and remove within active shift;
- correct runtime;
- correct remote;
- correct battery;
- classification equals `EXACT`.

---

# 93. Unknown Gap Tests

Critical test:

```text
06:00 Battery 1 installed West.
No events.
17:00 state corrected to Battery 4.
```

Expected:

- no exact Battery 1 runtime;
- unknown interval created;
- Battery 4 starts a new known state at 17:00.

---

# 94. Shift Tests

Test:

- day shift exact cycle;
- night shift crossing midnight;
- cycle crossing downtime;
- Friday day shift;
- Friday night into Saturday;
- weekend gap;
- Monday restart.

---

# 95. Ambiguous End Tests

Example:

```text
Battery installed 12:30 PM.
Removed next day 5:45 AM.
```

Verify:

- minimum runtime;
- maximum runtime;
- `SHIFT_INTERRUPTED`;
- excluded from primary exact median.

---

# 96. Outlier Tests

Test:

- no outlier classification before enough samples;
- high cycle after stable baseline;
- high cycle excluded from primary median;
- raw event preserved;
- old outlier reclassified when distribution changes.

---

# 97. Short Runtime Tests

Test:

- short exact runtime is preserved;
- flagged as short;
- not converted to unknown;
- repeated short runtimes affect trend.

---

# 98. Remote Comparison Tests

Synthetic data:

## Equal performance

Expected:

`No remote warning.`

## All batteries about 45 minutes worse in East

Expected:

`East warning after sufficient samples.`

## Only Battery 3 worse in East

Expected:

`Battery-specific issue more likely; no strong East fleet-wide warning.`

---

# 99. Undo Tests

Test:

- normal change;
- undo;
- state restored;
- app restart after undo;
- state still restored;
- undone events remain visible in history.

---

# 100. Correction Tests

Test:

- incorrect current state corrected;
- previous open cycle becomes unusable;
- new battery creates clean anchor;
- no fake dead event generated.

---

# 101. Data Quality Tests

Test:

- few cycles = Limited;
- more coverage = Developing;
- broad clean coverage = Good;
- many unknown gaps prevent Strong;
- no strong conclusions with weak evidence.

---

# 102. UI Tests

Verify:

- West always left;
- East always right;
- main screen does not scroll;
- normal change requires two primary taps;
- 2 × 2 battery picker;
- unavailable battery cannot be selected normally;
- correction path works;
- undo is visible;
- unknown state is understandable;
- shift banner does not block battery changes;
- diagnostics return to main screen after inactivity.

---

# 103. First-Time User Test

Mount the tablet vertically.

Give someone unfamiliar with the app this instruction:

> “The West / Front crane had Battery 3. It died. You put Battery 1 in.”

Do not explain the interface.

Success means they naturally:

1. use the left / West side;
2. tap Change Battery;
3. tap 1;
4. understand the action is complete.

Repeat for East.

If this fails, the UI needs redesign.

---

# 104. Glove Test

Test with the actual work gloves used in the area.

Verify:

- buttons are easy to hit;
- numbers are readable;
- West/East distinction is obvious;
- double taps do not create duplicate events.

---

# 105. Hostile Usage Test

Simulate:

- five operators;
- one careful user;
- four inconsistent users;
- missed changes;
- wrong selections;
- shift gaps;
- ignored confirmations;
- one fake long apparent runtime;
- one real short cycle.

Expected:

> The app produces fewer complete cycles, but the complete cycles that remain are trustworthy.

If the app instead invents many precise runtimes, the design has failed.

---

# 106. Performance Requirements

The dataset is tiny by modern standards.

The app should feel immediate.

Targets:

- main screen appears quickly;
- picker opens instantly;
- save feedback feels immediate;
- diagnostics load quickly;
- years of history remain trivial for SQLite.

Correctness matters more than exotic optimization.

---

# 107. Field Trial

Initial deployment should focus on observing actual usage rather than immediately trusting every diagnostic.

Watch for:

- whether people understand West/East;
- whether people use Undo;
- how often state corrections happen;
- how often unknown gaps occur;
- how often the shift confirmation is used;
- whether legitimate runtimes are being flagged incorrectly;
- whether batteries are ever swapped for reasons other than being dead.

---

# 108. Important Field-Trial Question

Version 1 assumes a normal battery replacement means the removed battery was effectively dead or depleted.

During field testing, verify whether that is normally true.

If operators frequently swap batteries for unrelated reasons, a future version may need:

- BATTERY DEAD
- SWAPPED FOR OTHER REASON

Do not add that complexity unless real usage proves it necessary.

---

# 109. Phase 0: Foundation

Build:

- Android project;
- Compose navigation;
- Room;
- DataStore;
- battery seed records;
- remote seed records;
- repository layer;
- domain models;
- basic test framework.

Acceptance:

- app launches;
- database persists;
- four batteries exist;
- West and East exist;
- no internet required.

---

# 110. Phase 1: Portrait Core Tracker

Build:

- portrait-only layout;
- West tall card permanently left;
- East tall card permanently right;
- large battery numbers;
- Change Battery buttons;
- full-screen 2 × 2 battery picker;
- no scrolling on main screen;
- persistent current state;
- normal transactional battery change.

Acceptance:

> A first-time user can correctly change either crane battery without instruction.

---

# 111. Phase 2: Guardrails and Recovery

Build:

- battery collision prevention;
- Undo;
- state correction;
- explicit unknown state;
- current battery tap correction;
- duplicate-tap protection;
- clear save/error feedback;
- deterministic event reducer.

Acceptance:

> Common operator mistakes cannot silently corrupt the current state.

---

# 112. Phase 3: Shift-Aware Engine

Build:

- day schedule;
- night schedule;
- midnight handling;
- weekdays/weekends;
- ambiguous shift-end windows;
- state staleness;
- shift confirmation banner;
- minimum/maximum active runtime calculation;
- exact vs shift-interrupted classification.

Acceptance:

> Overnight and weekend gaps cannot become absurd battery runtimes simply because nobody touched the tablet.

---

# 113. Phase 4: Cycle and Confidence Engine

Build:

- derived cycles;
- exact cycles;
- unknown-gap handling;
- confirmed minimums;
- suspicious-high detection;
- short-runtime detection;
- medians;
- MAD;
- dynamic reclassification.

Acceptance:

> One missed battery change cannot poison later statistics.

---

# 114. Phase 5: Battery Diagnostics

Build:

- battery overview;
- battery detail;
- reliable cycle count;
- typical runtime;
- recent runtime;
- historical baseline;
- trend;
- short-runtime count;
- dead-event count;
- runtime graph.

Acceptance:

> The app can distinguish insufficient evidence from a credible battery degradation pattern.

---

# 115. Phase 6: Remote Diagnostics

Build:

- West vs East comparison;
- battery-controlled paired comparison;
- sample thresholds;
- remote warnings;
- human-readable explanations.

Acceptance:

> A remote-specific pattern across multiple batteries becomes visible without being confused with one bad battery.

---

# 116. Phase 7: History and Data Quality

Build:

- human-readable event history;
- correction history;
- unknown-gap counts;
- exact/estimated counts;
- data-quality summary;
- evidence explanations.

Acceptance:

> A supervisor can understand why the app reached a conclusion.

---

# 117. Phase 8: Backup and Export

Build:

- automatic local database backups;
- retention rules;
- manual database export;
- raw-event CSV;
- derived-cycle CSV;
- export feedback.

Acceptance:

> Data can be removed from the tablet for deeper analysis without internet access.

---

# 118. Phase 9: Dedicated Tablet Behaviour

Build:

- portrait orientation locked;
- keep screen awake while powered;
- return to main tracker after diagnostic inactivity;
- optional launch after reboot;
- optional screen pinning/kiosk setup.

Acceptance:

> The tablet behaves primarily as a battery-tracking appliance.

---

# 119. Phase 10: Hardening

Run:

- full unit suite;
- randomized event-sequence tests;
- crash/restart tests;
- database migration tests;
- clock-change tests;
- duplicate-tap tests;
- unknown-gap tests;
- collision tests;
- months of synthetic event data.

---

# 120. Phase 11: Field Trial

Deploy internally.

Measure:

- normal changes;
- corrections;
- unknown resets;
- undo events;
- collisions prevented;
- exact cycles;
- shift-interrupted cycles.

Use the field data to tune thresholds.

---

# 121. Deferred Features

Do not initially build:

- operator accounts;
- cloud synchronization;
- server backend;
- web dashboard;
- push notifications;
- charger-slot tracking;
- battery voltage entry;
- Bluetooth detection;
- NFC;
- barcode scanning;
- usage-intensity tracking;
- employee productivity tracking;
- complex machine-learning models.

These can be evaluated later.

---

# 122. Possible Future Charger Tracking

If battery results remain unexplained, charger tracking may become valuable.

Example:

```text
Battery 1 charged in Slot A
Battery 2 charged in Slot B
```

This could reveal a charging problem.

Only add this if it can be captured with almost no extra operator effort.

---

# 123. Possible Future NFC

NFC tags on the batteries could eventually allow an operator to tap the physical battery against the tablet instead of selecting 1–4.

This could reduce selection mistakes further.

It is not required for Version 1.

---

# 124. Definition of Done

The app is ready for real deployment when all of the following are true:

1. A first-time user can record a normal battery change without instruction.
2. West is always left.
3. East is always right.
4. Normal replacement requires two primary taps.
5. Current state survives restart.
6. No network is required.
7. The same battery cannot silently exist in both remotes.
8. Wrong state can be easily corrected.
9. Unknown is a supported state.
10. Missed changes create uncertainty instead of false runtime.
11. Shift gaps cannot create absurd overnight or weekend runtimes.
12. Undo is simple.
13. Primary statistics use robust measurements.
14. Suspicious high values do not distort the normal result.
15. Repeated short cycles remain visible.
16. Battery degradation can be detected.
17. West and East can be compared.
18. Weak evidence produces cautious wording.
19. Raw events are preserved.
20. Backups are local.
21. Data can be exported.
22. The main portrait screen works comfortably with gloves.
23. The app remains useful even if only one operator out of five uses it properly.

---

# 125. Final System Behaviour

Under ideal usage:

```text
Battery change
→ two taps
→ exact cycle
→ strong diagnostics
```

Under mediocre usage:

```text
Some changes recorded
Some missed
→ exact measurements remain usable
→ uncertain periods excluded
```

Under poor usage:

```text
Most operators ignore the app
One operator occasionally uses it correctly
→ smaller dataset
→ still trustworthy
```

That final case is the defining requirement.

The visible app should remain extremely simple.

The internal system should be conservative, self-correcting, fault-tolerant, and honest about uncertainty.

The operator tells the app:

> **Which battery did you put in?**

The software handles:

- timestamps;
- shift awareness;
- state confidence;
- runtime calculations;
- outlier detection;
- corrections;
- unknown gaps;
- battery health;
- remote comparison;
- data quality;
- backup;
- diagnostics.

The core principle remains:

> **Never turn missing data into bad data.**

And the central UX principle is:

> **Someone seeing the vertically mounted tablet for the first time should understand what to press before they have time to wonder how the app works.**
