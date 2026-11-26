# Native UI accessibility and responsiveness

Status: implemented engineering UI; device interaction and rendered screenshots
still require verification. Participant readiness remains disabled.

## Implemented changes

- Shared PreviewFrame scrolls content independently of pause/stop. Maximum content
  width is 720 dp with 16 dp horizontal margins; split-screen/landscape use actual
  available constraints. Scaffold handles system-bar insets with edge-to-edge.
- Text uses Material sp typography and can wrap without fixed text heights or
  ellipsis. Safety controls stack below 360 dp content width or at fontScale >=
  1.5. Touch targets are at least 48 dp; primary and safety actions at least 56 dp.
- Light/dark themes follow the system with a stable teal/slate palette independent
  of wallpaper. Stop has an error color plus an explicit label. Status errors use
  both text and the error surface. Disabled controls retain Material semantics.
- Pane names and heading semantics expose screen changes and heading navigation.
  Polite live regions announce state/busy/outcome changes without interrupting
  speech. Text buttons supply their own names; decorative stimulus shapes are
  excluded from semantics to avoid duplicate reading.
- Signal Gate uses a blue circle and orange rounded square with external names.
  Text is no longer constrained inside a fixed circle. The timed engineering flow
  adds a visible progress bar for the active response window while preserving the
  redundant text/shape cue. The bar is not a score and does not encode correctness.
- Home highlights one action and puts the session explorer under technical tools.
  Polish event labels replace raw event IDs in simulation buttons; technical
  identifiers/counters are collapsed. Storage errors remain visible on Home.

The engineering UI does not establish therapeutic usability or accessibility
conformance. It has no flashing cue or new motion animation. Full translations
and participant wording still require their own content/design qualification.

## Automated verification

ArcContrastTest checks actual light/dark tokens: normal text pairs >= 4.5:1,
outline and both stimulus fills against surfaces >= 3:1. It covers primary,
secondary, status/error containers, background and surface text. Disabled control
opacity and rendered edge antialiasing are outside this token check. Existing
preview/reducer/journal tests verify behavior remains unchanged. All five flavors
use the same shared UI and are covered by the existing Android build workflow.

    gradle :app:testCoreDebugUnitTest :app:assembleCoreDebug

The contrast formula follows sRGB relative luminance. Guidance references:
[Android color contrast](https://support.google.com/accessibility/android/answer/7158390)
and [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics).

## Device acceptance matrix, not yet executed

Use each of Home, Signal Gate and Session explorer with:

| Dimension | Cases |
| --- | --- |
| Available window | 320 dp narrow, ordinary phone, tablet/wide, landscape, split screen |
| Font scale | 1.0, 1.5, 2.0 and largest supported device setting |
| Theme | Light and dark, system bars visible |
| Input | Touch, keyboard/D-pad, TalkBack, Switch Access |
| Session | Ready, active trial, rule switch, paused, stopped, complete, saving, read-only/error |

For every combination verify wrapping, no clipped labels, every action reachable,
no overlap with system bars, and visible pause/stop without scrolling the content.
On extreme short windows confirm safety controls fit; if not, record dimensions
and block acceptance rather than reducing text scale. Check visible focus,
screen/heading navigation, single announcements of stimulus identity, state changes
and correct enabled/disabled announcements. Inspect disabled reasons and error
recovery text. Run Accessibility Scanner and inspect screenshots for actual
contrast. Rotate/background: Signal Gate still resets/pauses per its contract;
session explorer restores the committed journal. No automatic task progression
or silent resume is introduced.

Record device/OS, actual window dimensions, font scale, theme and pass/fail with
screenshots in an engineering acceptance report. No physical-device or emulator
UI acceptance run is claimed by compilation or JVM tests alone.

## Information hierarchy and Polish presentation

Home has one highlighted card and one filled primary action: before agreement it
opens information/decision; after acceptance it opens Signal Gate. Instructions
and agreement/withdrawal remain immediately accessible as supporting actions.
Session explorer is under the collapsed Technical tools section. Storage errors
remain visible outside it; normal journal status appears inside the tools.

SectionCard groups instructions, agreement information and summary. No consent
content is hidden or removed; roles/confirmations start unselected, refusal and
withdrawal remain available. Information version is now engineering-pl-2 to match
the updated wording. The task button is Reaguj; recovery labels use Polish
wording. Session event IDs remain unchanged internally, with complete Polish
display labels and a safe unknown-code message. Raw module descriptions and
capability labels are replaced in Home by the existing Polish variant guide.
Launcher labels are Polish for every flavor.

Task progress, current rule and stimulus are visually separated. Trial sequence,
response mappings, controller, persistence and safety callbacks are unchanged.
Pause/stop stay in the fixed footer. Technical identifiers/counters are collapsed
in the explorer; its simulated events remain clearly labeled as simulations.

Additional device acceptance, not yet executed: confirm exactly one primary action
on Home in both agreement states; tools start collapsed and reopen accessibly;
instructions/withdrawal stay reachable; read-only errors show with tools closed;
all event labels wrap and are announced in Polish; no personal/internal payload
appears for unknown journal messages; summary/large fonts keep safety controls
visible. Inspect card surface contrast in light/dark modes and short landscape.
The existing APK download points to an older build until a new APK is produced.

## Child-facing engineering mission

Signal Gate now uses the Polish Misja kolorów robot narrative from the existing
optional task framing. A quiet completed-trial progress bar is labeled by visible
text and excluded from duplicate accessibility announcements. It reflects trial
count, not correctness or elapsed time, and stays visible when paused/stopped.
Completion acknowledges trying all signals rather than praising correctness.
Rule change and pause copy encourage taking time and freely stopping.

The current engineering flow now uses the deterministic 1500 ms response-window
candidate already implemented in the timed task engine. This timing is not a
qualified/frozen BYOD study parameter. The engineering presentation still requires
child usability and device checks; see
../../docs/child_engagement_design.md. Check narrow/landscape and large fonts
for additional card height and continued reachability of pinned safety controls.
No clinical benefit or child-interest result is claimed.

## Implemented child-facing game elements

The READY screen has three selectable original vector robots (Iskra/Luna/Piksel),
each with a different silhouette, color and Polish greeting. Selection semantics
announce the chosen companion. The eight-cell route is decorative with one readable
progress summary; it cannot skip trials. It appears before/at checkpoints and
pause/end, rather than adding clutter during an active stimulus.

Three deterministic keepsakes unlock at 2/4/8 completed trials, regardless of
correctness/speed. The album accumulates in Compose memory across retries on the
same screen and survives pause/stop there. Exiting/recreating the screen or process
clears it; no permanent progression is implied. No audio, haptics, flashing,
streaks or random rewards were introduced. MissionKeepsakeTest checks milestone
boundaries, accumulation across retries and invalid progress.

This is an implemented engineering game layer. It is not evidence of increased
interest or therapeutic effect. It changes the motivational presentation and must
be independently specified/frozen before any research use; task sequence and
controller contracts remain unchanged. Information is now engineering-pl-4.

Device acceptance: inspect robot selection with TalkBack/large fonts; each selected
robot must be visually distinct; start remains reachable; maps wrap correctly at
narrow widths; all three keepsakes appear at thresholds and remain on stop/retry;
exit/rotation clears the album as described; safety controls remain pinned.
