package com.shariftechnologies.scammonitor.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Color.darken(fraction: Float): Color = lerp(this, Color.Black, fraction.coerceIn(0f, 1f))

fun Color.lighten(fraction: Float): Color = lerp(this, Color.White, fraction.coerceIn(0f, 1f))

/**
 * A soft shadow built from stacked translucent rounded rectangles. It needs no blur API, so it looks the same on
 * every Android version, and (unlike elevation) it can be offset in any direction and tinted, which the clay and
 * soft-shadow looks need.
 */
fun Modifier.fauxShadow(
    color: Color,
    spread: Dp,
    dx: Dp = 0.dp,
    dy: Dp = 0.dp,
    corner: Dp,
    layers: Int = 8,
): Modifier = drawBehind {
    val s = spread.toPx()
    val ox = dx.toPx()
    val oy = dy.toPx()
    val c = corner.toPx()
    for (i in layers downTo 1) {
        val t = i / layers.toFloat()
        val grow = s * t
        val a = (color.alpha * (1f - t * 0.85f) * 2.2f / layers).coerceIn(0f, 1f)
        drawRoundRect(
            color = color.copy(alpha = a),
            topLeft = Offset(ox - grow, oy - grow),
            size = Size(size.width + 2 * grow, size.height + 2 * grow),
            cornerRadius = CornerRadius(c + grow),
        )
    }
}

/**
 * The backdrop every screen sits on: a soft gradient with three slowly drifting colour blooms. Glass surfaces are
 * translucent, so these blooms are what gives them depth.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val g = LocalGlass.current
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(g.bgTop, g.bgBottom)))
            .drawBehind {
                val w = size.width
                val h = size.height
                fun bloom(c: Color, cx: Float, cy: Float, r: Float) =
                    drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
                bloom(g.blobA, w * (0.10f + 0.30f * drift), h * 0.10f, w * 0.80f)
                bloom(g.blobB, w * (0.98f - 0.35f * drift), h * 0.45f, w * 0.75f)
                bloom(g.blobC, w * (0.20f + 0.25f * drift), h * 0.92f, w * 0.85f)
            },
        content = content,
    )
}
