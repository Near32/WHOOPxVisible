package io.github.kevin.visibleclone.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Visible's palette: a deep aubergine background with lavender cards, mint for calm, amber for
 * activity, coral for exertion. Deliberately low-arousal — this is an app for people in burnout.
 */
object Visible {
    // Backgrounds
    val canvasLight = Color(0xFFF6F4F8)
    val canvasDark = Color(0xFF141018)
    val cardLight = Color(0xFFFBF9FD)
    val cardDark = Color(0xFF221C28)

    // Brand
    val plum = Color(0xFF3B2A45)
    val lavender = Color(0xFFE7DEF2)
    val lavenderDeep = Color(0xFFCDBDE3)
    val mint = Color(0xFF7FD9C0)
    val mintDeep = Color(0xFF3FA98B)

    // Zones
    val rest = Color(0xFF9B8FB3)
    val activity = Color(0xFFF2B84B)
    val exertion = Color(0xFFF07C6C)

    // Text
    val ink = Color(0xFF2B2233)
    val inkSoft = Color(0xFF6B6275)
    val onDark = Color(0xFFF2EEF7)
    val onDarkSoft = Color(0xFFA79FB3)
}

object ZonePalette {
    fun color(zone: String): Color = when (zone) {
        "EXERTION" -> Visible.exertion
        "ACTIVITY" -> Visible.activity
        else -> Visible.rest
    }

    fun label(zone: String): String = when (zone) {
        "EXERTION" -> "Exertion"
        "ACTIVITY" -> "Activity"
        else -> "Rest"
    }
}
