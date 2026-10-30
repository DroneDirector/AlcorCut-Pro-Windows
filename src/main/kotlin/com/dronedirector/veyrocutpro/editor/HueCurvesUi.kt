package com.dronedirector.veyrocutpro.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import kotlin.math.abs

/**
 * Hue curves editor (photo and video): four curves — hue vs hue, hue vs saturation, hue vs brightness and
 * brightness vs saturation. Drag a point up or down; double-tap a point to put it back. Own code.
 */
@Composable
fun HueCurvesEditor(curves: HueCurves?, onChange: (HueCurves?) -> Unit) {
    val hc = curves ?: HueCurves()
    var tab by remember { mutableIntStateOf(0) }
    val names = listOf("Hue vs Hue", "Hue vs Sat", "Hue vs Bright", "Bright vs Sat")
    val pts = when (tab) { 1 -> hc.hueSat; 2 -> hc.hueLum; 3 -> hc.lumSat; else -> hc.hueHue }
    fun set(p: List<Float>) {
        val n = when (tab) { 1 -> hc.copy(hueSat = p); 2 -> hc.copy(hueLum = p); 3 -> hc.copy(lumSat = p); else -> hc.copy(hueHue = p) }
        onChange(if (n.isZero) null else n)
    }
    val curPts by rememberUpdatedState(pts)
    val curSet by rememberUpdatedState(::set)
    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            names.forEachIndexed { i, n ->
                Text(n, color = if (tab == i) Color.Black else Color.White, fontSize = 11.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (tab == i) Pro.Amber else Pro.Fg.copy(alpha = 0.08f))
                        .clickable { tab = i }.padding(horizontal = 9.dp, vertical = 5.dp))
            }
        }
        val hueBased = tab != 3
        Canvas(
            Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF101318))
                .pointerInput(tab) {
                    fun xOf(i: Int) = if (hueBased) size.width * (i + 0.5f) / 8f else size.width * i / 7f
                    fun nearest(x: Float) = (0 until 8).minByOrNull { abs(xOf(it) - x) } ?: 0
                    detectTapGestures(onDoubleTap = { o -> val i = nearest(o.x); curSet(curPts.toMutableList().also { it[i] = 0f }) })
                }
                .pointerInput(tab) {
                    fun xOf(i: Int) = if (hueBased) size.width * (i + 0.5f) / 8f else size.width * i / 7f
                    var idx = 0
                    detectDragGestures(onDragStart = { o -> idx = (0 until 8).minByOrNull { abs(xOf(it) - o.x) } ?: 0 }) { ch, _ ->
                        ch.consume()
                        val v = (1f - 2f * (ch.position.y / size.height)).coerceIn(-1f, 1f)
                        curSet(curPts.toMutableList().also { it[idx] = if (abs(v) < 0.04f) 0f else v })
                    }
                },
        ) {
            val w = size.width; val h = size.height
            // Band of what the curve works on: the colour wheel, or black → white.
            val band = if (hueBased) Brush.horizontalGradient(
                *(listOf(0f to Color.hsv(337.5f, 0.8f, 0.9f)) + (0 until 8).map { k -> (k + 0.5f) / 8f to Color.hsv(k * 45f, 0.8f, 0.9f) } + listOf(1f to Color.hsv(337.5f, 0.8f, 0.9f))).toTypedArray(),
            )
            else Brush.horizontalGradient(listOf(Color.Black, Color.White))
            drawRect(band, Offset(0f, h - 10.dp.toPx()), Size(w, 10.dp.toPx()))
            drawLine(Pro.Fg.copy(alpha = 0.18f), Offset(0f, h / 2), Offset(w, h / 2), 1.dp.toPx())
            // The curve, sampled finely.
            val path = Path()
            val steps = 96
            for (k in 0..steps) {
                val fx = k / steps.toFloat()
                val v = if (hueBased) HueCurves.atHue(pts, fx * 360f - 22.5f) else HueCurves.atLum(pts, fx)
                val y = h / 2 - v * (h / 2 - 8.dp.toPx())
                if (k == 0) path.moveTo(fx * w, y) else path.lineTo(fx * w, y)
            }
            drawPath(path, Pro.Amber, style = Stroke(2.dp.toPx()))
            for (i in 0 until 8) {
                val x = if (hueBased) w * (i + 0.5f) / 8f else w * i / 7f
                val y = h / 2 - pts[i] * (h / 2 - 8.dp.toPx())
                val dot = if (hueBased) Color.hsv(i * 45f, 0.85f, 1f) else Color(i / 7f, i / 7f, i / 7f)
                drawCircle(Color.Black, 7.dp.toPx(), Offset(x, y))
                drawCircle(dot, 5.5f.dp.toPx(), Offset(x, y))
                if (pts[i] != 0f) drawCircle(Color.White, 7.dp.toPx(), Offset(x, y), style = Stroke(1.5f.dp.toPx()))
            }
        }
        Text(
            when (tab) {
                1 -> "Drag a colour up for more saturation, down for less (e.g. only the greens)."
                2 -> "Drag a colour up to brighten it, down to darken it (e.g. darker blue sky)."
                3 -> "Saturation by brightness: e.g. less colour in the shadows or the highlights."
                else -> "Drag a colour up or down to turn it into a neighbouring colour (e.g. orange leaves → red)."
            } + " Double-tap a point to reset it.",
            color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
        )
    }
}
