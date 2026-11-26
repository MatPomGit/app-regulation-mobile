# Runbook deweloperski

1. Sprawdź dokładny SHA, JDK, Gradle i SDK zgodnie z [instalacją](../installation.md).
2. Uruchom testy i lint właściwego wariantu w izolowanym środowisku.
3. W razie błędu sprawdź log kompilacji, dostępność SDK i wersje zależności. Nie obchodź kontroli.
4. Przy błędzie dziennika zachowaj bazę w kontrolowanym miejscu; nie resetuj ani nie publikuj danych. Dziennik blokuje dalsze zmiany po wykryciu niespójności.
5. Rollback kodu przez zweryfikowany commit; nie przywracaj starszego schematu bazy bez testu migracji.

Brak wdrożonej usługi i zweryfikowanej procedury fizycznej. [Testy](../testing.md).
