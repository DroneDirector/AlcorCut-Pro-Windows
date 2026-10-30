package com.dronedirector.veyrocutpro.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dronedirector.veyrocutpro.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The app logo with a glowing, sparkling ring in the app colour (Settings → App colour): a light
 * runs round the ring, sparkles orbit and twinkle, and a soft glow breathes behind it — the same
 * style as the download bar and the scroll wheels.
 */
@Composable
fun GlowLogo(size: Dp, modifier: Modifier = Modifier) {
    val accent = Pro.Amber
    val bright = lerp(accent, Color.White, 0.55f)
    val t = rememberInfiniteTransition(label = "logo")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val breath by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "breath")
    // The shutter: open, a quick close (the photo), a short hold, then it opens again — every 2.6 s.
    val open by t.animateFloat(1f, 1f, infiniteRepeatable(keyframes {
        durationMillis = 2600
        1f at 0; 1f at 1900; 0f at 2040 using FastOutLinearInEasing; 0f at 2120; 1f at 2520 using LinearOutSlowInEasing
    }), label = "shutter")
    // A flash on the ring as the shutter fires.
    val flash = ((1f - open) * 1.4f).coerceIn(0f, 1f)
    // The ring and sparkles reach a little beyond the logo.
    val outer = size * 1.42f
    Box(modifier.size(outer), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val r = size.toPx() / 2f + 2.dp.toPx()
            // Soft glow behind everything.
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = (0.34f * breath + 0.3f * flash).coerceAtMost(1f)), accent.copy(alpha = 0.10f * breath), Color.Transparent), c, r * 1.42f), r * 1.42f, c)
            // Base ring (brightens with the shutter flash).
            drawCircle(lerp(accent.copy(alpha = 0.45f), Color.White.copy(alpha = 0.9f), flash * 0.7f), r, c, style = Stroke((2.5f + 1.5f * flash).dp.toPx()))
            // Running light: a bright arc sweeping round the ring.
            rotate(spin, c) {
                drawCircle(
                    Brush.sweepGradient(listOf(Color.Transparent, accent.copy(alpha = 0.2f), bright, Color.White, Color.Transparent, Color.Transparent), c),
                    r, c, style = Stroke(4.dp.toPx()),
                )
                // Wider halo along the light.
                drawCircle(
                    Brush.sweepGradient(listOf(Color.Transparent, Color.Transparent, accent.copy(alpha = 0.35f), Color.Transparent, Color.Transparent), c),
                    r, c, style = Stroke(12.dp.toPx()),
                )
            }
            // Ticks that shimmer as the light passes (like the wheels' ridges).
            for (i in 0 until 48) {
                val a = i / 48f * 2f * PI.toFloat()
                val lit = ((1f - (((spin / 360f) - i / 48f + 1f) % 1f)) * 4f - 3f).coerceIn(0f, 1f)
                val p1 = c + Offset(cos(a), sin(a)) * (r + 5.dp.toPx())
                val p2 = c + Offset(cos(a), sin(a)) * (r + 8.dp.toPx())
                drawLine(lerp(accent.copy(alpha = 0.18f), Color.White.copy(alpha = 0.85f), lit), p1, p2, 1.5.dp.toPx())
            }
            // Orbiting sparkles with little tails.
            for (k in 0 until 7) {
                val f = (phase + k / 7f) % 1f
                val ang = (f * 2f * PI.toFloat()) * (if (k % 2 == 0) 1f else -0.7f) + k
                val rad = r * (1.12f + 0.12f * sin(f * 6.28f * 2 + k))
                val p = c + Offset(cos(ang), sin(ang)) * rad
                val tw = (sin(f * 6.28f * 3 + k * 1.7f) + 1f) / 2f
                drawCircle(accent.copy(alpha = 0.35f * tw), (2.5f + 4f * tw).dp.toPx(), p)
                drawCircle(Color.White.copy(alpha = 0.6f + 0.4f * tw), (0.8f + 1.4f * tw).dp.toPx(), p)
                // Four-point star on the brightest moments.
                if (tw > 0.75f) {
                    val s = (tw - 0.75f) * 4f * 7.dp.toPx()
                    drawLine(Color.White.copy(alpha = 0.8f), p - Offset(s, 0f), p + Offset(s, 0f), 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.8f), p - Offset(0f, s), p + Offset(0f, s), 1.dp.toPx())
                }
            }
        }
        // The logo itself.
        Box(Modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.45f; scaleY = 1.45f })
            // Animated aperture drawn over the logo's own blades (same place and colours).
            Canvas(Modifier.fillMaxSize()) { drawIris(this, open) }
        }
    }
}

private val BLADE_LIGHT = listOf(0xFFFFD456, 0xFFFFC53A, 0xFFF8AE12, 0xFFEC9C00, 0xFFF6AA10, 0xFFFFC02E).map { Color(it) }
private val BLADE_DARK = listOf(0xFFC27800, 0xFFB06A00, 0xFF9C5C00, 0xFF8A4F00, 0xFF9C5C00, 0xFFB06A00).map { Color(it) }

/**
 * Six iris blades in the logo's coordinates (108-unit icon, drawn at 1.45×): the blades always fill
 * the disc between the rim and a hexagonal opening; [open] 1 = the logo's normal opening, 0 = shut.
 */
private fun drawIris(ds: androidx.compose.ui.graphics.drawscope.DrawScope, open: Float) = with(ds) {
    val k = size.minDimension * 1.45f / 108f
    val c = Offset(size.width / 2f, size.height / 2f)
    fun p(ang: Float, rad: Float) = c + Offset(cos(ang), sin(ang)) * (rad * k)
    val deg = (PI / 180.0).toFloat()
    // The dark disc behind the blades (covers the static ones).
    drawCircle(Brush.radialGradient(listOf(Color(0xFF26323F), Color(0xFF0B0F15), Color(0xFF030405)), c + Offset(-8f * k, -10f * k), 30f * k), 25f * k, c)
    val outerR = 23.4f
    val h = 0.5f + 7.1f * open             // hexagon opening
    val swirl = (1f - open) * 38f * deg    // blades swing in as they close
    for (i in 0 until 6) {
        val a1 = (-112f + i * 60f) * deg; val a2 = a1 + 60f * deg
        val q2 = a1 + 90f * deg + swirl; val q1 = a1 + 30f * deg + swirl
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(p(a1, outerR).x, p(a1, outerR).y)
            arcTo(androidx.compose.ui.geometry.Rect(c, outerR * k), a1 / deg, 60f, false)
            lineTo(p(q2, h).x, p(q2, h).y)
            lineTo(p(q1, h).x, p(q1, h).y)
            close()
        }
        val mid = a1 + 30f * deg
        drawPath(path, Brush.linearGradient(listOf(BLADE_LIGHT[i], BLADE_DARK[i]), p(mid, outerR), p(mid + 20f * deg, h)))
        drawPath(path, Color(0xE0140C00), style = Stroke(0.7f * k, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
    // Soft light in the opening and a sheen across the blades.
    if (open > 0.15f) drawCircle(Brush.radialGradient(listOf(Color(0x66FFB300), Color(0x00FFB300)), c, 5.2f * k * open), 5.2f * k * open, c)
    drawCircle(Color(0xAAFFE7A0), 1.2f * k * open.coerceAtLeast(0.2f), c)
    drawArc(Color.White.copy(alpha = 0.22f), 200f, 70f, false, c - Offset(19f * k, 19f * k), androidx.compose.ui.geometry.Size(38f * k, 38f * k), style = Stroke(1.6f * k))
}
