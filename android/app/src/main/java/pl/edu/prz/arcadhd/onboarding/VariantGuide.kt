package pl.edu.prz.arcadhd.onboarding

data class GuideSection(val title: String, val paragraphs: List<String>)
data class VariantGuide(val paperId: String, val title: String, val sections: List<GuideSection>)

/** Polish user instructions describing actual preview behavior and separately planned modules. */
object VariantGuides {
    const val INFORMATION_VERSION = "engineering-pl-4"
    const val DISPLAY_INFORMATION_VERSION = "4"
    val paperIds = setOf("P1", "P2", "P3", "P4", "P5")

    fun forPaper(paperId: String): VariantGuide {
        require(paperId in paperIds) { "unsupported_variant" }
        val (title, purpose, moduleInstructions, permissions) = when (paperId) {
            "P1" -> listOf(
                "Moduł podstawowy", "Docelowo: ćwiczenie powrotu do zadania w cyklu wyzwanie, pauza, orientacja, strategia i powrót do zadania.",
                "Obecnie podstawowy Signal Gate działa jako czasowa demonstracja techniczna połączona z kontrolerem Core. Ukończone próby są zapisywane lokalnie. Kwalifikacja telefonu i naukowo ważny pomiar T_rec nie są jeszcze wykonywane; przy zmianie reguły demonstracja jawnie oznacza recovery jako nieważne zamiast wyliczać pozorny wynik. Dźwięk i wibracje nie są jeszcze wykonywane.",
                "Ten wariant nie wymaga kamery, mikrofonu, lokalizacji ani Bluetooth.",
            )
            "P2" -> listOf(
                "Mimika i kierunek spojrzenia", "Docelowo: osobny moduł badawczy analizujący na urządzeniu cechy zachowania twarzy i spojrzenia podczas podstawowego zadania.",
                "Obecnie dostępne są wspólne podglądy. Nie ustawiaj telefonu do nagrywania: przednia kamera, wykrywanie twarzy i analiza spojrzenia nie są uruchomione. Przyszłe pomiary będą wymagały osobnej informacji, wyboru użytkownika i instrukcji ustawienia telefonu. Mimika nie jest diagnozą emocji ani ADHD.",
                "Pakiet deklaruje uprawnienie do kamery, ale obecna aplikacja o nie nie prosi i nie używa kamery. Zgoda na demonstrację nie obejmuje przyszłego pomiaru twarzy.",
            )
            "P3" -> listOf(
                "Dźwięk bilateralny", "Docelowo: osobne porównanie kontrolowanego dźwięku stereo i warunków dopasowanych akustycznie.",
                "Obecnie aplikacja nie odtwarza bodźców stereo ani nie sprawdza kanałów słuchawek. Do podglądów nie potrzebujesz słuchawek. Nie próbuj samodzielnie odtwarzać protokołu stymulacji. Przyszła procedura wymaga sprawdzenia kanałów, ustawienia komfortowego poziomu i zatwierdzonej instrukcji; po dyskomforcie należy przerwać. Nie jest to procedura EMDR.",
                "Nie są wykonywane pomiary mikrofonem ani parowanie słuchawek. Przyszły moduł dźwiękowy będzie miał osobną informację i decyzję użytkownika.",
            )
            "P4" -> listOf(
                "Ruch i rozszerzona rzeczywistość", "Docelowo: porównanie zadania nieruchomego, ruchowego bez AR i z AR.",
                "Obecnie podglądy wykonuj w wygodnej, stabilnej pozycji. Nie chodź z telefonem i nie szukaj obiektów w otoczeniu: AR, kamera tylna i pomiar ruchu nie są uruchomione. Przyszłe zadania wymagają osobnej kwalifikacji przestrzeni i telefonu oraz instrukcji bezpieczeństwa. Nie zaczynaj ćwiczeń ruchowych na podstawie tego podglądu.",
                "Pakiet deklaruje kamerę dla przyszłego AR; obecna aplikacja nie prosi o dostęp ani nie otwiera kamery. Moduł AR wymaga osobnej informacji i decyzji.",
            )
            else -> listOf(
                "Pomiar EEG", "Docelowo: opcjonalny, obserwacyjny pomiar EEG z kompatybilnej opaski Muse S Athena, bez sterowania zadaniem na podstawie EEG.",
                "Obecnie nie zakładaj ani nie paruj opaski w aplikacji. Wyszukiwanie urządzeń, synchronizacja i pobieranie EEG nie są wdrożone. Podglądy działają bez opaski. Przyszła procedura wymaga autoryzowanego adaptera, sprawdzenia kompatybilności, osobnej zgody i instrukcji przygotowania. EEG w tym projekcie nie służy samodzielnej diagnozie.",
                "Pakiet deklaruje Bluetooth dla przyszłego adaptera, ale obecna aplikacja nie prosi o te uprawnienia i nie wyszukuje urządzeń. Zgoda na demonstrację nie obejmuje pomiaru EEG.",
            )
        }
        return VariantGuide(paperId, title, listOf(
            GuideSection("O tym wariancie", listOf(purpose,
                "To wersja techniczna, nie gotowa terapia ani sesja badawcza. Nie potwierdzono skuteczności klinicznej. Możesz odmówić, zrobić przerwę lub zakończyć bez podania powodu.")),
            GuideSection("Przed rozpoczęciem", listOf(
                "Przeczytaj instrukcję i ekran zgody. Osoba dorosła może sprawdzić demonstrację samodzielnie. Dziecko powinno korzystać razem z opiekunem, tylko jeśli chce; sprzeciw dziecka oznacza zakończenie.",
                "Podglądy działają offline. Nie wymagają konta, imienia, kodu uczestnika ani sprzętu dodatkowego. Ustaw czytelny tekst w Androidzie i wybierz wygodną pozycję.")),
            GuideSection("Robot i pamiątki", listOf(
                "Przed wyprawą możesz wybrać Iskrę, Lunę lub Piksela. Wybór zmienia wygląd i opowieść, ale nie reguły sygnałów.",
                "Mapa przedstawia osiem bram. Po odwiedzeniu 2, 4 i 8 bram otrzymujesz pamiątki niezależnie od poprawności i szybkości. Pauza i stop ich nie odbierają.",
                "Album pozostaje przy ponowieniu misji na tym ekranie. Powrót do ekranu głównego, odtworzenie ekranu lub zamknięcie procesu go usuwa. To dane w pamięci, nie trwały zapis postępów ani wynik badania.")),
            GuideSection("Signal Gate: krok po kroku", listOf(
                "1. Po wyrażeniu zgody wybierz „Wypróbuj Signal Gate”, wybierz robota, następnie „Ruszamy w drogę”. Jest osiem stałych prób z ograniczonym oknem odpowiedzi. Pasek pokazuje pozostały czas.",
                "2. W regule A naciśnij Reaguj dla niebieskiego okręgu. Dla pomarańczowego kwadratu nic nie naciskaj do końca próby; brak reakcji jest wtedy rejestrowany jako odpowiedź NO-GO.",
                "3. Po czwartej próbie aplikacja przechodzi przez techniczny cykl zmiany reguły: wyzwanie, pauza, orientacja i wybór krótkiej strategii. Ponieważ nie ma jeszcze zamrożonej referencji Reguły B, aplikacja nie wylicza T_rec i jawnie oznacza dane recovery jako nieważne.",
                "4. Po powrocie do zadania obowiązuje reguła B: Reaguj dla pomarańczowego kwadratu, a dla niebieskiego okręgu nic nie naciskaj do końca próby.",
                "5. Pauza zachowuje bieżącą próbę i zatrzymuje jej aktywny czas. Wznowienie wymaga przycisku. Zatrzymaj kończy demonstrację. Wyjście w tło wstrzymuje aktywną próbę.",
                "6. Każda ukończona próba jest najpierw zapisywana w prywatnej bazie technicznej, a dopiero potem pojawia się kolejna. Po ośmiu próbach pojawia się krótkie podsumowanie. Wynik zgodności z regułą nie jest wynikiem klinicznym.")),
            GuideSection("Przebieg sesji: krok po kroku", listOf(
                "1. Na ekranie głównym rozwiń „Narzędzia techniczne”, następnie wybierz podgląd przebiegu sesji lub otwórz zapisaną demonstrację. „Symuluj” służy do sprawdzania kontrolera; nie wykonuje kwalifikacji, pomiaru ani zgody badawczej.",
                "2. Po każdym zaakceptowanym przejściu aplikacja zapisuje zmianę lokalnie. Podczas zapisu zwykłe przyciski są zablokowane; pauza i stop pozostają dostępne tam, gdzie pozwala stan.",
                "3. Możesz utworzyć nową demonstrację podstawową lub kontroli aktywnej. Kontrola aktywna blokuje wejście w wyzwanie, regulację i obserwację powrotu do zadania. Powrót do ekranu głównego zatrzymuje niezakończoną sesję.",
                "4. Po ponownym uruchomieniu sprawdzana jest historia. Kontynuacja po przerwaniu wymaga świadomego wyboru; przerwana obserwacja powrotu do zadania kończy sesję. Po restarcie telefonu poprzedni zapis jest tylko do odczytu. Nie obchodź komunikatu błędu zapisu.")),
            GuideSection("Instrukcja modułu dodatkowego", listOf(moduleInstructions)),
            GuideSection("Uprawnienia urządzenia", listOf(permissions,
                "Żaden podgląd nie nagrywa mikrofonu, nie pobiera lokalizacji i nie korzysta z kontaktów. Uprawnienie Androida nie jest zgodą na udział w badaniu.")),
            GuideSection("Co zapisuje obecna wersja", listOf(
                "Signal Gate zapisuje ukończone próby w prywatnej bazie SQLite: techniczny identyfikator przebiegu i próby, regułę, bodziec, rodzaj reakcji, poprawność oraz czasy monotoniczne. Osobno zapisywane są przejścia kontrolera sesji. Rekordy prób nie trafiają do kolejki wysyłki.",
                "Aplikacja nie zbiera danych uczestnika, obrazu, dźwięku ani EEG i niczego nie wysyła na serwer. Baza nie jest szyfrowana, kopia zapasowa aplikacji jest wyłączona. Nie wpisuj danych osobowych; demonstracja nie ma formularza do ich wpisywania.",
                "Nowa demonstracja i wycofanie zgody nie usuwają wcześniejszego dziennika. Aby usunąć cały lokalny zapis, użyj ustawień Androida: Aplikacje, ARC-ADHD właściwego wariantu, Pamięć, Wyczyść dane. Usunie to wszystkie demonstracje tego wariantu. Nazwy opcji zależą od telefonu.")),
            GuideSection("Przerwa, odmowa i pomoc", listOf(
                "Jeżeli zadanie męczy lub jest nieprzyjemne, wybierz Pauza albo Zatrzymaj. Możesz przerwać bez kary. Dziecko może też powiedzieć opiekunowi „stop” lub „nie chcę”.",
                "Instrukcja i informacje o zgodzie są dostępne na ekranie głównym. „Wycofaj zgodę” blokuje dalsze podglądy i zatrzymuje bieżącą demonstrację, jeśli zapis jest możliwy. Po zamknięciu procesu aplikacja ponownie prosi o zgodę.",
                "W razie błędu przeczytaj komunikat, sprawdź wolne miejsce i uruchom aplikację ponownie. Jeśli problem trwa, zgłoś go osobie, która udostępniła tę wersję; nie umieszczaj prywatnych danych w zgłoszeniu.")),
        ))
    }
}
