# Instalacja środowiska deweloperskiego

Użyj zatwierdzonego SHA repozytorium, JDK 17, Gradle 9.6.0 i Android SDK Platform 37.0. Ustaw ANDROID_HOME na lokalne SDK. Nie zapisuj `local.properties`, kluczy podpisu ani danych w Git.

```bash
cd android
gradle --no-daemon :app:testCoreDebugUnitTest :app:lintCoreDebug :app:assembleCoreDebug
```

Pozostałe warianty: Facial, Bilateral, EmbodiedAr, Eeg. Pakiety debug są wyłącznie do kontrolowanego testowania; brak zatwierdzonego wydania. Instalacja na urządzeniu: NOT_RUN.

Przed kompilacją przygotuj lokalną zależność z katalogu `android/`:

```bash
git clone https://github.com/MatPomGit/lib-adapt.git .dependencies/lib-adapt
git -C .dependencies/lib-adapt checkout --detach dc1602097b4feaf5f9cf9b103a18013ab787c746
(cd .dependencies/lib-adapt && gradle --no-daemon test publishMavenJavaPublicationToBuildRepository)
```
