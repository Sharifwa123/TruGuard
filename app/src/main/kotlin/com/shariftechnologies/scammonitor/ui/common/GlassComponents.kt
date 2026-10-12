package com.shariftechnologies.scammonitor.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shariftechnologies.scammonitor.R
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.ui.theme.LocalGlass
import com.shariftechnologies.scammonitor.ui.theme.darken
import com.shariftechnologies.scammonitor.ui.theme.fauxShadow
import com.shariftechnologies.scammonitor.ui.theme.lighten
import com.shariftechnologies.scammonitor.ui.theme.severityColor
import com.shariftechnologies.scammonitor.ui.theme.severityTextColor

enum class Tone { Accent, Good, Warn, Danger, Neutral }

private class ToneColors(val top: Color, val bottom: Color, val glow: Color, val onColor: Color)

@Composable
private fun toneColors(tone: Tone): ToneColors {
    val g = LocalGlass.current
    return when (tone) {
        Tone.Accent -> ToneColors(g.accent.lighten(0.14f), g.accentDeep, g.accentDeep, Color.White)
        Tone.Good -> ToneColors(g.good.lighten(0.12f), g.goodDeep, g.goodDeep, Color.White)
        Tone.Warn -> ToneColors(g.warn.lighten(0.1f), g.warnDeep, g.warnDeep, Color(0xFF2A1B00))
        Tone.Danger -> ToneColors(g.danger.lighten(0.1f), g.dangerDeep, g.dangerDeep, Color.White)
        Tone.Neutral -> ToneColors(
            g.neuBase.lighten(if (g.dark) 0.10f else 0.65f),
            g.neuBase.darken(if (g.dark) 0.25f else 0.07f),
            g.shadow,
            g.ink,
        )
    }
}

/** Frosted-glass surface: translucent gradient, a bright rim and a soft sheen. Optionally tappable with a springy press. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(14.dp),
    tint: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(corner)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && onClick != null) 0.975f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "cardPress",
    )
    val haptic = LocalHapticFeedback.current
    var m = modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .fauxShadow(g.shadow, 14.dp, 0.dp, 6.dp, corner)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(g.glassHi, g.glassLo)))
    if (tint != null) m = m.background(tint.copy(alpha = 0.12f))
    m = m
        .drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = if (g.dark) 0.10f else 0.45f), Color.Transparent),
                    center = Offset(size.width * 0.12f, 0f),
                    radius = size.width * 0.9f,
                ),
            )
        }
        .border(1.dp, Brush.linearGradient(listOf(g.rimHi, g.rimLo, g.rimHi.copy(alpha = g.rimHi.alpha * 0.5f))), shape)
    if (onClick != null) {
        m = m.clickable(interactionSource = source, indication = null, role = Role.Button) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    }
    Column(m.padding(padding), content = content)
}

/** Puffy "clay" button: gradient body, bright top rim, tinted drop shadow that shrinks as it is pressed. */
@Composable
fun ClayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: Tone = Tone.Accent,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val c = toneColors(tone)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val p by animateFloatAsState(if (pressed) 1f else 0f, spring(0.7f, 600f), label = "clayPress")
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .heightIn(min = if (compact) 36.dp else 46.dp)
            .graphicsLayer {
                val s = 1f - 0.04f * p
                scaleX = s
                scaleY = s
                alpha = if (enabled) 1f else 0.5f
            }
            .fauxShadow(c.glow.copy(alpha = 0.42f), 12.dp * (1f - 0.4f * p), 0.dp, 6.dp * (1f - 0.5f * p), 50.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c.top, c.bottom)))
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.10f)),
                        startY = size.height * 0.55f,
                        endY = size.height,
                    ),
                )
            }
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.05f))), shape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = if (compact) 14.dp else 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = c.onColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = c.onColor, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun ClayIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, tone: Tone = Tone.Neutral) {
    val c = toneColors(tone)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val p by animateFloatAsState(if (pressed) 1f else 0f, spring(0.7f, 600f), label = "iconPress")
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .size(44.dp)
            .graphicsLayer { val s = 1f - 0.06f * p; scaleX = s; scaleY = s }
            .fauxShadow(c.glow.copy(alpha = 0.4f), 10.dp * (1f - 0.4f * p), 0.dp, 5.dp * (1f - 0.5f * p), 22.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(c.top, c.bottom)))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.05f))), CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = c.onColor, modifier = Modifier.size(20.dp)) }
}

/** Switch with an inset track and a puffy knob that springs across. */
@Composable
fun ClaySwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val g = LocalGlass.current
    val t by animateFloatAsState(if (checked) 1f else 0f, spring(0.65f, 420f), label = "switch")
    val track = lerp(g.trackOff, g.accent, t)
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .size(width = 54.dp, height = 32.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Switch,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(it)
            },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(track.darken(0.14f), track)))
                .border(1.dp, Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.20f), Color.White.copy(alpha = 0.45f))), CircleShape),
        )
        Box(
            Modifier
                .padding(start = 3.dp + 22.dp * t, top = 3.dp)
                .size(26.dp)
                .fauxShadow(Color.Black.copy(alpha = 0.32f), 6.dp, 0.dp, 3.dp, 13.dp, 5)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFD9E0F2))))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White, Color(0x33FFFFFF))), CircleShape),
        )
    }
}

/** A switch row: title, optional one-line hint, and the clay switch. */
@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, hint: String? = null, enabled: Boolean = true) {
    val g = LocalGlass.current
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = g.ink)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = g.inkSoft)
        }
        ClaySwitch(checked, onChange, enabled = enabled)
    }
}

/** Soft-shadow ("neumorphic") tile: it sits proud of the background and presses in when tapped. */
@Composable
fun StatTile(value: String, label: String, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val g = LocalGlass.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val p by animateFloatAsState(if (pressed) 1f else 0f, spring(0.8f, 500f), label = "tilePress")
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(22.dp)
    var m = modifier
        .fauxShadow(g.neuLight, 10.dp * (1f - 0.6f * p), (-5).dp * (1f - 0.8f * p), (-5).dp * (1f - 0.8f * p), 22.dp, 6)
        .fauxShadow(g.neuDark, 10.dp * (1f - 0.6f * p), 5.dp * (1f - 0.8f * p), 5.dp * (1f - 0.8f * p), 22.dp, 6)
        .clip(shape)
        .background(
            Brush.linearGradient(
                listOf(
                    lerp(g.neuBase.lighten(0.35f), g.neuBase.darken(0.05f), p),
                    lerp(g.neuBase.darken(0.04f), g.neuBase.lighten(0.25f), p),
                ),
            ),
        )
    if (onClick != null) {
        m = m.clickable(interactionSource = source, indication = null, role = Role.Button) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    }
    Column(m.padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = g.inkSoft, maxLines = 1)
    }
}

@Composable
fun SeverityPill(severity: Severity, modifier: Modifier = Modifier) {
    val c = severityColor(severity)
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(c.copy(alpha = 0.16f))
            .border(1.dp, c.copy(alpha = 0.5f), shape)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(5.dp))
        Text(severity.name, color = severityTextColor(severity), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Selectable pill: glass when off, glowing clay when on. */
@Composable
fun GlassChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val g = LocalGlass.current
    val t by animateFloatAsState(if (selected) 1f else 0f, spring(0.8f, 500f), label = "chip")
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .heightIn(min = 36.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .fauxShadow(g.accentDeep.copy(alpha = 0.40f * t), 10.dp, 0.dp, 4.dp, 50.dp, 6)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(g.glassHi, g.accent.lighten(0.1f), t), lerp(g.glassLo, g.accentDeep, t))))
            .border(1.dp, Brush.verticalGradient(listOf(g.rimHi, g.rimLo)), shape)
            .clickable(enabled = enabled, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = lerp(g.ink, Color.White, t), style = MaterialTheme.typography.labelLarge) }
}

/** A glossy orb with an icon (the shield by default), optionally with a slow pulse to show it is live. */
@Composable
fun ShieldBadge(tone: Tone, modifier: Modifier = Modifier, size: Dp = 72.dp, pulsing: Boolean = false, icon: ImageVector? = null) {
    val c = toneColors(tone)
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (pulsing) {
            Canvas(Modifier.matchParentSize()) {
                val r = this.size.minDimension / 2f * (0.78f + 0.5f * pulse)
                drawCircle(c.glow.copy(alpha = 0.32f * (1f - pulse)), radius = r)
            }
        }
        Box(
            Modifier
                .size(size * 0.74f)
                .fauxShadow(c.glow.copy(alpha = 0.5f), 14.dp, 0.dp, 7.dp, size)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(c.top, c.bottom)))
                .border(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.05f))), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = c.onColor, modifier = Modifier.size(size * 0.38f))
            else Icon(painterResource(R.drawable.ic_stat_shield), contentDescription = null, tint = c.onColor, modifier = Modifier.size(size * 0.42f))
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 0.9.sp,
        color = LocalGlass.current.inkSoft,
    )
}

@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalGlass.current.ink.copy(alpha = 0.08f)))
}

@Composable
fun Chevron(open: Boolean, modifier: Modifier = Modifier) {
    val angle by animateFloatAsState(if (open) 180f else 0f, spring(0.8f, 500f), label = "chevron")
    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = LocalGlass.current.inkSoft, modifier = modifier.size(22.dp).rotate(angle))
}

@Composable
fun Expand(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) { content() }
}

@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, tone: Tone = Tone.Accent) {
    val g = LocalGlass.current
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ShieldBadge(tone, size = 84.dp, icon = icon)
        Text(title, style = MaterialTheme.typography.titleMedium, color = g.ink, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = g.inkSoft, textAlign = TextAlign.Center)
    }
}

class NavSpec(val route: String, val label: String, val icon: ImageVector)

/** Floating glass tab bar: the selected tab grows into a glowing clay pill with its label. */
@Composable
fun GlassNavBar(items: List<NavSpec>, selectedRoute: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(32.dp)
    Box(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .fauxShadow(g.shadow, 18.dp, 0.dp, 8.dp, 32.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(g.glassHi.copy(alpha = (g.glassHi.alpha + 0.2f).coerceAtMost(1f)), g.glassLo.copy(alpha = (g.glassLo.alpha + 0.2f).coerceAtMost(1f)))))
                .border(1.dp, Brush.verticalGradient(listOf(g.rimHi, g.rimLo)), shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = item.route == selectedRoute
                val weight by animateFloatAsState(if (selected) 2.1f else 1f, spring(0.8f, 380f), label = "navWeight")
                NavCell(item, selected, Modifier.weight(weight).fillMaxHeight().padding(5.dp)) { onSelect(item.route) }
            }
        }
    }
}

@Composable
private fun RowScope.NavCell(item: NavSpec, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val g = LocalGlass.current
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(26.dp)
    val bubble = if (selected) {
        Modifier
            .fauxShadow(g.accentDeep.copy(alpha = 0.40f), 10.dp, 0.dp, 4.dp, 26.dp, 6)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(g.accent.lighten(0.14f), g.accentDeep)))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.05f))), shape)
    } else Modifier.clip(shape)
    Box(
        modifier
            .then(bubble)
            .clickable(role = Role.Tab) {
                if (!selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { contentDescription = item.label },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(item.icon, contentDescription = null, tint = if (selected) Color.White else g.inkSoft, modifier = Modifier.size(22.dp))
            AnimatedVisibility(visible = selected, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                Text(
                    item.label,
                    modifier = Modifier.padding(start = 6.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
