package com.shariftechnologies.scammonitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.shariftechnologies.scammonitor.domain.model.Severity

/**
 * Colours for the glass / clay / soft-shadow look. One light and one dark set; every custom surface reads from here
 * so the whole app changes together.
 */
@Immutable
class GlassColors(
    val dark: Boolean,
    val bgTop: Color,
    val bgBottom: Color,
    val blobA: Color,
    val blobB: Color,
    val blobC: Color,
    val glassHi: Color,
    val glassLo: Color,
    val rimHi: Color,
    val rimLo: Color,
    val shadow: Color,
    val ink: Color,
    val inkSoft: Color,
    val neuBase: Color,
    val neuLight: Color,
    val neuDark: Color,
    val trackOff: Color,
    val accent: Color,
    val accentDeep: Color,
    val good: Color,
    val goodDeep: Color,
    val warn: Color,
    val warnDeep: Color,
    val danger: Color,
    val dangerDeep: Color,
)

val LightGlass = GlassColors(
    dark = false,
    bgTop = Color(0xFFE8EEFF), bgBottom = Color(0xFFF8EEF8),
    blobA = Color(0x886E9BFF), blobB = Color(0x77B08CFF), blobC = Color(0x776FE0CF),
    glassHi = Color(0xB3FFFFFF), glassLo = Color(0x73FFFFFF),
    rimHi = Color(0xF2FFFFFF), rimLo = Color(0x40FFFFFF),
    shadow = Color(0x331E2A5A),
    ink = Color(0xFF141A2B), inkSoft = Color(0xFF5A6482),
    neuBase = Color(0xFFEAEFFA), neuLight = Color(0xE6FFFFFF), neuDark = Color(0x66A3B1D4),
    trackOff = Color(0xFFCBD3E8),
    accent = Color(0xFF4A78FF), accentDeep = Color(0xFF2A4FDB),
    good = Color(0xFF28C48A), goodDeep = Color(0xFF0E9366),
    warn = Color(0xFFFFB02E), warnDeep = Color(0xFFE08A00),
    danger = Color(0xFFFF6B63), dangerDeep = Color(0xFFD13A33),
)

val DarkGlass = GlassColors(
    dark = true,
    bgTop = Color(0xFF0A0F1E), bgBottom = Color(0xFF140C26),
    blobA = Color(0x772F5BFF), blobB = Color(0x667C4DFF), blobC = Color(0x5512B8A6),
    glassHi = Color(0x24FFFFFF), glassLo = Color(0x0FFFFFFF),
    rimHi = Color(0x66FFFFFF), rimLo = Color(0x0DFFFFFF),
    shadow = Color(0x99000000),
    ink = Color(0xFFEEF1FA), inkSoft = Color(0xFFA4ADC8),
    neuBase = Color(0xFF151B30), neuLight = Color(0x14FFFFFF), neuDark = Color(0x99000000),
    trackOff = Color(0xFF2B3350),
    accent = Color(0xFF6E94FF), accentDeep = Color(0xFF3D63EE),
    good = Color(0xFF3DDC97), goodDeep = Color(0xFF16A870),
    warn = Color(0xFFFFC65C), warnDeep = Color(0xFFE59A12),
    danger = Color(0xFFFF8A83), dangerDeep = Color(0xFFE5483F),
)

val LocalGlass = staticCompositionLocalOf { LightGlass }

/** Denser than the Material defaults so more fits on screen; sizes still scale with the user's font setting. */
private val CompactTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium),
)

/** Largest font scale the layouts are designed for; larger system settings are honoured up to this point. */
private const val MAX_FONT_SCALE = 1.1f

@Composable
fun ScamMonitorTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val g = if (dark) DarkGlass else LightGlass
    val density = LocalDensity.current
    val scheme = if (dark) {
        darkColorScheme(
            primary = g.accent, onPrimary = Color.White, background = g.bgTop, onBackground = g.ink,
            surface = g.neuBase, onSurface = g.ink, surfaceVariant = g.neuBase, onSurfaceVariant = g.inkSoft,
            surfaceContainerHigh = g.neuBase, surfaceContainer = g.neuBase, error = g.danger, outline = g.inkSoft,
        )
    } else {
        lightColorScheme(
            primary = g.accent, onPrimary = Color.White, background = g.bgTop, onBackground = g.ink,
            surface = g.neuBase, onSurface = g.ink, surfaceVariant = g.neuBase, onSurfaceVariant = g.inkSoft,
            surfaceContainerHigh = Color(0xFFF4F7FF), surfaceContainer = g.neuBase, error = g.danger, outline = g.inkSoft,
        )
    }
    CompositionLocalProvider(
        LocalGlass provides g,
        LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(MAX_FONT_SCALE)),
    ) {
        MaterialTheme(colorScheme = scheme, typography = CompactTypography, content = content)
    }
}

/** Severity is always paired with a text label, never conveyed by colour alone. */
@Composable
fun severityColor(severity: Severity): Color {
    val g = LocalGlass.current
    return when (severity) {
        Severity.HIGH -> g.danger
        Severity.MEDIUM -> g.warn
        Severity.LOW -> if (g.dark) Color(0xFFFFE082) else Color(0xFF9A7B00)
        Severity.INFO -> g.inkSoft
    }
}

/** Text-safe version of a severity colour: the light-mode amber/red are too pale as small text on glass. */
@Composable
fun severityTextColor(severity: Severity): Color {
    val g = LocalGlass.current
    if (g.dark) return severityColor(severity)
    return when (severity) {
        Severity.HIGH -> g.dangerDeep
        Severity.MEDIUM -> Color(0xFFB36B00)
        Severity.LOW -> Color(0xFF7A6200)
        Severity.INFO -> g.inkSoft
    }
}
