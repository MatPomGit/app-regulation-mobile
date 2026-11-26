# Bezpieczeństwo i zgłaszanie podatności

**Status: DEVELOPMENT / CONTACT_NOT_VERIFIED.** Właściciel: Mateusz Pomianek. Prywatny kanał raportowania nie został zweryfikowany; nie publikuj szczegółów podatności w Issues. Zanim aplikacja przyjmie zewnętrznych użytkowników lub dane, skonfiguruj realny, prywatny kanał i zweryfikuj jego obsługę.

## Jak zgłaszać problem

Nie publikuj exploitów, sekretów, tokenów, danych uczestników, studentów ani poufnych logów w publicznych Issues i PR. Po skonfigurowaniu kanału podaj właścicielowi opis wpływu, dotkniętą wersję/SHA, bezpieczne kroki reprodukcji i minimalne dowody bez prywatnych danych. Nie testuj produkcji ani urządzeń osób trzecich bez zgody.

Zgłoszenia funkcjonalne bez informacji wrażliwych kieruj zgodnie z [SUPPORT.md](SUPPORT.md). Zasady zachowania: [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Zasady techniczne

Traktuj kod z niezaufanych PR, forków i niezweryfikowanych zależności jako niezaufany; testuj w izolacji, bez sekretów i uprawnień hosta. Nie wykonuj go na stałych runnerach HomeServer lub Andromeda. Stosuj minimalne uprawnienia, sprawdzaj podatności oraz pochodzenie i licencje zależności. Nie ujawniaj danych użytkowników w artefaktach, raportach ani publicznej stronie.

Lokalny dziennik SQLite nie jest szyfrowany; backup jest wyłączony, a eksport uczestników nie jest aktywny. Nie przetwarzaj danych uczestników. Przed wydaniem zweryfikuj urządzenia, zależności i kanał raportowania. Mapa wymagań: [model zagrożeń](docs/security/threat-model.md), [ADR bezpieczeństwa](docs/adr/0002-security-boundaries.md), [incydenty](docs/security/incident-response.md), [prywatność](docs/privacy.md). Wersja szablonu i deklaracje w plikach nie stanowią certyfikacji.

## Wydania i odpowiedzialność

Brak aktualnych dowodów oznacz `NOT_RUN` lub `UNAVAILABLE`, nie `PASS`. Zgoda na merge nie jest zgodą na release, deploy, ujawnienie danych ani zmianę infrastruktury. Przed wydaniem wymagaj oceny ryzyka, licencji, praw do dystrybucji, testów na dokładnym SHA i odrębnej decyzji właściciela.
