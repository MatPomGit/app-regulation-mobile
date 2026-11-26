# ARC Regulation Mobile

Android/Kotlin/Jetpack Compose engineering prototype for regulation training and executive-control tasks. Scientific owner: Mateusz Pomianek. License: Apache-2.0.

The migrated project is in [android/](android/README.md). Five build flavors: core, facial, bilateral, embodiedAr and eeg. Optional flavor names indicate research scope, not validated sensor functionality. Participant readiness is false. No clinical use, participant recruitment or distribution is authorized by this source migration.

## Build and test

JDK 17, Android SDK 37 and Gradle 9.6.0 are the source project's declared environment.

```bash
cd android
gradle :app:testCoreDebugUnitTest :app:lintCoreDebug
gradle :app:assembleCoreDebug :app:assembleFacialDebug :app:assembleBilateralDebug :app:assembleEmbodiedArDebug :app:assembleEegDebug
```

Actual build and physical device validation remain pending. The separate synthetic template event contract is not the Android journal API. See [architecture](ARCHITECTURE.md).
