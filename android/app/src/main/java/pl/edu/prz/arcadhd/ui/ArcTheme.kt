package pl.edu.prz.arcadhd.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Fixed engineering palette keeps contrast independent of the phone wallpaper. */
object ArcColors {
    val blue = Color(0xFF176ECB)
    val orange = Color(0xFFB74D00)
    val light = lightColorScheme(
        primary = Color(0xFF075E63), onPrimary = Color.White,
        primaryContainer = Color(0xFFD0EFED), onPrimaryContainer = Color(0xFF093C40),
        secondary = Color(0xFF375A73), onSecondary = Color.White,
        secondaryContainer = Color(0xFFDCEAF4), onSecondaryContainer = Color(0xFF17374A),
        background = Color(0xFFF5F8FA), onBackground = Color(0xFF182D35),
        surface = Color(0xFFF5F8FA), onSurface = Color(0xFF182D35),
        surfaceVariant = Color(0xFFE4ECEF), onSurfaceVariant = Color(0xFF3C525D),
        outline = Color(0xFF617580),
        error = Color(0xFFA52129), onError = Color.White,
        errorContainer = Color(0xFFFFDADB), onErrorContainer = Color(0xFF650E16),
    )
    val dark = darkColorScheme(
        primary = Color(0xFF89D5D0), onPrimary = Color(0xFF073C40),
        primaryContainer = Color(0xFF164E53), onPrimaryContainer = Color(0xFFCDF3F0),
        secondary = Color(0xFFB4D2E8), onSecondary = Color(0xFF18384B),
        secondaryContainer = Color(0xFF2C4B60), onSecondaryContainer = Color(0xFFDCEAF4),
        background = Color(0xFF101D24), onBackground = Color(0xFFE0EBF1),
        surface = Color(0xFF101D24), onSurface = Color(0xFFE0EBF1),
        surfaceVariant = Color(0xFF263B46), onSurfaceVariant = Color(0xFFC2D3DD),
        outline = Color(0xFF93A9B6),
        error = Color(0xFFFFB3B6), onError = Color(0xFF650E16),
        errorContainer = Color(0xFF7F2028), onErrorContainer = Color(0xFFFFDADB),
    )
}

@Composable
fun ArcTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) ArcColors.dark else ArcColors.light,
        content = content)
}
