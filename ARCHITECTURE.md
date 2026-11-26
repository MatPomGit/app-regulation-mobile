# Architecture

The Android Gradle project under android/ has one application module and five flavors. Compose screens call the session controller and task logic; engineering journal storage uses local SQLite. The bilateral flavor has engineering audio components. Facial, AR and EEG flavor identifiers do not establish validated sensor integration. See [Android architecture](android/ARCHITECTURE.md) and [persistence](android/PERSISTENCE.md).

The template synthetic export contract is separate from application storage and does not authorize participant telemetry. No release, signing or participant-data activation is part of this migration. Future shared libraries must use versioned interfaces and preserve behavior.

Build and physical device evidence: NOT_RUN.
