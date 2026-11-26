# Variant instructions and technical-use agreement

Status: user-facing Polish information and voluntary engineering-preview agreement
implemented in all five flavors. Participant readiness remains false. This is not
an approved participant consent, legal signature, GDPR lawful-basis selection or
permission to activate any future companion sensor module.

## User flow

Home exposes **Opis i instrukcja tego wariantu** and **Zgoda przed uruchomieniem**
without accepting anything. Instructions resolve by the selected compile-time
paper ID; unknown IDs fail instead of falling back to an unrelated description.
They cover purpose, implemented/planned functions, preparation, the eight Signal
Gate trials and rule switch, session explorer, storage/restart, permissions,
pause/stop, refusal and deletion through Android app storage settings.

| APK flavor | Guide | Variant-specific boundary |
| --- | --- | --- |
| core | P1, basic module | Timed engineering Signal Gate and Core state flow; no validated BYOD qualification, frozen recovery measure or participant telemetry |
| facial | P2, face/gaze | Front camera and inference inactive; no filming instructions for the preview |
| bilateral | P3, bilateral sound | No stimulus playback or channel test; no headphones required for previews |
| embodiedAr | P4, movement/AR | No rear camera, AR session or motion acquisition; no walking task |
| eeg | P5, EEG | No discovery, pairing or acquisition; no headset required for previews |

Before either preview opens or its journal initializes, the person makes an
explicit decision. No role or checkbox is selected initially. Independent
confirmations cover understanding information and the described local demo
storage. A caregiver additionally confirms explanation to the child, the child's
explicit willingness and accompanying them. This is a self-declaration, not age,
legal-representation or assent verification for a study. Child-friendly text
explains changeable rules, pause, stop and refusal. Refusal retains information
access and blocks previews; no penalty or inferred agreement is used.

The accepted information version and variant are held only in process memory.
Rotation can retain the decision; process death/relaunch requires a new decision.
There is no persistent receipt, signature, identity, timestamp or export record.
This intentional boundary avoids treating an engineering acknowledgement as a
research consent artifact. Version/variant mismatches require a fresh decision.

Home exposes the information and **Wycofaj zgodę**. Withdrawal immediately blocks
new task actions, requests a durable stop for an existing nonterminal engineering
session when writable, stops the in-memory task state, hides the flow and invalidates
pending UI callbacks. Completed engineering trial rows already committed locally
remain in the app-private database. Previously authorized commits can finish; no
new input is accepted.
It does not erase earlier engineering journals. The UI explains that Android's
Clear app data removes all local demonstrations for that package. It never
promises deletion from a server; there is no uploader.

## Ownership and verification

- onboarding/VariantGuide.kt: common steps and distinct Polish module instructions.
- onboarding/EngineeringConsent.kt: incomplete/default/child/version/variant guards,
  explicit acceptance and process-only withdrawal.
- ui/InformationScreens.kt: scrollable headings, grouped accessible radio/checkbox
  rows, no preselection, refusal and review/withdrawal paths.
- MainActivity: guards opening, task callbacks and controller inputs; initializes
  the journal only after current agreement. Stop remains the withdrawal exception.

EngineeringConsentTest covers incomplete choices, child-willingness requirement,
variant/version isolation, withdrawal, explicit reacceptance and resolution of
all five instructions including the actually compiled flavor. Existing domain,
storage and contrast tests continue to run in the all-five-flavor workflow.

Physical-device acceptance, not yet executed: first launch refuses without DB
initialization; instructions remain readable; unchecked controls and role selection
are correctly announced by TalkBack; both roles accept only with required fields;
rotation retains accepted agreement but relaunch asks again; refusal blocks both
previews; withdrawal during an outstanding commit hides the UI and rejects late
callbacks; reacceptance never silently resumes a task; module permissions are not
requested. Inspect all five APKs with large fonts and dark/light themes.

## Research-use gate

The participant information/permission/assent templates in ../../docs/ethics/
remain the source for future institutional completion. Controller/contact details,
formal classification, data-protection basis, retention, rights, incident contacts
and optional-module decisions are not fabricated or silently accepted by this UI.
A future research build needs approved versioned information, separate module
choices, age-appropriate assent, the authorized recording/withdrawal procedure and
participant eligibility before collection. The current agreement cannot satisfy
those requirements. Estimated 2–4 further core implementation packages remain,
subject to configuration freeze, governance completion and device qualification.
