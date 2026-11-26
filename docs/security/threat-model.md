# Model zagrożeń

Wstępny audyt statyczny; nie jest certyfikacją. Granice: UI, lokalna baza, system Android, opcjonalne urządzenia, łańcuch zależności i CI.

| Zagrożenie | Istniejąca kontrola | Ryzyko / dowód |
| --- | --- | --- |
| Uszkodzenie dziennika | transakcje, replay, skróty, blokada przy niezgodności | test fizyczny NOT_RUN |
| Ujawnienie danych | brak backupu, brak aktywnego uploadu, readiness=false | SQLite bez szyfrowania; dane uczestników zabronione |
| Przerwanie sesji | polityka restartu i jawny abort | test urządzenia NOT_RUN |
| Łańcuch dostaw | wersje bezpośrednie, CI read-only | SBOM i licencje przechodnie pending |
| Czujniki i dźwięk | warianty i granice modułów | bezpieczeństwo fizyczne NOT_RUN |

[Polityka prywatności](../privacy.md) · [Dziennik](../../android/PERSISTENCE.md). Przed badaniami i wydaniem wymagane testy oraz niezależny przegląd.
