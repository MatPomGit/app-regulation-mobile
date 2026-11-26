# Komponenty Android

Źródła: `android/app/src/main/java/pl/edu/prz/arcadhd/`. Jeden moduł Gradle, pięć flavors.

| Pakiet | Odpowiedzialność |
| --- | --- |
| ui, onboarding | Compose, informacja i zgoda inżynierska |
| core, session | warianty, automat sesji, koordynacja cyklu |
| task, measurement | Signal Gate, kryteria wyzwania, monitor odzyskiwania |
| storage, platform/storage | lokalny dziennik SQLite, replay, restart |
| qualification | kontrakt kwalifikacji urządzenia |
| bilateral/audio | generowanie i odtwarzanie sygnału, kolejność warunków |

Pozostałe warianty nie są dowodem działającej integracji czujników. Szczegóły: [architektura Android](../../android/ARCHITECTURE.md). Wydzielenie bibliotek jest przyszłą zmianą, nie istniejącą zależnością.
