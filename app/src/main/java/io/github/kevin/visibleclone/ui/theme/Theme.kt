package io.github.kevin.visibleclone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Calm, low-arousal palette: muted greens and warm neutrals rather than WHOOP's saturated teal/red.
private val Calm = Color(0xFF2F6F5E)
private val CalmLight = Color(0xFFB7DACF)
private val Sand = Color(0xFFF4F1EA)
private val Ink = Color(0xFF1D2422)
private val Amber = Color(0xFFB4762A)
private val AmberLight = Color(0xFFF2D9B4)
private val Rose = Color(0xFF9C4A4A)
private val RoseLight = Color(0xFFF0C9C9)

object ZoneColors {
    val resting = Color(0xFF5C7A6E)
    val activity = Amber
    val exertion = Rose
}

fun zoneColor(zone: String, dark: Boolean): Color = when (zone) {
    "EXERTION" -> Rose
    "ACTIVITY" -> Amber
    else -> Calm
}

private val LightScheme = lightColorScheme(
    primary = Calm,
    onPrimary = Color.White,
    primaryContainer = CalmLight,
    onPrimaryContainer = Ink,
    secondary = Amber,
    secondaryContainer = AmberLight,
    error = Rose,
    errorContainer = RoseLight,
    background = Sand,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE6E1D7),
    onSurfaceVariant = Color(0xFF4C554F),
)

private val DarkScheme = darkColorScheme(
    primary = CalmLight,
    onPrimary = Color(0xFF0E2B24),
    primaryContainer = Color(0xFF235146),
    onPrimaryContainer = Color(0xFFD5EDE4),
    secondary = AmberLight,
    onSecondary = Color(0xFF3A2608),
    secondaryContainer = Color(0xFF5E4315),
    error = Color(0xFFE8A8A8),
    errorContainer = Color(0xFF5C2A2A),
    background = Color(0xFF121614),
    onBackground = Color(0xFFE4E8E5),
    surface = Color(0xFF1A1F1D),
    onSurface = Color(0xFFE4E8E5),
    surfaceVariant = Color(0xFF2A302D),
    onSurfaceVariant = Color(0xFFB7C0BA),
)

@Composable
fun VisibleCloneTheme(useDark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (useDark) DarkScheme else LightScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
