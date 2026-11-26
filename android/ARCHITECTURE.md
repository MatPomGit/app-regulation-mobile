# Native Android architecture

Static Compose-first UI audit (2026-11-11): screen inventory from the earlier project. The existing screens already use Compose; physical usability and device acceptance remain open.

## Modules and dependency direction

The implemented project uses a single Gradle app module with five product flavors (`core`, `facial`, `bilateral`, `embodiedAr`, `eeg`). The layout below is a **target ownership map**, not a declaration that every package or SDK integration exists. Split modules only when isolation or measured build performance justifies it.

```text
ui/                 Jetpack Compose screens and lightweight drawing
core/               pure Kotlin states, decisions, IDs, interfaces
task/               Signal Gate and challenge eligibility
regulation/         pause/orient/strategy/recovery orchestration
safety/             stop/pause/overload precedence
telemetry/          event models and durable-queue interface
platform/           Android adapters
  camera/           CameraX
  vision/           MediaPipe/TFLite
  storage/          native SQLite adapter
  sensors/          IMU and device state
  connectivity/     BLE/GATT and vendor adapters
  work/             WorkManager upload
optional/
  facial_gaze/      Paper 2 measurement only until separately validated
  bilateral_audio/  Paper 3
  embodied_ar/      Paper 4 / ARCore
  eeg/              Paper 5 observation-first
```

Dependency rule: UI and optional modules may depend on core contracts. Core must remain Android-SDK-light and must not depend on optional modules.

## Performance model

The main thread renders UI and applies already-decided state. Camera analysis, ML inference, persistence and peripheral IO use bounded background execution. Backpressure is explicit: for real-time vision, prefer newest useful frame over an unbounded queue.

Scientific events are timestamped using a monotonic clock at the closest meaningful source boundary. Wall-clock time is metadata, not the ordering clock.

## Camera ownership

Core sessions do not require camera access. Facial/gaze and AR modules acquire camera only while their condition is active. First implementation assumes front-camera vision and rear-camera AR do not run simultaneously. Any concurrent-camera path requires device-specific qualification and timing validation.

## Data ownership

Frames are ephemeral by default. Optional vision modules emit approved derived features and quality metadata. The engineering journal stores simulated transitions and queue state in native SQLite. Participant records and upload remain disabled until storage protection and export allowlists are frozen.

## AR

ARCore is isolated behind an optional module. Failure or absence of ARCore must not prevent core sessions from running. AR tracking state is recorded as scientific/technical context for Paper 4.

## P03 stereo-audio engineering adapter

The optional `src/bilateral/` source set contains deterministic stereo PCM
generation, a pure Kotlin fail-closed route policy and an unconnected
`AudioTrack` bench adapter. Neither Core nor shared session logic references
it. No participant-facing playback, calibrated SPL or hardware stop-latency
claim exists. See `manuscripts/03-bilateral-audio/README.md`.

## Wearables

BLE/vendor SDK adapters expose timestamped samples/events through a narrow interface. Connection state, device clock, receive clock, sequence counters and synchronization events are recorded separately. The core controller does not depend on EEG in the first EEG study.


## Implemented engineering slice

`task/SignalGatePreview.kt` owns a pure Kotlin immutable preview state and
fixed rule sequence. `ui/SignalGatePreviewScreen.kt` renders controls only.
`MainActivity` owns the current preview and pauses it in `onPause`. UI callbacks
carry the rendered state and trial index, so a stale callback cannot advance
another trial or undo a pause/stop. This slice is not the scientific session
state machine in `config/core_session_state_machine.example.json`.

A technical session reducer, recovery-monitoring contracts, a qualification model and a durable engineering journal now exist. They do **not** amount to qualified real devices, participant telemetry, a frozen Rule B reference or participant-ready intervention. Before participant use, satisfy separate ethics, privacy, safety, timing, data-export and protocol gates.

## Session reducer

`session/SessionGraph.kt` is generated from the authoritative JSON contract.
`SessionController` executes its transitions with explicit global priority,
stored resume targets, monotonic transition metadata and recovery-context guards.
It is pure Kotlin/JVM. `SessionPreviewScreen` and `MainActivity` offer a developer
event explorer with Android background interruption. The reducer returns
transition records to the caller. `storage/SessionJournal` commits inputs, queue
records and checkpoints atomically, verifies replay and handles stable-ID retries.
`platform/storage/EngineeringJournalRuntime` serializes persistence on one
process-wide background executor and publishes only committed state to the UI.
The shared SQL schema runs on Android SQLite; JDBC is a test-only JVM adapter.

This is separate from the fixed Signal Gate preview. The explorer simulates
external service decisions and must not be connected to participant collection
until qualification, eligibility, recovery scoring and participant storage/export
contracts are implemented and validated. See [PERSISTENCE.md](PERSISTENCE.md).
