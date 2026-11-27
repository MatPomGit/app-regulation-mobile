# Zależności

Deklaracje źródłowe w `android/build.gradle.kts` i `android/app/build.gradle.kts`: AGP 9.4.0, Kotlin Compose 2.4.21, Compose BOM 2026.09.00, Activity Compose 1.12.0, JUnit 4.13.2, SQLite JDBC 3.54.1.0 (tylko testy). Gradle 9.6.0, JDK 17, SDK 37.0. Repozytoria: Google Maven, Maven Central, Gradle Plugin Portal.

Zależności przechodnie nie mają jeszcze pełnego lockfile i zweryfikowanego SBOM. Audyt licencji i podatności wymagany przed wydaniem. LIB-ADAPT: `org.matpomgit:lib-adapt:0.1.0`, przypięty commit `dc1602097b4feaf5f9cf9b103a18013ab787c746`. Lokalny artefakt Maven jest budowany ze zweryfikowanego checkoutu; brak zdalnej publikacji. Modele punktacji badawczej pozostają w aplikacji.
