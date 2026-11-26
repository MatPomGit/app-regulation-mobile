# Instalacja środowiska deweloperskiego

Użyj zatwierdzonego SHA repozytorium, JDK 17, Gradle 9.6.0 i Android SDK Platform 37.0. Ustaw ANDROID_HOME na lokalne SDK. Nie zapisuj `local.properties`, kluczy podpisu ani danych w Git.

```bash
cd android
gradle --no-daemon :app:testCoreDebugUnitTest :app:lintCoreDebug :app:assembleCoreDebug
```

Pozostałe warianty: Facial, Bilateral, EmbodiedAr, Eeg. Pakiety debug są wyłącznie do kontrolowanego testowania; brak zatwierdzonego wydania. Instalacja na urządzeniu: NOT_RUN.
