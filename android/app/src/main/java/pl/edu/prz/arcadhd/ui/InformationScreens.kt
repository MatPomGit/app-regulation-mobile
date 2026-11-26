package pl.edu.prz.arcadhd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import pl.edu.prz.arcadhd.onboarding.*

@Composable
fun InstructionsScreen(paperId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val guide = remember(paperId) { VariantGuides.forPaper(paperId) }
    PreviewFrame("Instrukcja: ${guide.title}", modifier) {
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Wróć")
        }
        TechnicalBadge()
        Text("Wariant: ${guide.title}. Wersja informacji: ${VariantGuides.DISPLAY_INFORMATION_VERSION}.")
        guide.sections.forEach { section ->
            SectionCard(section.title) {
                section.paragraphs.forEach { Text(it) }
            }
        }
    }
}

@Composable
fun AgreementScreen(
    paperId: String, agreement: DemoAgreement?, onAccept: (ConsentChoices) -> Unit,
    onDecline: () -> Unit, onWithdraw: () -> Unit, onInstructions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDecline)
    var choices by remember(paperId) { mutableStateOf(ConsentChoices()) }
    PreviewFrame("Zgoda na demonstrację", modifier) {
        TechnicalBadge()
        SectionCard("Twoja decyzja") {
        Text("Dotyczy wyłącznie technicznego podglądu. Nie jest zgodą na udział w badaniu ani na przyszłe pomiary.")
        Text("Decyzja jest dobrowolna. Odmowa pozostawia dostęp do instrukcji. Możesz wycofać zgodę na ekranie głównym bez podania powodu.")
        Text("Nie zapisujemy imienia, podpisu ani decyzji na dysku. Zgoda obowiązuje tylko w bieżącym procesie aplikacji; po jego zakończeniu poprosimy ponownie.")
        }
        OutlinedButton(onClick = onInstructions, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Przeczytaj pełny opis i instrukcję wariantu")
        }
        SectionCard("Co będziesz robić") {
        Text("Możesz wybrać wygląd robota. Pamiątki za 2, 4 i 8 bram pozostają tylko na ekranie podglądu, także po stopie i ponowieniu. Powrót do ekranu głównego lub odtworzenie ekranu usuwa album.")
        Text("Signal Gate ma osiem stałych prób z ograniczonym oknem odpowiedzi. Dla sygnału NO-GO nie naciskasz przycisku do końca próby. Po zmianie reguły pojawia się krótka sekwencja pauzy, orientacji i wyboru strategii. Pauza wstrzymuje aktywny czas próby, Zatrzymaj kończy; możesz przerwać bez kary.")
        }
        SectionCard("Co zapisujemy") {
        Text("Aplikacja zapisuje lokalnie techniczny przebieg sesji oraz ukończone próby Signal Gate: identyfikatory techniczne, regułę i bodziec, rodzaj reakcji, poprawność oraz czas monotoniczny. Rekordy prób nie są przeznaczone do wysyłki. Prywatna baza nie jest szyfrowana i nie ma automatycznej kopii zapasowej. Historia pozostaje po wycofaniu zgody; można ją usunąć przez Wyczyść dane aplikacji w ustawieniach Androida.")
        Text("Nie zbieramy danych uczestników, obrazu, dźwięku ani EEG. Nie wysyłamy danych na serwer. Dodatkowe moduły i uprawnienia nie są uruchamiane przez tę zgodę.")
        }
        SectionCard("Jak przerwać") {
        Text("Nie obiecujemy poprawy ADHD. Jeżeli coś męczy lub jest nieprzyjemne, przerwij. Dziecko korzysta z opiekunem i może odmówić niezależnie od jego decyzji.")
        }
        if (agreement != null) {
            Text("Zgoda jest aktywna w bieżącym uruchomieniu. Wersja informacji: ${VariantGuides.DISPLAY_INFORMATION_VERSION}.")
            SupportingAction("Wycofaj zgodę", onClick = onWithdraw)
            OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Wróć do ekranu głównego")
            }
        } else {
            SectionCard("Kto będzie korzystać?") {
            Column(Modifier.selectableGroup()) {
                RoleChoice("Osoba pełnoletnia, samodzielnie", choices.role == DemoRole.ADULT) {
                    choices = choices.copy(role = DemoRole.ADULT, childWilling = false)
                }
                RoleChoice("Dziecko razem z opiekunem", choices.role == DemoRole.CAREGIVER) {
                    choices = choices.copy(role = DemoRole.CAREGIVER, childWilling = false)
                }
            }
            }
            SectionCard("Potwierdź swój wybór") {
            ConsentCheck("Przeczytano opis i instrukcję. Rozumiem status techniczny, dobrowolność i sposób przerwania.", choices.informationUnderstood) {
                choices = choices.copy(informationUnderstood = it)
            }
            ConsentCheck("Zgadzam się na uruchomienie podglądów i opisany lokalny zapis demonstracji, bez wysyłania danych.", choices.localStorageAccepted) {
                choices = choices.copy(localStorageAccepted = it)
            }
            if (choices.role == DemoRole.CAREGIVER) {
                StatusMessage("Dla dziecka: sprawdzamy przyciski nowej aplikacji. Zasady mogą się zmienić. Możesz zrobić przerwę, powiedzieć stop albo nie chcę. Nie musisz kończyć. Opiekun przeczyta instrukcję razem z Tobą.", announce = false)
                ConsentCheck("Jestem opiekunem. Wyjaśniono dziecku podgląd i możliwość odmowy; dziecko wyraźnie chce spróbować. Będę mu towarzyszyć.", choices.childWilling) {
                    choices = choices.copy(childWilling = it)
                }
            }
            }
            if (!choices.canAccept) Text("Wybierz, kto będzie korzystać, i zaznacz wymagane potwierdzenia. Możesz również odmówić.")
            OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("Nie wyrażam zgody, wróć")
            }
            PreviewAction("Wyrażam zgodę na demonstrację", enabled = choices.canAccept) { onAccept(choices) }
        }
    }
}

@Composable
private fun RoleChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected,
        role = Role.RadioButton, onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp).weight(1f))
    }
}

@Composable
private fun ConsentCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked,
        role = Role.Checkbox, onValueChange = onChange).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, Modifier.padding(start = 12.dp).weight(1f))
    }
}
