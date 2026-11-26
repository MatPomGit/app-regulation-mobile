# ARC-ADHD native Android application

Status: canonical implementation scaffold with an interactive Signal Gate engineering preview. It is not a participant-ready research build.

Technology baseline:
- Kotlin/JVM Android application;
- Jetpack Compose for core UI;
- pure-Kotlin domain/state-machine layer;
- native SQLite for the durable engineering journal; participant telemetry remains gated;
- CameraX + MediaPipe/TFLite planned for optional on-device vision;
- ARCore planned as an optional Paper 4 module;
- Android BLE/GATT integration planned for optional wearable modules;
- WorkManager planned for durable deferred upload after local commit.

## Architectural rule

The core scientific state machine must compile and be testable without camera, AR, BLE, network or vendor SDKs. Optional research modules depend on core contracts; core never depends on optional modules.

## Current scaffold

The project now contains a compile-time article-module contract plus dedicated source sets for Papers 1–5. Each source set declares its capabilities and expected measurement outputs while sharing the same core application shell. The primary Compose Signal Gate now uses the monotonic timed task engine and the canonical Core-cycle coordinator. Completed engineering trials are committed to the private SQLite journal before the UI advances. Participant envelopes/export, validated recovery scoring, camera inference, ARCore and EEG acquisition remain gated before participant use.

## Migration order

1. Qualify the timed Compose flow, durable trial writes and restart behavior on physical Android devices.
2. Implement mapping-matched target-rule calibration and then connect validated recovery scoring without outcome-driven tuning.
3. Persist typed participant task/recovery events only after the participant envelopes and privacy/export allowlists are frozen.
4. Run the BYOD benchmark and freeze the qualification policy from real-device evidence.
5. Add bounded audio/haptics and lifecycle interruption handling.
6. Add CameraX/MediaPipe optional facial/gaze measurement.
7. Add ARCore optional embodied/AR module.
8. Add BLE/wearable adapters only after device-specific measurement contracts are frozen.

Legacy Unity is outside this Android repository.

## Article build variants

The application has five compile-time flavors:

- `core` — Paper 1, core closed-loop intervention only;
- `facial` — Paper 2, core + facial/gaze measurement boundary;
- `bilateral` — Paper 3, core + bilateral-audio study boundary;
- `embodiedAr` — Paper 4, core + embodied/AR boundary;
- `eeg` — Paper 5, core + wearable EEG boundary.

See [variant mapping](VARIANTS.md).

## APK builds

The Android engineering validation workflow checks all five debug variants on pull requests and main. No APK distribution workflow is configured in this repository.

Local task examples:

```bash
gradle :app:assembleCoreDebug
gradle :app:assembleFacialDebug
gradle :app:assembleBilateralDebug
gradle :app:assembleEmbodiedArDebug
gradle :app:assembleEegDebug
```

## Source-set layout

```text
app/src/main/          shared shell and scientific contracts
app/src/core/          Paper 1 module descriptor
app/src/facial/        Paper 2 camera/facial-gaze descriptor
app/src/bilateral/     Paper 3 stereo-audio descriptor
app/src/embodiedAr/    Paper 4 movement/AR descriptor
app/src/eeg/           Paper 5 BLE/EEG descriptor
```

Each flavor provides the same `CurrentArticleModule` symbol, so the selected APK can only see its own article-specific module at compile time.

## Distribution

No Google Play publication workflow is configured. Signed distribution requires a separate release decision and verified artifacts.

## Timed Core Signal Gate engineering flow

Open any flavor and choose **Wypróbuj Signal Gate**. The primary Compose flow
contains eight fixed timed trials using the reference A/B mappings. GO responses
are explicit taps; NO-GO behavior is measured as withholding until the deterministic
deadline. Every completed trial is committed to the private engineering SQLite
journal before the next trial is shown.

After trial 4 the engineering-only challenge gate invokes the canonical Core
sequence: challenge, regulation pause, orient, strategy selection/execution and
resume. The flow intentionally does **not** fabricate a recovery score: because
the eight-trial engineering run lacks the frozen mapping-matched Rule B reference,
recovery is recorded as data-invalid and the session continues without T_rec.
The final task enters reflection and then SESSION_COMPLETE.

See [SIGNAL_GATE_PREVIEW.md](SIGNAL_GATE_PREVIEW.md) and
[PERSISTENCE.md](PERSISTENCE.md). No participant research envelope is created and
no trial enters the delivery queue or network uploader.

## Session controller explorer

Expand **Narzędzia techniczne**, then choose **Podgląd przebiegu sesji** to inspect the canonical session graph.
Buttons simulate service events; they do not run qualification, obtain consent,
measure recovery or produce participant records. Engineering transitions are committed locally before the UI advances. Core and active-control demonstrations share
the same graph, with challenge/regulation/recovery entries blocked for active
control. Pause and stop remain outside the scrollable event list.

See [SESSION_CONTROLLER.md](SESSION_CONTROLLER.md) for input contracts,
recovery-context validation, source generation and verification commands.

## Durable engineering sessions

The session explorer retains its committed state and immutable transition history
in a private SQLite database. A transaction commits each transition, pending queue
record and checkpoint together. Initialization verifies deterministic replay.
After process restart, continuation requires explicit interruption handling; an
open recovery context is aborted. After a device reboot, the previous journal
is available for inspection only and a new demonstration can be started.

There is no network uploader or participant data collection. Completed timed
Signal Gate trials are retained in the separate immutable `engineering_trials`
table and are explicitly excluded from the delivery queue. See
[PERSISTENCE.md](PERSISTENCE.md) for the exact engineering boundary, migration,
failure policy, test coverage and device acceptance checks.

## Responsive and accessible UI

All three screens use a shared, scrollable, width-limited layout and fixed light/
dark palettes following the system theme. Pause/stop remain outside the content
scroll; controls stack on narrow windows or large font settings. Headings, pane
names and polite status announcements support accessibility services. The
engineering Signal Gate preview distinguishes stimuli with text and shape as
well as color. See [UI_ACCESSIBILITY.md](UI_ACCESSIBILITY.md) for verification
and the physical-device acceptance matrix.

## User instructions and agreement

Every variant exposes a dedicated Polish guide from Home. Before opening either
preview, users explicitly accept the described technical demonstration and local
storage; refusal leaves the guide accessible. A caregiver also confirms the child's
willingness. Agreement can be withdrawn from Home and is requested again after
process restart. It is not a research participation consent. See
[USER_INFORMATION.md](USER_INFORMATION.md) for scope, data handling and tests.

## Home information hierarchy

The highlighted card offers one primary action: **Przeczytaj informacje i zdecyduj**
before agreement, **Wypróbuj Signal Gate** afterwards. Instructions, agreement
information and withdrawal are supporting actions outside technical tools.
The explorer and module details are collapsed under **Narzędzia techniczne**;
storage errors still appear when that section is closed. User-facing task action
is **Reaguj** and all simulated event labels are Polish. The linked debug APK
predates this source update until a new manual build is produced.

## Playable engineering game layer

The Misja kolorów preview includes three selectable vector robot companions,
an eight-gate route map, and deterministic keepsakes after 2/4/8 completed
trials. Keepsakes are independent of correctness/speed and accumulate across
retries on the same preview screen; pause/stop does not revoke them.
Screen exit/recreation clears the in-memory album. Polish user instructions
describe these boundaries. These features are implemented but their appeal to
children has not been measured; the linked older APK does not include them.
