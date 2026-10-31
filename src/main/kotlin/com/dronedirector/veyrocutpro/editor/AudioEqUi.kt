package com.dronedirector.veyrocutpro.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.hintedScroll
import kotlinx.coroutines.delay
import kotlin.math.log10
import kotlin.math.pow

private fun fx(x: Float) = (20.0 * 1000.0.pow(x.toDouble())).toFloat()          // 0..1 → 20 Hz..20 kHz
private fun xf(f: Float) = (log10(f / 20.0) / 3.0).toFloat().coerceIn(0f, 1f)
private const val DBR = 18f

/** Parametric EQ: drag the numbered points (across = frequency, up / down = boost / cut) over the live spectrum. */
@Composable
fun ParametricEq(bands: List<EqBand>, playing: Boolean, onChange: (List<EqBand>?) -> Unit) {
    var sel by remember { mutableStateOf(0) }
    var spec by remember { mutableStateOf<FloatArray?>(null) }
    LaunchedEffect(playing) { while (playing) { spec = try { AudioSpectrum.bands(96) } catch (_: Throwable) { null }; delay(90) } }
    val cur by rememberUpdatedState(bands)
    val ch by rememberUpdatedState(onChange)
    Box(
        Modifier.fillMaxWidth().height(190.dp).padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val d = awaitFirstDown()
                    val list = cur
                    if (list.isEmpty()) return@awaitEachGesture
                    fun pos(b: EqBand) = Offset(xf(b.freq) * size.width, (0.5f - (if (b.type >= 3) 0f else b.gain) / (2 * DBR)) * size.height)
                    val i = list.indices.minByOrNull { (pos(list[it]) - d.position).getDistance() } ?: return@awaitEachGesture
                    sel = i
                    while (true) {
                        val ev = awaitPointerEvent(); val c = ev.changes.firstOrNull() ?: break
                        if (!c.pressed) break
                        val f = fx((c.position.x / size.width).coerceIn(0f, 1f))
                        val g = ((0.5f - c.position.y / size.height) * 2 * DBR).coerceIn(-DBR, DBR)
                        val nb = cur.toMutableList()
                        nb[i] = nb[i].copy(freq = f, gain = if (nb[i].type >= 3) 0f else (Math.round(g * 2) / 2f))
                        ch(nb); c.consume()
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val grid = com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.1f)
            for (f in listOf(50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f)) drawLine(grid, Offset(xf(f) * w, 0f), Offset(xf(f) * w, h))
            for (db in listOf(-12f, -6f, 0f, 6f, 12f)) drawLine(if (db == 0f) com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.25f) else grid, Offset(0f, (0.5f - db / (2 * DBR)) * h), Offset(w, (0.5f - db / (2 * DBR)) * h))
            spec?.let { s ->
                val p = Path(); p.moveTo(0f, h)
                s.forEachIndexed { i, v -> p.lineTo((i + 0.5f) / s.size * w, (1f - ((v + 60f) / 70f).coerceIn(0f, 1f)) * h) }
                p.lineTo(w, h); p.close()
                drawPath(p, Pro.Amber.copy(alpha = 0.18f))
            }
            val curve = Path()
            for (i in 0..120) {
                val x = i / 120f
                val y = (0.5f - EqMath.responseDb(bands, fx(x)).coerceIn(-DBR, DBR) / (2 * DBR)) * h
                if (i == 0) curve.moveTo(x * w, y) else curve.lineTo(x * w, y)
            }
            drawPath(curve, Color.White, style = Stroke(2.5f))
            bands.forEachIndexed { i, b ->
                val o = Offset(xf(b.freq) * w, (0.5f - (if (b.type >= 3) 0f else b.gain) / (2 * DBR)) * h)
                drawCircle(if (i == sel) Pro.Amber else Color.White.copy(alpha = if (b.on) 0.8f else 0.3f), if (i == sel) 9.dp.toPx() else 7.dp.toPx(), o)
            }
        }
    }
    val b = bands.getOrNull(sel)
    Row(Modifier.hintedScroll(horizontal = true), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        bands.forEachIndexed { i, x -> PillChip("${i + 1}", sel == i) { sel = i } }
        if (bands.size < 8) PillChip("+ BAND", false, accentText = true) { ch(bands + EqBand(freq = listOf(250f, 1000f, 3000f, 8000f, 120f, 500f, 6000f, 60f)[bands.size % 8])); sel = bands.size }
    }
    if (b != null) {
        fun upd(n: EqBand) = ch(bands.toMutableList().also { it[sel] = n })
        Chips(EqBand.TYPES, b.type) { upd(b.copy(type = it, gain = if (it >= 3) 0f else b.gain, q = if (it == 1 || it == 2) 0.8f else if (it >= 3) 0.707f else 1f)) }
        Text("%s · %s Hz%s".format(EqBand.TYPES[b.type], if (b.freq >= 1000) "%.1fk".format(b.freq / 1000) else "%.0f".format(b.freq), if (b.type < 3) " · %+.1f dB".format(b.gain) else ""), style = Pro.Label.copy(color = Color.White))
        EdSlider(if (b.type == 1 || b.type == 2) "Slope" else "Width (Q)", b.q, if (b.type == 1 || b.type == 2) 0.5f..1f else 0.3f..10f, 1f, { "%.2f".format(it) }) { upd(b.copy(q = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (b.on) "BYPASS" else "TURN ON", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp), modifier = Modifier.clickable { upd(b.copy(on = !b.on)) }.padding(8.dp))
            Text("REMOVE BAND", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp), modifier = Modifier.clickable { val n = bands.filterIndexed { k, _ -> k != sel }; sel = 0; ch(n.takeIf { it.isNotEmpty() }) }.padding(8.dp))
        }
    }
    Text("Drag a point: across sets the frequency, up / down boosts or cuts. The shaded area is the live sound while playing. Up to 8 bands, on top of the quick Low / Mid / High.", color = Pro.Dim, fontSize = 11.sp)
}
