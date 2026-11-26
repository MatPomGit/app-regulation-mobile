package pl.edu.prz.arcadhd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import pl.edu.prz.arcadhd.task.GateColor
import pl.edu.prz.arcadhd.task.GateRule
import pl.edu.prz.arcadhd.task.PreviewStage
import pl.edu.prz.arcadhd.task.SignalGatePreview
import pl.edu.prz.arcadhd.task.MissionKeepsake
import pl.edu.prz.arcadhd.task.missionKeepsakes

/** Extra text/shape cues apply only to this untimed engineering preview. */
@Composable
fun SignalGatePreviewScreen(
    preview: SignalGatePreview,
    onChange: (SignalGatePreview) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler {
        if (preview.isActive) onChange(preview.pause()) else onExit()
    }
    var robot by remember { mutableStateOf(RobotCompanion.ISKRA) }
    var album by remember { mutableStateOf(emptySet<MissionKeepsake>()) }
    val visibleAlbum = album + missionKeepsakes(preview.trialIndex)
    SideEffect { if (album != visibleAlbum) album = visibleAlbum }
    val terminal = preview.stage in setOf(PreviewStage.COMPLETE, PreviewStage.STOPPED)
    PreviewFrame("Misja kolorów", modifier, footer = {
        SafetyActions(
            pauseEnabled = preview.isActive, stopEnabled = !terminal,
            showPause = preview.isActive, showStop = !terminal,
            onPause = { onChange(preview.pause()) }, onStop = { onChange(preview.stop()) },
        )
    }) {
        TechnicalBadge()
        StatusMessage(when (preview.stage) {
            PreviewStage.READY -> "Gotowe do rozpoczęcia"
            PreviewStage.TASK -> "Próba ${preview.trialIndex + 1} z ${SignalGatePreview.TRIAL_COUNT}"
            PreviewStage.RULE_SWITCH -> "Zmiana reguły. Potwierdź nową regułę przed następną próbą."
            PreviewStage.PAUSED -> "Podgląd wstrzymany. Bieżąca próba pozostaje bez odpowiedzi."
            PreviewStage.COMPLETE -> "Podgląd zakończony"
            PreviewStage.STOPPED -> "Podgląd zatrzymany"
        })
        if (preview.stage != PreviewStage.READY && preview.stage != PreviewStage.TASK) {
            GateRoute(preview.trialIndex)
        }
        when (preview.stage) {
            PreviewStage.READY -> {
                CompanionPicker(robot) { robot = it }
                GateRoute(0)
                SectionCard("Pomóż robotowi odczytać sygnały") {
                Text("Przed Tobą osiem sygnałów. Sprawdź, kiedy zareagować, a kiedy je pominąć. Nie musisz się spieszyć.")
                Text("Reguła A: niebieski okrąg = reaguj, pomarańczowy kwadrat = pomiń.")
                Text("W połowie drogi zasada się zmieni. Na ekranie pojawi się nowa zasada przed dalszą drogą.")
                Text("Próby nie mierzą czasu; odpowiedzi pozostają tylko w pamięci.")
                Text("Przycisk „Pomiń” służy wyłącznie do sprawdzania logiki podglądu.")
                }
                MissionAlbum(visibleAlbum)
                PreviewAction("Ruszamy w drogę") { onChange(preview.start()) }
            }
            PreviewStage.TASK -> {
                val displayedTrialIndex = preview.trialIndex
                SectionCard("Aktualna reguła") {
                Text(if (preview.activeRule == GateRule.A)
                    "Reguła A: reaguj na niebieski okrąg"
                else "Reguła B: reaguj na pomarańczowy kwadrat")
                }
                val blue = preview.currentStimulus == GateColor.BLUE
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Label outside the shape scales freely; the decorative shape is not read twice.
                    Box(Modifier.size(96.dp).clearAndSetSemantics {}.background(
                        if (blue) ArcColors.blue else ArcColors.orange,
                        if (blue) CircleShape else RoundedCornerShape(8.dp),
                    ))
                    Text(if (blue) "Niebieski okrąg" else "Pomarańczowy kwadrat",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                PreviewAction("Reaguj") { onChange(preview.submit(displayedTrialIndex, true)) }
                OutlinedButton(onClick = { onChange(preview.submit(displayedTrialIndex, false)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Pomiń") }
            }
            PreviewStage.RULE_SWITCH -> {
                RobotPortrait(robot, Modifier.align(Alignment.CenterHorizontally))
                MissionAlbum(visibleAlbum)
                SectionCard("Nowa zasada na drugą część drogi") {
                    Text("Teraz reaguj na pomarańczowy kwadrat. Niebieski okrąg pomiń.")
                    Text("Przeczytaj spokojnie. Ruszamy dalej, kiedy zechcesz.")
                }
                PreviewAction("Rozumiem, kontynuuj") { onChange(preview.acknowledgeSwitch()) }
            }
            PreviewStage.PAUSED -> {
                Text("Zrób przerwę, jeśli jej potrzebujesz. Możesz wrócić albo zakończyć misję.")
                PreviewAction("Wznów") { onChange(preview.resume()) }
            }
            PreviewStage.COMPLETE -> {
                RobotPortrait(robot, Modifier.align(Alignment.CenterHorizontally))
                MissionAlbum(visibleAlbum)
                SectionCard("Misja ukończona") {
                    Text("Dziękujemy za sprawdzenie wszystkich sygnałów. Teraz możesz odpocząć albo spróbować jeszcze raz.")
                    Text("Odpowiedzi zgodne z regułą: ${preview.correctCount} / ${SignalGatePreview.TRIAL_COUNT}")
                    Text("To sprawdzenie reguł. Nie jest oceną zdrowia ani pomiarem powrotu do zadania.")
                }
            }
            PreviewStage.STOPPED -> {
                SectionCard("Wyprawa zatrzymana") {
                    Text("Możesz wrócić do ekranu głównego albo spróbować ponownie, gdy zechcesz.")
                }
                MissionAlbum(visibleAlbum)
            }
        }
        if (terminal) PreviewAction("Spróbuj ponownie") { onChange(SignalGatePreview()) }
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Wróć do ekranu głównego")
        }
    }
}
