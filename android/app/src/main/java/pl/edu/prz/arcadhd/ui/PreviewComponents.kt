package pl.edu.prz.arcadhd.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Scroll content independently of essential controls; limit line lengths on wide windows. */
@Composable
fun PreviewFrame(
    title: String,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize().padding(horizontal = 16.dp)
            .semantics { paneTitle = title }) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() })
                content()
            }
            footer()
        }
    }
}

@Composable
fun PreviewAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(label, textAlign = TextAlign.Center)
    }
}

@Composable
fun StatusMessage(text: String, error: Boolean = false, announce: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Surface(color = if (error) colors.errorContainer else colors.primaryContainer,
        contentColor = if (error) colors.onErrorContainer else colors.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(16.dp).semantics {
            if (announce) liveRegion = LiveRegionMode.Polite
        }, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Stack at large font sizes and narrow widths without reducing text or hit targets. */
@Composable
fun SafetyActions(
    pauseEnabled: Boolean, stopEnabled: Boolean,
    onPause: () -> Unit, onStop: () -> Unit,
    showPause: Boolean = true, showStop: Boolean = true,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        val stacked = maxWidth < 360.dp || fontScale >= 1.5f
        val pause: @Composable (Modifier) -> Unit = { modifier ->
            OutlinedButton(onClick = onPause, enabled = pauseEnabled,
                modifier = modifier.heightIn(min = 56.dp)) { Text("Pauza", textAlign = TextAlign.Center) }
        }
        val stop: @Composable (Modifier) -> Unit = { modifier ->
            Button(onClick = onStop, enabled = stopEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError),
                modifier = modifier.heightIn(min = 56.dp)) {
                Text("Zatrzymaj", textAlign = TextAlign.Center)
            }
        }
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showPause) pause(Modifier.fillMaxWidth())
            if (showStop) stop(Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showPause) pause(Modifier.weight(1f))
            if (showStop) stop(Modifier.weight(1f))
        }
    }
}

/** Group related content without turning supporting actions into primary calls to action. */
@Composable
fun SectionCard(
    title: String,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (highlighted) colors.primaryContainer else colors.surface,
        contentColor = if (highlighted) colors.onPrimaryContainer else colors.onSurface,
        tonalElevation = if (highlighted) 0.dp else 1.dp,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
fun SupportingAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
}

@Composable
fun TechnicalBadge() {
    Text("Podgląd techniczny • bez zbierania danych uczestników",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun DetailToggle(label: String, expanded: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
            stateDescription = if (expanded) "Rozwinięte" else "Zwinięte"
        }) {
        Text(if (expanded) "Ukryj: $label" else "Pokaż: $label")
    }
}
