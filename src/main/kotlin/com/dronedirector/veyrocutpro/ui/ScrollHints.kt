package com.dronedirector.veyrocutpro.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Scrolling with a visible hint: when there is more content in a direction, that edge fades out
 * softly and a tiny chevron appears there. The chevron nudges gently three times when it shows
 * up, then rests faintly. Nothing is drawn when everything fits.
 */
fun Modifier.hintedScroll(horizontal: Boolean, fade: Dp = 22.dp, tint: Color = Color.White): Modifier = composed {
    val state = rememberScrollState()
    val nudge = remember { Animatable(0f) }
    val canBack = state.canScrollBackward
    val canForward = state.canScrollForward

    LaunchedEffect(canBack, canForward) {
        nudge.snapTo(0f)
        if (canBack || canForward) {
            repeat(3) {
                nudge.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
                nudge.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
            }
        }
    }

    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val f = fade.toPx().coerceAtMost(if (horizontal) size.width / 3 else size.height / 3)
            val w = size.width
            val h = size.height
            val back = state.canScrollBackward
            val forward = state.canScrollForward

            // Soft edge fades (mask the content).
            if (horizontal) {
                if (back) drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = f),
                    size = Size(f, h), blendMode = BlendMode.DstIn,
                )
                if (forward) drawRect(
                    Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = w - f, endX = w),
                    topLeft = Offset(w - f, 0f), size = Size(f, h), blendMode = BlendMode.DstIn,
                )
            } else {
                if (back) drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = f),
                    size = Size(w, f), blendMode = BlendMode.DstIn,
                )
                if (forward) drawRect(
                    Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = h - f, endY = h),
                    topLeft = Offset(0f, h - f), size = Size(w, f), blendMode = BlendMode.DstIn,
                )
            }

            // Tiny chevrons at the edges that have more to see.
            val n = nudge.value
            val alpha = 0.5f + 0.4f * n
            val shift = 3.dp.toPx() * n
            val inset = 8.dp.toPx()
            if (horizontal) {
                if (forward) chevron(Offset(w - inset + shift, h / 2), 0, tint.copy(alpha = alpha))
                if (back) chevron(Offset(inset - shift, h / 2), 2, tint.copy(alpha = alpha))
            } else {
                if (forward) chevron(Offset(w / 2, h - inset + shift), 1, tint.copy(alpha = alpha))
                if (back) chevron(Offset(w / 2, inset - shift), 3, tint.copy(alpha = alpha))
            }
        }
        .then(if (horizontal) Modifier.horizontalScroll(state) else Modifier.verticalScroll(state))
}

/** A small open chevron centred on [c]. direction: 0 = right, 1 = down, 2 = left, 3 = up. */
private fun DrawScope.chevron(c: Offset, direction: Int, color: Color) {
    val a = 3.dp.toPx() // depth
    val b = 5.dp.toPx() // half-height
    val path = Path().apply {
        when (direction) {
            0 -> { moveTo(c.x - a, c.y - b); lineTo(c.x + a, c.y); lineTo(c.x - a, c.y + b) }
            2 -> { moveTo(c.x + a, c.y - b); lineTo(c.x - a, c.y); lineTo(c.x + a, c.y + b) }
            1 -> { moveTo(c.x - b, c.y - a); lineTo(c.x, c.y + a); lineTo(c.x + b, c.y - a) }
            else -> { moveTo(c.x - b, c.y + a); lineTo(c.x, c.y - a); lineTo(c.x + b, c.y + a) }
        }
    }
    // Faint dark halo keeps the chevron readable over bright camera images.
    drawPath(path, Color.Black.copy(alpha = color.alpha * 0.5f), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, color, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
