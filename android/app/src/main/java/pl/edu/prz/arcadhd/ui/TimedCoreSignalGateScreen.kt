package pl.edu.prz.arcadhd.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pl.edu.prz.arcadhd.session.SessionState
import pl.edu.prz.arcadhd.task.GateColor
import pl.edu.prz.arcadhd.task.GateRule
import pl.edu.prz.arcadhd.task.MissionKeepsake
import pl.edu.prz.arcadhd.task.TimedGateStage
import pl.edu.prz.arcadhd.task.TimedSignalGate
import pl.edu.prz.arcadhd.task.missionKeepsakes

@Composable
fun TimedCoreSignalGateScreen(
    gate: TimedSignalGate,
    sessionState: SessionState,
    busy: Boolean,
    locked: Boolean,
    feedback: String,
    selectedStrategy: String?,
    onStart: () -> Unit,
    onGo: (String) -> Unit,
    onTimeout: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onChallengeDelivered: () -> Unit,
    onPauseComplete: () -> Unit,
    onOrientationComplete: () -> Unit,
    onStrategySelected: (String) -> Unit,
    onStrategyComplete: () -> Unit,
    onContinueAfterInvalidRecovery: () -> Unit,
    onReflectionComplete: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler {
        if (!locked && sessionState !in terminalStates) onStop() else onExit()
    }

    var robot by remember { mutableStateOf(RobotCompanion.ISKRA) }
    var album by remember { mutableStateOf(emptySet<MissionKeepsake>()) }
    val visibleAlbum = album + missionKeepsakes(gate.trialIndex.coerceIn(0, TimedSignalGate.TRIAL_COUNT))
    SideEffect { if (album != visibleAlbum) album = visibleAlbum }

    val activeTimedTrial = sessionState == SessionState.BASELINE_TASK &&
        gate.stage == TimedGateStage.TASK && !busy && !locked
    val trial = gate.currentTrial

    LaunchedEffect(activeTimedTrial, trial?.id, trial?.deadlineAtMs) {
        if (activeTimedTrial && trial != null) {
            val remaining = trial.deadlineAtMs - SystemClock.elapsedRealtime()
            if (remaining > 0) delay(remaining)
            onTimeout()
        }
    }

    PreviewFrame("Misja kolorów", modifier, footer = {
        val canPause = sessionState == SessionState.BASELINE_TASK &&
            gate.stage == TimedGateStage.TASK && !busy && !locked
        val canStop = sessionState !in terminalStates && !locked
        SafetyActions(
            pauseEnabled = canPause,
            stopEnabled = canStop,
            showPause = canPause,
            showStop = sessionState !in terminalStates,
            onPause = onPause,
            onStop = onStop,
        )
    }) {
        TechnicalBadge()
        if (feedback.isNotBlank()) {
            StatusMessage(journalMessage(feedback), error = locked)
        }
        if (busy) StatusMessage("Zapisuję zmianę lokalnie…")
        if (locked) {
            StatusMessage(
                "Nie można potwierdzić trwałego zapisu. Dalszy przebieg został zablokowany.",
                error = true,
            )
        }

        when (sessionState) {
            SessionState.CHALLENGE_ACTIVE -> ChallengeScreen(onChallengeDelivered, busy || locked)
            SessionState.REGULATION_PAUSE -> RegulationPauseScreen(onPauseComplete, busy || locked)
            SessionState.ORIENT -> OrientScreen(onOrientationComplete, busy || locked)
            SessionState.STRATEGY_SELECTION -> StrategySelectionScreen(
                selectedStrategy,
                onStrategySelected,
                busy || locked,
            )
            SessionState.STRATEGY_EXECUTION -> StrategyExecutionScreen(
                selectedStrategy,
                onStrategyComplete,
                busy || locked,
            )
            SessionState.RECOVERY_DATA_INVALID -> InvalidRecoveryScreen(
                onContinueAfterInvalidRecovery,
                busy || locked,
            )
            SessionState.SESSION_REFLECTION -> ReflectionScreen(onReflectionComplete, busy || locked)
            SessionState.SESSION_COMPLETE -> CompletedSessionScreen(gate, visibleAlbum, robot)
            SessionState.SAFE_STOP, SessionState.PROTOCOL_ABORT, SessionState.FATAL_ERROR ->
                StoppedSessionScreen(visibleAlbum)
            SessionState.PAUSED_BY_CHILD -> PausedScreen(onResume, busy || locked)
            SessionState.BASELINE_TASK -> TimedGateContent(
                gate = gate,
                robot = robot,
                album = visibleAlbum,
                locked = locked,
                busy = busy,
                onRobot = { robot = it },
                onStart = onStart,
                onGo = onGo,
            )
            else -> {
                StatusMessage("Przygotowuję techniczny przebieg Core: ${sessionStateLabel(sessionState)}")
            }
        }

        OutlinedButton(
            onClick = onExit,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("Wróć do ekranu głównego")
        }
    }
}

@Composable
private fun TimedGateContent(
    gate: TimedSignalGate,
    robot: RobotCompanion,
    album: Set<MissionKeepsake>,
    locked: Boolean,
    busy: Boolean,
    onRobot: (RobotCompanion) -> Unit,
    onStart: () -> Unit,
    onGo: (String) -> Unit,
) {
    when (gate.stage) {
        TimedGateStage.READY -> {
            CompanionPicker(robot, onRobot)
            GateRoute(0)
            SectionCard("Pomóż robotowi odczytać sygnały") {
                Text("Przed Tobą osiem sygnałów. Każdy jest pokazywany przez ograniczony czas.")
                Text("Reguła A: niebieski okrąg = reaguj, pomarańczowy kwadrat = nie naciskaj nic.")
                Text("W połowie drogi zasada się zmieni i pojawi się krótka sekwencja regulacyjna.")
                Text("Ukończone próby są zapisywane tylko w prywatnym dzienniku technicznym urządzenia.")
                Text("To nadal demonstracja inżynierska, nie sesja badawcza ani ocena kliniczna.")
            }
            MissionAlbum(album)
            PreviewAction("Ruszamy w drogę", enabled = !busy && !locked, onClick = onStart)
        }
        TimedGateStage.TASK -> {
            val trial = gate.currentTrial
            if (trial == null) {
                StatusMessage("Brak aktywnej próby.", error = true)
                return
            }
            SectionCard("Aktualna reguła") {
                Text(
                    if (trial.rule == GateRule.A) {
                        "Reguła A: reaguj na niebieski okrąg"
                    } else {
                        "Reguła B: reaguj na pomarańczowy kwadrat"
                    },
                )
                Text("Jeśli sygnał nie wymaga reakcji, nic nie naciskaj do końca czasu próby.")
            }
            GateRoute(gate.trialIndex)
            val blue = trial.stimulus == GateColor.BLUE
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(96.dp).clearAndSetSemantics {}.background(
                        if (blue) ArcColors.blue else ArcColors.orange,
                        if (blue) CircleShape else RoundedCornerShape(8.dp),
                    ),
                )
                Text(
                    if (blue) "Niebieski okrąg" else "Pomarańczowy kwadrat",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                TrialCountdown(gate)
            }
            PreviewAction(
                "Reaguj",
                enabled = !busy && !locked,
                onClick = { onGo(trial.id) },
            )
        }
        TimedGateStage.RULE_SWITCH -> {
            SectionCard("Zmiana reguły") {
                Text("Przejście do nowej reguły jest obsługiwane przez przebieg Core.")
            }
        }
        TimedGateStage.PAUSED -> PausedScreen({}, true)
        TimedGateStage.COMPLETE -> {
            SectionCard("Próby ukończone") {
                Text("Zapisano ${gate.responses.size} ukończonych prób. Trwa podsumowanie sesji.")
            }
            MissionAlbum(album)
        }
        TimedGateStage.STOPPED -> StoppedSessionScreen(album)
    }
}

@Composable
private fun TrialCountdown(gate: TimedSignalGate) {
    val trial = gate.currentTrial ?: return
    val progress by produceState(
        1f,
        trial.id,
        trial.deadlineAtMs,
        gate.responseWindowMs,
    ) {
        while (true) {
            val remaining = (trial.deadlineAtMs - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            value = (remaining.toFloat() / gate.responseWindowMs.toFloat()).coerceIn(0f, 1f)
            if (remaining == 0L) break
            delay(50)
        }
    }
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Pozostały czas próby jest pokazany paskiem. Nie trzeba odliczać samodzielnie.")
}

@Composable
private fun ChallengeScreen(onNext: () -> Unit, disabled: Boolean) {
    SectionCard("Zmiana zasady") {
        Text("Zasada właśnie się zmienia. Zatrzymaj się na chwilę przed dalszą częścią misji.")
        Text("Ten etap jest połączony z kanonicznym kontrolerem Core.")
    }
    PreviewAction("Zatrzymaj się na chwilę", enabled = !disabled, onClick = onNext)
}

@Composable
private fun RegulationPauseScreen(onNext: () -> Unit, disabled: Boolean) {
    SectionCard("Krótka przerwa") {
        Text("Odłóż na moment reakcję. Nie musisz niczego robić szybko.")
    }
    PreviewAction("Jestem gotowa/y", enabled = !disabled, onClick = onNext)
}

@Composable
private fun OrientScreen(onNext: () -> Unit, disabled: Boolean) {
    SectionCard("Sprawdź, co dzieje się teraz") {
        Text("Zauważ oddech, napięcie ramion i kontakt stóp lub ciała z podłożem.")
        Text("Nie trzeba nazywać emocji ani podawać żadnych danych o zdrowiu.")
    }
    PreviewAction("Dalej", enabled = !disabled, onClick = onNext)
}

@Composable
private fun StrategySelectionScreen(
    selected: String?,
    onSelect: (String) -> Unit,
    disabled: Boolean,
) {
    SectionCard("Wybierz krótką strategię") {
        listOf("Spokojny oddech", "Rozluźnij ramiona", "Rozejrzyj się i wróć uwagą").forEach {
            OutlinedButton(
                onClick = { onSelect(it) },
                enabled = !disabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(if (selected == it) "$it • wybrano" else it)
            }
        }
    }
}

@Composable
private fun StrategyExecutionScreen(
    selected: String?,
    onNext: () -> Unit,
    disabled: Boolean,
) {
    SectionCard("Wykonaj strategię") {
        Text(selected ?: "Wybrana strategia")
        Text("Wykonaj ją spokojnie przez krótką chwilę, a następnie wróć do zadania.")
    }
    PreviewAction("Gotowe", enabled = !disabled, onClick = onNext)
}

@Composable
private fun InvalidRecoveryScreen(onNext: () -> Unit, disabled: Boolean) {
    SectionCard("Powrót do zadania") {
        Text("Demonstracja nie wylicza teraz T_rec.")
        Text("Brakuje zamrożonej, dopasowanej referencji dla Reguły B, więc kontroler oznacza pomiar recovery jako nieważny zamiast tworzyć pozorny wynik.")
    }
    PreviewAction("Wróć do misji", enabled = !disabled, onClick = onNext)
}

@Composable
private fun PausedScreen(onResume: () -> Unit, disabled: Boolean) {
    SectionCard("Pauza") {
        Text("Bieżąca próba jest wstrzymana. Czas aktywnej próby nie biegnie podczas pauzy.")
    }
    PreviewAction("Wznów", enabled = !disabled, onClick = onResume)
}

@Composable
private fun ReflectionScreen(onComplete: () -> Unit, disabled: Boolean) {
    SectionCard("Koniec misji") {
        Text("Przypomnij sobie, co pomogło Ci wrócić do zadania po zmianie zasady.")
        Text("To krótkie podsumowanie demonstracji, nie kwestionariusz diagnostyczny.")
    }
    PreviewAction("Zakończ", enabled = !disabled, onClick = onComplete)
}

@Composable
private fun CompletedSessionScreen(
    gate: TimedSignalGate,
    album: Set<MissionKeepsake>,
    robot: RobotCompanion,
) {
    RobotPortrait(robot, Modifier)
    MissionAlbum(album)
    SectionCard("Misja zakończona") {
        Text("Trwale zapisane próby: ${gate.responses.size} / ${TimedSignalGate.TRIAL_COUNT}")
        Text("Odpowiedzi zgodne z regułą: ${gate.responses.count { it.correct }} / ${gate.responses.size}")
        Text("Wynik dotyczy wyłącznie demonstracji reguł i nie jest wynikiem klinicznym.")
    }
}

@Composable
private fun StoppedSessionScreen(album: Set<MissionKeepsake>) {
    SectionCard("Misja zatrzymana") {
        Text("Dalsze próby nie są wykonywane. Ukończone wcześniej próby pozostają w prywatnym dzienniku technicznym.")
    }
    MissionAlbum(album)
}

private val terminalStates = setOf(
    SessionState.SESSION_COMPLETE,
    SessionState.SAFE_STOP,
    SessionState.PROTOCOL_ABORT,
    SessionState.FATAL_ERROR,
)
