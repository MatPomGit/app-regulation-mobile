package pl.edu.prz.arcadhd.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/** Check actual theme tokens so later palette edits cannot quietly reduce readable contrast. */
class ArcContrastTest {
    private fun luminance(color: Color): Double {
        fun linear(value: Float): Double = if (value <= 0.04045f) value / 12.92
            else ((value + 0.055) / 1.055).pow(2.4)
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }
    private fun contrast(a: Color, b: Color): Double {
        val first = luminance(a); val second = luminance(b)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    @Test fun readableThemePairsMeetNormalTextContrast() {
        listOf(ArcColors.light, ArcColors.dark).forEach { colors ->
            listOf(
                "primary" to (colors.primary to colors.onPrimary),
                "primaryContainer" to (colors.primaryContainer to colors.onPrimaryContainer),
                "secondary" to (colors.secondary to colors.onSecondary),
                "secondaryContainer" to (colors.secondaryContainer to colors.onSecondaryContainer),
                "surface" to (colors.surface to colors.onSurface),
                "surfaceVariant" to (colors.surfaceVariant to colors.onSurfaceVariant),
                "background" to (colors.background to colors.onBackground),
                "error" to (colors.error to colors.onError),
                "errorContainer" to (colors.errorContainer to colors.onErrorContainer),
            ).forEach { (name, pair) ->
                assertTrue("$name contrast must be >= 4.5", contrast(pair.first, pair.second) >= 4.5)
            }
            assertTrue("Outline must be discernible", contrast(colors.outline, colors.surface) >= 3.0)
        }
    }

    @Test fun bothStimuliRemainDiscernibleFromTheBackground() {
        listOf(ArcColors.light, ArcColors.dark).forEach { theme ->
            listOf(ArcColors.blue, ArcColors.orange).forEach { stimulus ->
                assertTrue("Stimulus contrast must be >= 3", contrast(stimulus, theme.surface) >= 3.0)
            }
        }
    }
}
