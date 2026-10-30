package com.dronedirector.veyrocutpro.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.camera.CameraUiState
import com.dronedirector.veyrocutpro.camera.ProCameraController
import com.dronedirector.veyrocutpro.camera.Stops
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Manual focus rail on the right edge of the viewfinder: a narrow glass capsule with a scale,
 * an accent thumb and a distance tag. Top = ∞ (far), bottom = closest focus. Drag anywhere on
 * the rail to move the focus relative to where it is; the ✕ above it returns to autofocus.
 */
@Composable
fun FocusRail(
    s: CameraUiState,
    c: ProCameraController,
    onDragging: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val max = s.minFocusDiopters.coerceAtLeast(0.01f)
    // Square-root scale: more travel near ∞, where small changes matter most.
    val t = sqrt((s.focusDiopters / max).coerceIn(0f, 1f))
    val shownT by animateFloatAsState(t, tween(60), label = "focusRail")
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val accent = Pro.Amber
    val dragged by rememberUpdatedState(onDragging)
    val curT by rememberUpdatedState(t)
    val curMax by rememberUpdatedState(max)
    var active by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (active) 1f else 0f, tween(180), label = "railGlow")

    BoxWithConstraints(modifier) {
        val railH = (maxHeight * 0.52f).coerceIn(150.dp, 300.dp)
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Back to autofocus.
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        c.setFocusAuto()
                    }
                    .semantics { contentDescription = "Back to autofocus" },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(9.dp)) {
                    val w = 1.6.dp.toPx()
                    drawLine(Color.White, Offset.Zero, Offset(size.width, size.height), w, StrokeCap.Round)
                    drawLine(Color.White, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
                }
            }
            Text("MF", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)

            Box(Modifier.width(30.dp).height(railH)) {
                var railPx by remember { mutableFloatStateOf(1f) }
                var lastTick by remember { mutableIntStateOf(-1) }
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { railPx = it.height.toFloat() }
                        .pointerInput(Unit) {
                            val pad = 14.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                active = true; dragged(true)
                                val startY = down.position.y
                                val startT = curT
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    ch.consume()
                                    val span = (size.height - 2 * pad).coerceAtLeast(1f)
                                    val nt = (startT + (ch.position.y - startY) / span).coerceIn(0f, 1f)
                                    c.setFocus(nt * nt * curMax)
                                    // A soft tick every eighth of the scale.
                                    val tick = (nt * 8f).roundToInt()
                                    if (tick != lastTick && abs(nt * 8f - tick) < 0.12f) {
                                        if (lastTick != -1) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        lastTick = tick
                                    }
                                }
                                active = false; dragged(false); lastTick = -1
                            }
                        }
                        .semantics { contentDescription = "Manual focus slider" }
                ) {
                    val w = size.width
                    val h = size.height
                    val pad = 14.dp.toPx()
                    val cx = w / 2f
                    val r = w / 2f
                    // Glass capsule.
                    drawRoundRect(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.62f), Color.Black.copy(alpha = 0.38f), Color.Black.copy(alpha = 0.62f))),
                        cornerRadius = CornerRadius(r, r),
                    )
                    drawRoundRect(
                        Color.White.copy(alpha = 0.14f + 0.2f * glow),
                        cornerRadius = CornerRadius(r, r),
                        style = Stroke(1.dp.toPx()),
                    )
                    val y0 = pad
                    val span = h - 2 * pad
                    // Scale: 33 fine ticks, longer every quarter.
                    for (i in 0..32) {
                        val y = y0 + span * i / 32f
                        val major = i % 8 == 0
                        val len = if (major) 9.dp.toPx() else 4.dp.toPx()
                        drawLine(
                            Color.White.copy(alpha = if (major) 0.55f else 0.22f),
                            Offset(cx - len / 2f, y), Offset(cx + len / 2f, y),
                            if (major) 1.2.dp.toPx() else 0.8.dp.toPx(),
                        )
                    }
                    val ty = y0 + span * shownT
                    // Accent travel from ∞ down to the thumb.
                    drawLine(accent.copy(alpha = 0.85f), Offset(cx, y0), Offset(cx, ty), 2.dp.toPx(), StrokeCap.Round)
                    // Thumb: soft glow + pill.
                    drawCircle(accent.copy(alpha = 0.18f + 0.22f * glow), radius = 11.dp.toPx() + 4.dp.toPx() * glow, center = Offset(cx, ty))
                    val pw = w - 8.dp.toPx()
                    val ph = 5.dp.toPx()
                    drawRoundRect(accent, Offset(cx - pw / 2f, ty - ph / 2f), Size(pw, ph), CornerRadius(ph / 2f, ph / 2f))
                    drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(cx - pw / 2f + 2.dp.toPx(), ty - 0.5.dp.toPx()), Size(pw - 4.dp.toPx(), 1.dp.toPx()))
                }
                // End labels inside the capsule.
                Text("∞", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, modifier = Modifier.align(Alignment.TopCenter).offset(y = (-1).dp))
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)))

                // Distance tag riding beside the thumb.
                val padPx = with(density) { 14.dp.toPx() }
                val tagY = padPx + (railPx - 2 * padPx) * shownT
                val tagH = with(density) { 20.dp.toPx() }
                Text(
                    Stops.focusLabel(s.focusDiopters),
                    color = accent,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(-(36.dp.roundToPx()), (tagY - tagH / 2f).roundToInt()) }
                        .wrapContentWidth(Alignment.End, unbounded = true)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .border(1.dp, accent.copy(alpha = 0.35f + 0.4f * glow), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Progress ring while the finger is held down: fills over the last part of the 2 s hold. */
@Composable
fun HoldRing(point: Offset?, durationMs: Int) {
    if (point == null) return
    val p = remember(point) { Animatable(0f) }
    LaunchedEffect(point) { p.animateTo(1f, tween(durationMs, easing = LinearEasing)) }
    val accent = Pro.Amber
    Canvas(Modifier.fillMaxSize()) {
        val r = 30.dp.toPx()
        val tl = Offset(point.x - r, point.y - r)
        drawCircle(Color.Black.copy(alpha = 0.25f), radius = r + 3.dp.toPx(), center = point)
        drawCircle(Color.White.copy(alpha = 0.3f), radius = r, center = point, style = Stroke(1.5.dp.toPx()))
        drawArc(
            accent, -90f, 360f * p.value, false, tl, Size(2 * r, 2 * r),
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
