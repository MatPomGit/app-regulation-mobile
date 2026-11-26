# Testy Android i repozytorium

Workflow Android engineering validation instaluje SDK 37.0, sprawdza exact SHA i wykonuje testy JVM, lint oraz assembleDebug dla pięciu wariantów. Testy SQLite JDBC nie są testami Android SQLiteOpenHelper. Walidacja fizyczna NOT_RUN.

# Testy i dowody

**Walidacja strukturalna i kontraktowa:** zainstaluj zależności w izolowanym środowisku i uruchom:

```bash
python -m pip install -r requirements-ci.txt
python scripts/validate_repository.py --phase scaffold
python scripts/validate_documentation.py
python -m pip check
python -m unittest discover -s tests -v
python src/example_cli.py --output outputs/example.synthetic.json
```

Walidator dokumentacji wymusza liczbę znaków Unicode zgodnie z [polityką](../config/documentation-policy.json); przekroczenie limitu, zły format polityki lub brak wymaganej dokumentacji powodują FAIL. Sama długość i obecność dokumentów nie potwierdzają kompletności architektury, weryfikacji zagrożeń ani prawdziwości deklaracji.

Walidator sprawdza JSON Schema Draft 2020-12, format `date-time` przykładowego zdarzenia syntetycznego, zgodność metadanych, autorstwo oraz wybrane sygnatury sekretów i ryzykowne ścieżki. Skaner nie zastępuje audytu prywatności ani badania rzeczywistych danych. Walidacja strukturalna jest niezależna od testów sprzętowych.

**Hierarchy dowodów:** test strukturalny, jednostkowy, kontraktowy, integracyjny, instalacyjny, kompatybilności oraz walidacja fizycznego urządzenia (gdy dotyczy). Test na emulatorze nie jest dowodem walidacji fizycznej. Dla każdego raportu zapisz exact SHA, runtime, środowisko, wersje, parametry, wynik i ograniczenia. `NOT_RUN` nie znaczy `PASS`.

Testy wykonuj w izolowanym środowisku, z minimalnymi uprawnieniami i bez ujawniania sekretów. Wydanie jest odrębną czynnością, patrz [Wydania](releases.md).


## Kontrola publicznej witryny

CI buduje stronę statyczną bez publikacji: `python -m mkdocs build --strict` oraz `python scripts/validate_pages.py --site-dir site`. Sprawdza zgodność kodu z bieżącym SHA `pull_request.head.sha` (lub SHA zdarzenia push); poprawny test PR nie jest testem konfiguracji GitHub Pages ani zgodą na publikację.
