package pl.edu.prz.arcadhd.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import pl.edu.prz.arcadhd.core.ArticleModule
import pl.edu.prz.arcadhd.core.StudyVariant
import pl.edu.prz.arcadhd.onboarding.VariantGuides
import pl.edu.prz.arcadhd.session.SessionState

@Composable
fun HomeScreen(
    variant: StudyVariant, module: ArticleModule,
    consentAccepted: Boolean, onInstructions: () -> Unit, onAgreement: () -> Unit,
    onWithdraw: () -> Unit, ready: Boolean, busy: Boolean,
    hasSession: Boolean, canCreate: Boolean, readOnly: Boolean, feedback: String,
    onSession: () -> Unit, onSignalGate: () -> Unit, modifier: Modifier = Modifier,
) {
    var tools by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    val guide = remember(variant.paperId) { VariantGuides.forPaper(variant.paperId) }
    PreviewFrame("ARC-ADHD", modifier) {
        TechnicalBadge()
        SectionCard(guide.title, highlighted = true) {
            Text("Misja kolorów: pomóż robotowi odczytać osiem krótkich sygnałów. Reaguj zgodnie z aktualną regułą, a przy zmianie zasady przejdź przez krótką sekwencję regulacyjną.")
            if (consentAccepted) {
                PreviewAction(
                    "Wypróbuj Signal Gate",
                    enabled = ready && canCreate && !busy,
                    onClick = onSignalGate,
                )
            } else {
                Text("Najpierw przeczytaj informacje i zdecyduj, czy chcesz skorzystać. Bez zgody podglądy pozostają zamknięte.")
                PreviewAction("Przeczytaj informacje i zdecyduj", onClick = onAgreement)
            }
        }
        SectionCard("Instrukcja i Twoja decyzja") {
            SupportingAction("Opis i instrukcja wariantu", onClick = onInstructions)
            if (consentAccepted) {
                Text("Zgoda na demonstrację jest aktywna w tym uruchomieniu.")
                SupportingAction("Informacje o zgodzie", onClick = onAgreement)
                SupportingAction("Wycofaj zgodę", onClick = onWithdraw)
            } else {
                Text("Instrukcję możesz przeczytać również bez zgody.")
            }
        }
        // Storage failures remain visible even when technical tools are collapsed.
        if (consentAccepted && readOnly && feedback.isNotBlank()) {
            StatusMessage(journalMessage(feedback), error = true)
        }
        DetailToggle("narzędzia techniczne", tools) { tools = !tools }
        if (tools) SectionCard("Narzędzia techniczne") {
            Text("Eksplorator symuluje kroki kontrolera i zapisuje demonstrację lokalnie. Nie uruchamia sesji badawczej.")
            SupportingAction(if (hasSession) "Otwórz zapisaną demonstrację" else "Podgląd przebiegu sesji",
                enabled = consentAccepted && ready && !busy && (hasSession || canCreate),
                onClick = onSession)
            when {
                !consentAccepted -> Text("Dostęp wymaga zgody na demonstrację.")
                !ready -> StatusMessage("Odczytuję lokalny zapis…")
                busy -> StatusMessage("Zapisuję zmianę. Poczekaj na zakończenie.")
                !readOnly && feedback.isNotBlank() -> StatusMessage(journalMessage(feedback))
            }
            DetailToggle("informacje o wariancie", details) { details = !details }
            if (details) {
                Text("Wariant ${module.paperId}: ${guide.title}")
                guide.sections.first().paragraphs.forEach { Text(it) }
                guide.sections.single { it.title == "Instrukcja modułu dodatkowego" }
                    .paragraphs.forEach { Text(it) }
            }
        }
    }
}

/** Translate bounded journal outcomes; never expose an exception or database payload. */
fun journalMessage(code: String): String = when (code) {
    "local_storage_failure" -> "Nie udało się zapisać zmiany. Dalsze zmiany są zablokowane. Sprawdź wolne miejsce i uruchom aplikację ponownie."
    "clock_domain_changed" -> "Zegar urządzenia zmienił się. Poprzednia sesja jest tylko do odczytu; możesz rozpocząć nową demonstrację."
    "restart_requires_explicit_resume" -> "Sesja została przerwana. Wznowienie wymaga świadomego wyboru."
    "recovery_interrupted_protocol_abort" -> "Pomiar powrotu do zadania został przerwany. Sesja została zakończona bez przypisywania wyniku."
    "checkpoint_restored" -> "Odtworzono zapisany stan sesji."
    "new_engineering_session" -> "Utworzono nową sesję demonstracyjną."
    "stale_sequence" -> "Stan już się zmienił. Wybierz działanie ponownie."
    "configuration_mismatch", "journal_decode_failure", "event_integrity_failure",
    "checkpoint_mismatch", "queue_record_missing", "replay_mismatch",
    "command_sequence_conflict", "invalid_initial_snapshot", "session_not_found",
    "event_identity_conflict", "stale_checkpoint" ->
        "Nie można potwierdzić poprawności zapisu. Dalsze zmiany są zablokowane; historia została zachowana."
    "identical_retry" -> "Zmiana była już zapisana. Nie utworzono drugiego zdarzenia."
    "engineering_core_ready" -> "Przygotowano lokalną demonstrację czasowego Signal Gate."
    "trial_persisted" -> "Ukończona próba została zapisana lokalnie."
    "engineering_challenge_started" -> "Zmiana reguły uruchomiła techniczny przebieg Core."
    "engineering_challenge_suppressed" -> "Zmiana reguły nie spełniła technicznej bramki demonstracyjnej."
    "challenge_delivered" -> "Rozpoczęto przerwę regulacyjną."
    "pause_complete" -> "Przechodzę do krótkiej orientacji."
    "orientation_complete" -> "Możesz wybrać strategię."
    "strategy_selected" -> "Strategia została wybrana."
    "strategy_complete_without_reference" -> "Strategia zakończona; pomiar recovery oznaczono jako nieważny w tej demonstracji."
    "continue_after_invalid_recovery" -> "Wracamy do zadania bez przypisywania wyniku recovery."
    "session_goal_complete" -> "Próby zakończone. Pozostało krótkie podsumowanie."
    "reflection_complete" -> "Demonstracja została zakończona."
    "core_task_requires_fresh_session" -> "Czasowy Signal Gate wymaga nowej sesji technicznej."
    "trial_identity_conflict", "trial_sequence_gap", "trial_integrity_failure",
    "core_plan_mismatch", "core_plan_persistence_failure", "trial_decode_failure" ->
        "Nie można potwierdzić integralności zapisu prób. Dalszy przebieg został zablokowany."
    "non_monotonic_timestamp" -> "Nie można zapisać zmiany: czas urządzenia nie jest zgodny z dziennikiem."
    "terminal_state" -> "Ta demonstracja jest już zakończona."
    "sequence_exhausted" -> "Osiągnięto limit zdarzeń. Rozpocznij nową demonstrację."
    "missing_event", "event_not_allowed" -> "To działanie nie jest dostępne w bieżącym kroku."
    "missing_resume_target", "invalid_resume_target" -> "Nie można potwierdzić punktu wznowienia."
    "active_control_boundary" -> "Ten krok jest wyłączony w demonstracji kontroli aktywnej."
    "no_recovery_to_invalidate" -> "Brak rozpoczętej obserwacji powrotu do zadania."
    "recovery_context_required", "recovery_resume_context_unverified",
    "recovery_outcome_context_unverified" -> "Nie potwierdzono warunków obserwacji powrotu do zadania."
    "fatal_integrity_condition" -> "Demonstrację zakończono z powodu błędu spójności."
    "safety_stop", "explicit_child_stop" -> "Demonstracja została zatrzymana."
    else -> if (code in sessionEventLabels) "Symulacja: ${sessionEventLabel(code)}."
        else "Kontroler demonstracji zgłosił nierozpoznany stan techniczny."

}

fun sessionStateLabel(state: SessionState): String = when (state) {
    SessionState.BOOT -> "Uruchamianie"
    SessionState.RESEARCH_SETUP -> "Konfiguracja sesji"
    SessionState.DEVICE_QUALIFICATION -> "Kwalifikacja urządzenia"
    SessionState.CAREGIVER_ONBOARDING -> "Wprowadzenie opiekuna"
    SessionState.CHILD_READINESS -> "Gotowość do ćwiczenia"
    SessionState.TUTORIAL -> "Instrukcja ćwiczenia"
    SessionState.RULE_CALIBRATION -> "Kalibracja reguł"
    SessionState.BASELINE_TASK -> "Zadanie podstawowe"
    SessionState.CHALLENGE_PENDING -> "Przygotowanie wyzwania"
    SessionState.CHALLENGE_ACTIVE -> "Aktywne wyzwanie"
    SessionState.REGULATION_PAUSE -> "Przerwa regulacyjna"
    SessionState.ORIENT -> "Orientacja"
    SessionState.STRATEGY_SELECTION -> "Wybór strategii"
    SessionState.STRATEGY_EXECUTION -> "Wykonanie strategii"
    SessionState.RECOVERY_MONITORING -> "Monitorowanie powrotu do zadania"
    SessionState.RECOVERY_OBSERVED -> "Zaobserwowano powrót do zadania"
    SessionState.RECOVERY_CENSORED -> "Zakończono okno obserwacji powrotu do zadania"
    SessionState.RECOVERY_DATA_INVALID -> "Nieważne dane powrotu do zadania"
    SessionState.RESUME_GOAL -> "Powrót do zadania"
    SessionState.PAUSED_BY_CHILD -> "Sesja wstrzymana"
    SessionState.TECHNICAL_INTERRUPT -> "Przerwanie techniczne"
    SessionState.SESSION_REFLECTION -> "Podsumowanie sesji"
    SessionState.SESSION_COMPLETE -> "Sesja zakończona"
    SessionState.SAFE_STOP -> "Sesja zatrzymana"
    SessionState.PROTOCOL_ABORT -> "Sesja przerwana zgodnie z protokołem"
    SessionState.FATAL_ERROR -> "Błąd uniemożliwiający kontynuację"
}
