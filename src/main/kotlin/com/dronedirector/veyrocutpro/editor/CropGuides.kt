package com.dronedirector.veyrocutpro.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Photo editor → Crop → GUIDES: composition overlays drawn over the photo while cropping (never saved). */
object CropGuides {
    val MODES = listOf("Off", "Thirds", "Golden ratio", "Golden spiral", "Golden triangles", "Diagonals", "Grid")
    var mode by mutableIntStateOf(1)
    /** Spiral / triangles orientation, 0..3 (flips). */
    var turn by mutableIntStateOf(0)
    val turnable get() = mode == 3 || mode == 4
}

private const val PHI = 1.618034f

@Composable
fun CropGuideOverlay(imgW: Int, imgH: Int, modifier: Modifier = Modifier) {
    val mode = CropGuides.mode
    if (mode == 0 || imgW <= 0 || imgH <= 0) return
    val turn = CropGuides.turn
    Canvas(modifier.fillMaxSize()) {
        // The photo is drawn "fit" in this box.
        val k = min(size.width / imgW, size.height / imgH)
        val w = imgW * k; val h = imgH * k
        val x0 = (size.width - w) / 2; val y0 = (size.height - h) / 2
        val fx = turn and 1 != 0; val fy = turn and 2 != 0
        fun p(u: Float, v: Float) = Offset(x0 + (if (fx) 1 - u else u) * w, y0 + (if (fy) 1 - v else v) * h)
        val line = Color.White.copy(alpha = 0.75f)
        val sw = 1.2f * density
        fun ln(a: Offset, b: Offset) { drawLine(Color.Black.copy(alpha = 0.35f), a, b, sw * 2.2f); drawLine(line, a, b, sw) }
        when (mode) {
            1, 2, 6 -> {
                val ts = when (mode) { 1 -> listOf(1f / 3, 2f / 3); 2 -> listOf(1 - 1 / PHI, 1 / PHI); else -> (1..5).map { it / 6f } }
                for (t in ts) { ln(p(t, 0f), p(t, 1f)); ln(p(0f, t), p(1f, t)) }
            }
            3 -> spiral(::p, line, sw)
            4 -> {
                // Diagonal plus the two perpendiculars from the other corners.
                val a = Offset(x0, y0 + h); val b = Offset(x0 + w, y0)
                fun foot(c: Offset): Offset {
                    val dx = b.x - a.x; val dy = b.y - a.y
                    val t = ((c.x - a.x) * dx + (c.y - a.y) * dy) / (dx * dx + dy * dy)
                    return Offset(a.x + dx * t, a.y + dy * t)
                }
                fun m(o: Offset) = Offset(if (fx) 2 * x0 + w - o.x else o.x, if (fy) 2 * y0 + h - o.y else o.y)
                ln(m(a), m(b))
                val c1 = Offset(x0, y0); val c2 = Offset(x0 + w, y0 + h)
                ln(m(c1), m(foot(c1))); ln(m(c2), m(foot(c2)))
            }
            5 -> {
                ln(p(0f, 0f), p(1f, 1f)); ln(p(1f, 0f), p(0f, 1f))
                val s = min(w, h)
                val su = s / w; val sv = s / h
                ln(p(0f, 0f), p(su, sv)); ln(p(1f, 0f), p(1 - su, sv)); ln(p(0f, 1f), p(su, 1 - sv)); ln(p(1f, 1f), p(1 - su, 1 - sv))
            }
        }
    }
}

/** Golden spiral: quarter arcs through ever smaller golden rectangles, with the rectangles' dividing lines. */
private fun DrawScope.spiral(p: (Float, Float) -> Offset, color: Color, sw: Float) {
    var x = 0f; var y = 0f; var w = 1f; var h = 1f
    val path = Path()
    var first = true
    for (i in 0 until 10) {
        val s: Pair<Float, Float>; val e: Pair<Float, Float>; val c: Pair<Float, Float>
        when (i % 4) {
            0 -> { val a = w / PHI; s = x to y + h; e = x + a to y; c = x + a to y + h; drawLine(color.copy(alpha = 0.35f), p(x + a, y), p(x + a, y + h), sw); x += a; w -= a }
            1 -> { val b = h / PHI; s = x to y; e = x + w to y + b; c = x to y + b; drawLine(color.copy(alpha = 0.35f), p(x, y + b), p(x + w, y + b), sw); y += b; h -= b }
            2 -> { val a = w / PHI; s = x + w to y; e = x + w - a to y + h; c = x + w - a to y; drawLine(color.copy(alpha = 0.35f), p(x + w - a, y), p(x + w - a, y + h), sw); w -= a }
            else -> { val b = h / PHI; s = x + w to y + h; e = x to y + h - b; c = x + w to y + h - b; drawLine(color.copy(alpha = 0.35f), p(x, y + h - b), p(x + w, y + h - b), sw); h -= b }
        }
        for (j in 0..16) {
            val t = j / 16f * (Math.PI.toFloat() / 2)
            val u = c.first + (s.first - c.first) * cos(t) + (e.first - c.first) * sin(t)
            val v = c.second + (s.second - c.second) * cos(t) + (e.second - c.second) * sin(t)
            val o = p(u, v)
            if (first) { path.moveTo(o.x, o.y); first = false } else path.lineTo(o.x, o.y)
        }
    }
    drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke(sw * 2.2f))
    drawPath(path, color, style = Stroke(sw))
}
