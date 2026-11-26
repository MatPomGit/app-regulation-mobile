# Signal Gate: native engineering flow

Status: timed engineering implementation connected to the canonical Core state
machine. Participant readiness remains disabled. The legacy untimed
`SignalGatePreview` model remains only as a reference/test artifact.

## Primary runtime path

| Element | Source | Current behavior |
| --- | --- | --- |
| Timed task | `task/TimedSignalGate.kt` | Monotonic trial timing, stable trial IDs, response deadlines, timeout/withhold, explicit pause/resume |
| Engineering challenge gate | `task/EngineeringSignalGatePolicy.kt` | Four-trial demo-only gate, explicitly separate from the Phase 0 candidate policy |
| Core orchestration | `session/CoreCycleCoordinator.kt` | Challenge, pause, orient, strategy, recovery handling, resume and session completion |
| Compose flow | `ui/TimedCoreSignalGateScreen.kt` | Timed stimulus presentation, regulation screens, reflection and terminal summary |
| Persistence bridge | `platform/storage/EngineeringJournalRuntime.kt` | Durable-write-before-advance for completed trials and persisted Core transitions |
| Trial storage | `storage/SessionJournal.kt` | Immutable engineering trial rows with hashes and contiguous indices |

The eight reference trials keep the A/B rule mapping used by the earlier preview.
The active response window is currently 1500 ms in the engineering flow. This is
not a qualified or frozen Phase 0 BYOD parameter.

## Response semantics

A GO response is an explicit **Reaguj** tap before the trial deadline. A NO-GO
response is measured as no tap until timeout. There is no explicit "Pomiń"
button in the primary timed flow.

Each completed trial stores:

- task-run ID and trial ID;
- trial index;
- active rule and stimulus;
- response kind;
- expected GO/NO-GO class;
- correctness;
- monotonic stimulus and response timestamps;
- response latency.

The record is engineering-only. It contains no participant identity and is not a
`trial_completed` participant research envelope.

## Rule switch and Core sequence

After trial 4, an engineering-only eligibility policy checks the four completed
reference trials. When it passes, the runtime persists:

`BASELINE_TASK -> CHALLENGE_PENDING -> CHALLENGE_ACTIVE -> REGULATION_PAUSE
-> ORIENT -> STRATEGY_SELECTION -> STRATEGY_EXECUTION -> RECOVERY_MONITORING`.

The engineering run then deliberately transitions to
`RECOVERY_DATA_INVALID` because it does not yet contain the frozen,
mapping-matched target-rule reference required for scientifically valid recovery
scoring. The application therefore does not invent (T_rec) from this short
demonstration. After explicit continuation, the controller returns through
`RESUME_GOAL` to `BASELINE_TASK`, Rule B begins, and trials 5-8 run.

After trial 8, the controller enters `SESSION_REFLECTION` and then
`SESSION_COMPLETE`.

## Persistence contract

A completed timed trial is written to SQLite before the UI advances to the next
trial. If the write cannot be verified, further task progression is blocked.

The engineering trial table is not connected to `journal_queue`. There is no
network transport for these rows. Participant event envelopes still require
frozen field allowlists, privacy/governance approval and typed validators.

Pause is also persisted in the session controller. Resumption is explicit and the
timed engine shifts the active trial timestamps so paused wall-clock time does not
count against the response window. If the app is backgrounded while the previous
trial is still being committed, the next prepared trial is re-anchored after the
commit and immediately paused before it can consume response time in the background.

## Legacy untimed model

`task/SignalGatePreview.kt` and `ui/SignalGatePreviewScreen.kt` are retained
for regression/reference purposes. They no longer represent the primary Home
action. They must not be used as evidence that the timed Core flow is qualified.

## Automated verification

From `app/mobile-android-kotlin`:

    gradle :app:testCoreDebugUnitTest :app:lintCoreDebug :app:assembleCoreDebug

Tests cover deterministic timing, timeout/withhold, stale callbacks, pause-adjusted
deadlines, rule switching, engineering challenge eligibility, Core-cycle
transitions, durable trial persistence, idempotent retries, corruption detection
and database migration.

Physical-device checks remain mandatory for actual timing accuracy, lifecycle
behavior, accessibility and storage durability.
