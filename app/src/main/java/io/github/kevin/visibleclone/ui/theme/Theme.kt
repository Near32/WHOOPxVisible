package io.github.kevin.visibleclone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightScheme = lightColorScheme(
    primary = Visible.mintDeep,
    onPrimary = Color.White,
    primaryContainer = Visible.mint,
    onPrimaryContainer = Visible.ink,
    secondary = Visible.activity,
    onSecondary = Visible.ink,
    secondaryContainer = Visible.lavender,
    onSecondaryContainer = Visible.ink,
    tertiary = Visible.exertion,
    background = Visible.canvasLight,
    onBackground = Visible.ink,
    surface = Visible.cardLight,
    onSurface = Visible.ink,
    surfaceVariant = Visible.lavender,
    onSurfaceVariant = Visible.inkSoft,
    surfaceContainerHigh = Color(0xFFEFE9F6),
    error = Visible.exertion,
    errorContainer = Color(0xFFFFE3DE),
    onErrorContainer = Color(0xFF5C1F17),
    outline = Visible.lavenderDeep,
)

private val DarkScheme = darkColorScheme(
    primary = Visible.mint,
    onPrimary = Color(0xFF0B2A22),
    primaryContainer = Visible.mintDeep,
    onPrimaryContainer = Color(0xFFD9F5EC),
    secondary = Visible.activity,
    onSecondary = Color(0xFF2B1F03),
    secondaryContainer = Color(0xFF3C2D45),
    onSecondaryContainer = Visible.onDark,
    tertiary = Visible.exertion,
    background = Visible.canvasDark,
    onBackground = Visible.onDark,
    surface = Visible.cardDark,
    onSurface = Visible.onDark,
    surfaceVariant = Color(0xFF2E2635),
    onSurfaceVariant = Visible.onDarkSoft,
    surfaceContainerHigh = Color(0xFF2A2231),
    error = Visible.exertion,
    errorContainer = Color(0xFF4A211C),
    onErrorContainer = Color(0xFFFFD9D3),
    outline = Color(0xFF453B50),
)

/** Rounded, slightly soft typography — Visible's UI is friendly rather than clinical. */
private val AppTypography
    @Composable get() = MaterialTheme.typography.let { base ->
        base.copy(
            displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold),
            headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
        )
    }

@Composable
fun VisibleCloneTheme(useDark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (useDark) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content,
    )
}
