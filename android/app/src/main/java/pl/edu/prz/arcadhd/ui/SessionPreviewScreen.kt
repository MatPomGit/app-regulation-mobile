package pl.edu.prz.arcadhd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pl.edu.prz.arcadhd.session.SessionControls
import pl.edu.prz.arcadhd.session.SessionGraph
import pl.edu.prz.arcadhd.session.SessionMode
import pl.edu.prz.arcadhd.session.SessionSnapshot

/** Developer event explorer. Buttons simulate service events, never validate participants. */
@Composable
fun SessionPreviewScreen(
    snapshot: SessionSnapshot,
    feedback: String,
    sessionId: String?,
    busy: Boolean,
    readOnly: Boolean,
    canCreate: Boolean,
    onInput: (String?, SessionControls) -> Unit,
    onReset: (SessionMode) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onExit)
    var details by remember { mutableStateOf(false) }
    val edges = SessionGraph.transitions.getValue(snapshot.state)
    PreviewFrame("Podgląd kontrolera sesji", modifier, footer = {
        SafetyActions(
            pauseEnabled = !readOnly && "pause" in edges,
            stopEnabled = !readOnly && snapshot.state !in SessionGraph.terminal,
            onPause = { onInput(null, SessionControls(childPause = true)) },
            onStop = { onInput(null, SessionControls(childStop = true)) },
        )
    }) {
        TechnicalBadge()
        Text("To narzędzie techniczne: przyciski symulują zdarzenia, a nie wykonują pomiarów ani kwalifikacji telefonu.")
        StatusMessage("Stan: ${sessionStateLabel(snapshot.state)}")
        if (busy) Text("Trwa zapis lokalny…", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (readOnly) StatusMessage("Tylko odczyt. Dalsze zmiany tej sesji są zablokowane.", error = true)
        if (snapshot.resumeTarget != null) Text("Powrót do: ${sessionStateLabel(snapshot.resumeTarget)}")
        if (feedback.isNotBlank()) StatusMessage(journalMessage(feedback), error = readOnly)
        DetailToggle("szczegóły techniczne", details) { details = !details }
        if (details) SectionCard("Szczegóły demonstracji") {
            if (sessionId != null) Text("Identyfikator: $sessionId")
            Text("Tryb: ${if (snapshot.mode == SessionMode.CORE) "podstawowy" else "kontrola aktywna"}")
            Text("Liczba przejść: ${snapshot.sequence}")
            if (snapshot.recoveryContext != null) Text("Warunki obserwacji powrotu do zadania są zablokowane.")
        }
        SectionCard("Symulowane zdarzenia") {
        edges.keys.filter { it !in setOf("stop", "pause", "technical_interrupt") }.forEach { event ->
            OutlinedButton(
                onClick = { onInput(event, SessionControls()) },
                enabled = !busy && !readOnly,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Symuluj: ${sessionEventLabel(event)}") }
        }
        if ("technical_interrupt" in edges) {
            OutlinedButton(enabled = !busy && !readOnly, onClick = {
                onInput(null, SessionControls(technicalInterrupt = true))
            }) { Text("Symuluj przerwanie techniczne") }
        }
        }
        SectionCard("Nowa demonstracja") {
        OutlinedButton(enabled = canCreate && !busy, onClick = { onReset(SessionMode.CORE) }) {
            Text("Nowa sesja demonstracyjna")
        }
        OutlinedButton(enabled = canCreate && !busy, onClick = { onReset(SessionMode.ACTIVE_CONTROL) }) {
            Text("Nowa demonstracja kontroli aktywnej")
        }
        }
        SupportingAction("Wróć do ekranu głównego", onClick = onExit)

    }
}
