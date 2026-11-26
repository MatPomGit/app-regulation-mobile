# Android build variants

The project uses five product flavors mapped one-to-one to the five active ARC-ADHD manuscripts.

- `core` -> Paper 1
- `facial` -> Paper 2
- `bilateral` -> Paper 3
- `embodiedAr` -> Paper 4
- `eeg` -> Paper 5

Feature selection is compile-time and available through `core/StudyVariant.kt`. Companion features are not runtime toggles in the research build.

Full scientific mapping: `../../docs/android_article_variants.md`.
