package com.dronedirector.veyrocutpro.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
 *
 * When the content is longer than the space, it gets an empty strip at both ends as wide as the
 * chevron's place ([ARROW_SPACE]), so scrolled all the way to either end, no button or control
 * sits under a chevron. When everything fits, the layout is unchanged.
 */
fun Modifier.hintedScroll(horizontal: Boolean, fade: Dp = 22.dp, tint: Color = Color.White, scroll: androidx.compose.foundation.ScrollState? = null): Modifier = composed {
    val state = scroll ?: rememberScrollState()
    val space = if (state.maxValue > 0) ARROW_SPACE else 0.dp
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
        .padding(horizontal = if (horizontal) space else 0.dp, vertical = if (horizontal) 0.dp else space)
}

/** Room for a chevron at a scrolling edge (it sits within this strip, including its nudge). */
val ARROW_SPACE: Dp = 18.dp

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

/**
 * A group of top-bar icons that scrolls sideways (with the hint arrows) on phones, so the bar's
 * fixed buttons always keep their room; on tablets the icons sit in a plain row. Put it where a
 * weighted spacer would go: it takes the free width and lines the icons up at its end.
 */
@androidx.compose.runtime.Composable
fun androidx.compose.foundation.layout.RowScope.BarIconGroup(content: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val phone = androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp < 600
    if (!phone) {
        androidx.compose.foundation.layout.Box(Modifier.weight(1f), contentAlignment = androidx.compose.ui.Alignment.CenterEnd) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, content = content)
        }
        return
    }
    // Phones: the whole free width (and height) of the bar is the scroll area, so it's easy to swipe;
    // the icons still line up at its right end when they fit.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
        val w = maxWidth
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxHeight().hintedScroll(horizontal = true).widthIn(min = w),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            content = content,
        )
    }
}
