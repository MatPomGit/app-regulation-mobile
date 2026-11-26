package pl.edu.prz.arcadhd.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import pl.edu.prz.arcadhd.task.MissionKeepsake
import pl.edu.prz.arcadhd.task.SignalGatePreview

enum class RobotCompanion(val label: String, val greeting: String) {
    ISKRA("Iskra", "Razem odkryjemy drogę przez kolorowe bramy."),
    LUNA("Luna", "Lubię spokojne wyprawy. Ty wybierasz, kiedy ruszamy."),
    PIKSEL("Piksel", "Sprawdźmy sygnały. W połowie drogi poznamy nową zasadę."),
}

/** Original static vector mascot. No animation or response-contingent sensory feedback. */
@Composable
fun RobotPortrait(robot: RobotCompanion, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val body = when (robot) {
        RobotCompanion.ISKRA -> Color(0xFF287D78)
        RobotCompanion.LUNA -> Color(0xFF6857A0)
        RobotCompanion.PIKSEL -> Color(0xFF346BA3)
    }
    val eye = Color(0xFF132B38)
    Canvas(modifier.size(104.dp).semantics { contentDescription = "Robot ${robot.label}" }) {
        val unit = size.minDimension / 100f
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        drawRoundRect(body, point(25f, 67f), Size(50f * unit, 25f * unit),
            CornerRadius(8f * unit))
        drawLine(body, point(50f, 13f), point(50f, 25f), 4f * unit)
        drawCircle(body, 5f * unit, point(50f, 10f))
        val radius = if (robot == RobotCompanion.PIKSEL) 5f else 15f
        drawRoundRect(body, point(12f, 24f), Size(76f * unit, 44f * unit),
            CornerRadius(radius * unit))
        drawRoundRect(Color(0xFFE4F5F4), point(21f, 33f), Size(58f * unit, 25f * unit),
            CornerRadius(8f * unit))
        drawCircle(eye, 4f * unit, point(36f, 43f))
        drawCircle(eye, 4f * unit, point(64f, 43f))
        drawLine(eye, point(42f, 52f), point(58f, 52f), 2f * unit)
        drawCircle(colors.onPrimary, 4f * unit, point(50f, 79f))
        if (robot == RobotCompanion.LUNA) {
            drawCircle(body, 6f * unit, point(9f, 45f))
            drawCircle(body, 6f * unit, point(91f, 45f))
        }
        if (robot == RobotCompanion.ISKRA) {
            drawLine(body, point(16f, 70f), point(8f, 83f), 5f * unit)
            drawLine(body, point(84f, 70f), point(92f, 83f), 5f * unit)
        }
    }
}

@Composable
fun CompanionPicker(selectedRobot: RobotCompanion, onSelect: (RobotCompanion) -> Unit) {
    SectionCard("Wybierz towarzysza wyprawy") {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            RobotPortrait(selectedRobot)
            Text(selectedRobot.label, style = MaterialTheme.typography.titleLarge)
            Text(selectedRobot.greeting)
        }
        RobotCompanion.entries.forEach { robot ->
            OutlinedButton(onClick = { onSelect(robot) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                    selected = robot == selectedRobot
                }) {
                Text(if (robot == selectedRobot) "${robot.label} • wybrano" else "Wybierz: ${robot.label}")
            }
        }
        Text("Wybór zmienia wygląd i opowieść. Zasady sygnałów są takie same.")
    }
}

@Composable
fun GateRoute(completedTrials: Int) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mapa wyprawy • przebyte bramy: $completedTrials z ${SignalGatePreview.TRIAL_COUNT}")
        // The single text above explains progress; cells are decorative, never navigation.
        Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..1).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..4).forEach { column ->
                        val gate = row * 4 + column
                        val reached = gate <= completedTrials
                        val next = gate == completedTrials + 1
                        Surface(
                            color = if (reached) colors.primaryContainer else colors.surface,
                            contentColor = if (reached) colors.onPrimaryContainer else colors.onSurface,
                            border = BorderStroke(if (next) 3.dp else 1.dp,
                                if (next) colors.primary else colors.outline),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(if (reached) "✓ $gate" else gate.toString())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MissionAlbum(keepsakes: Set<MissionKeepsake>) {
    SectionCard("Pamiątki z tej wyprawy") {
        if (keepsakes.isEmpty()) {
            Text("Pamiątki pojawią się po odwiedzeniu 2, 4 i 8 bram. Nie zależą od szybkości ani wyniku.")
        } else {
            keepsakes.sortedBy { it.threshold }.forEach {
                Text("★ ${it.title}", style = MaterialTheme.typography.titleMedium)
                Text(it.description)
            }
        }
        Text("Pauza i stop nie odbierają pamiątek. Pozostają przy ponowieniu misji na tym ekranie. Powrót do ekranu głównego lub zamknięcie aplikacji kończy ten album.")
    }
}
