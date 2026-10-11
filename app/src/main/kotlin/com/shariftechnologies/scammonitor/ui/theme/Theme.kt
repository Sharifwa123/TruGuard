package com.shariftechnologies.scammonitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.shariftechnologies.scammonitor.domain.model.Severity

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF062E6F),
    background = Color(0xFF111418),
    onBackground = Color(0xFFE3E6EA),
    surface = Color(0xFF111418),
    onSurface = Color(0xFFE3E6EA),
    surfaceVariant = Color(0xFF1E2329),
    onSurfaceVariant = Color(0xFFB6BCC4),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A5FB4),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAFB),
    onBackground = Color(0xFF1B1D20),
    surface = Color(0xFFFAFAFB),
    onSurface = Color(0xFF1B1D20),
    surfaceVariant = Color(0xFFEBEEF2),
    onSurfaceVariant = Color(0xFF454B53),
    error = Color(0xFFB3261E),
)

@Composable
fun ScamMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

/** Severity is always paired with a text label, never conveyed by colour alone. */
@Composable
fun severityColor(severity: Severity): Color {
    val dark = isSystemInDarkTheme()
    return when (severity) {
        Severity.HIGH -> if (dark) Color(0xFFFF8A80) else Color(0xFFB3261E)
        Severity.MEDIUM -> if (dark) Color(0xFFFFB74D) else Color(0xFF9A5B00)
        Severity.LOW -> if (dark) Color(0xFFFFE082) else Color(0xFF6B5A00)
        Severity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
