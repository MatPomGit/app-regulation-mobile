# Android build variants

The project uses five product flavors mapped one-to-one to the five active ARC-ADHD manuscripts.

- `core` -> Paper 1
- `facial` -> Paper 2
- `bilateral` -> Paper 3
- `embodiedAr` -> Paper 4
- `eeg` -> Paper 5

Feature selection is compile-time and available through `core/StudyVariant.kt`. Companion features are not runtime toggles in the research build.

Scientific mapping: MSC-ADHD-P01, P02, P03 and P05 in ARC-NEURO; MSC-ADHD-P04 in ARC-XR.
