# Przepływy danych

Wejście użytkownika → Compose → reduktor sesji → transakcja SQLite → zatwierdzony checkpoint → UI. Zdarzenie i rekord kolejki są zapisywane atomowo; ukończone próby inżynierskie mają osobną tabelę bez kolejki eksportu. Odtworzenie sprawdza sekwencje, identyfikatory, skróty i stan reduktora.

Dziennik używa monotonicznej domeny zegara oraz losowych identyfikatorów sesji. Brak transportu wywołującego API kolejki; brak dowodu pomiarowego ruchu sieciowego. Baza nie jest szyfrowana. Backup Android wyłączono w manifeście. Retencja, usuwanie i eksport uczestników nie są zatwierdzone.

[Specyfikacja dziennika](../../android/PERSISTENCE.md) · [Prywatność](../privacy.md).
