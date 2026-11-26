# ADR-0002: granice bezpieczeństwa

**Status:** proposed, 2026-11-26.

Aktualna architektura: Compose, lokalny reduktor i dziennik SQLite, opcjonalne warianty sprzętowe. Nie ma aktywnego transportu kolejki ani zatwierdzonego eksportu uczestników. Proponowana granica: zachować engineering-only, minimalne uprawnienia, izolację CI i brak danych uczestników do odrębnej kwalifikacji. Alternatywy z backendem lub transportem zdalnym wymagają nowego modelu zagrożeń. Baza nie jest szyfrowana; retencja i testy fizyczne pozostają otwarte. [Model zagrożeń](../security/threat-model.md).
