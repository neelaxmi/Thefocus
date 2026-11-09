package com.neelaxmi.focus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** NEELAXMI palette: deep ink, aurora teal (progress), warm amber (reward), soft violet (secondary data). Original. */
object Palette {
    val Ink = Color(0xFF0B1020); val Panel = Color(0xFF121933); val PanelHigh = Color(0xFF1B2547)
    val Aurora = Color(0xFF3DDBC0); val Amber = Color(0xFFFFB84D); val Violet = Color(0xFF8B7CFF)
    val TextHi = Color(0xFFE8ECFF); val TextLo = Color(0xFF9AA6D0); val Danger = Color(0xFFFF6B7A)
}

private val Dark = darkColorScheme(
    primary = Palette.Aurora, onPrimary = Palette.Ink, secondary = Palette.Amber, onSecondary = Palette.Ink,
    tertiary = Palette.Violet, background = Palette.Ink, onBackground = Palette.TextHi,
    surface = Palette.Panel, onSurface = Palette.TextHi, surfaceVariant = Palette.PanelHigh, onSurfaceVariant = Palette.TextLo,
    error = Palette.Danger, outline = Color(0xFF3A4672),
)
private val Light = lightColorScheme(
    primary = Color(0xFF0B8F7B), onPrimary = Color.White, secondary = Color(0xFFB36B00), tertiary = Color(0xFF5A4BDB),
    background = Color(0xFFF4F6FF), onBackground = Color(0xFF0B1020), surface = Color.White, onSurface = Color(0xFF0B1020),
    surfaceVariant = Color(0xFFE6EAFB), onSurfaceVariant = Color(0xFF4A557D), error = Color(0xFFC62840), outline = Color(0xFFB5BEDF),
)

/** Tabular figures so the countdown does not jitter as digits change. */
val TimerStyle = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp, fontFeatureSettings = "tnum")

private val Type = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
)

@Composable
fun NeelaxmiTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, content = content)
}
