package com.dronedirector.veyrocutpro.ui

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Waveform, RGB parade and vectorscope, measured from the viewfinder picture a few times a second. */
object ScopeMath {
    /** [mode] 1 waveform, 2 parade, 3 vectorscope. Returns a W×H image. */
    fun render(src: Bitmap, mode: Int): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        return when (mode) {
            3 -> vector(px)
            else -> wave(px, w, h, mode == 2)
        }
    }

    private fun wave(px: IntArray, w: Int, h: Int, parade: Boolean): Bitmap {
        val outW = if (parade) w * 3 else w
        val outH = 100
        val acc = Array(if (parade) 3 else 1) { IntArray(w * outH) }
        for (y in 0 until h) for (x in 0 until w) {
            val c = px[y * w + x]
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            if (parade) {
                acc[0][x * outH + r * (outH - 1) / 255]++
                acc[1][x * outH + g * (outH - 1) / 255]++
                acc[2][x * outH + b * (outH - 1) / 255]++
            } else {
                val l = (r * 54 + g * 183 + b * 19) shr 8
                acc[0][x * outH + l * (outH - 1) / 255]++
            }
        }
        val out = IntArray(outW * outH)
        val k = 255f * 6f / h
        for (ch in acc.indices) for (x in 0 until w) for (v in 0 until outH) {
            val n = acc[ch][x * outH + v]
            if (n == 0) continue
            val a = min(255f, 40f + n * k).toInt()
            val col = if (!parade) (a shl 24) or 0x00E6FFE6 else when (ch) { 0 -> (a shl 24) or 0x00FF5050; 1 -> (a shl 24) or 0x0050FF70; else -> (a shl 24) or 0x005A8CFF }
            val ox = if (parade) ch * w + x else x
            out[(outH - 1 - v) * outW + ox] = col
        }
        return Bitmap.createBitmap(out, outW, outH, Bitmap.Config.ARGB_8888)
    }

    private fun vector(px: IntArray): Bitmap {
        val s = 120
        val acc = IntArray(s * s)
        for (c in px) {
            val r = ((c shr 16) and 0xFF) / 255f; val g = ((c shr 8) and 0xFF) / 255f; val b = (c and 0xFF) / 255f
            val cb = -0.1687f * r - 0.3313f * g + 0.5f * b
            val cr = 0.5f * r - 0.4187f * g - 0.0813f * b
            val x = ((cb + 0.5f) * (s - 1)).toInt().coerceIn(0, s - 1)
            val y = ((0.5f - cr) * (s - 1)).toInt().coerceIn(0, s - 1)
            acc[y * s + x]++
        }
        val out = IntArray(s * s)
        val k = 255f * 60f / px.size.coerceAtLeast(1)
        for (i in acc.indices) {
            val n = acc[i]
            if (n == 0) continue
            val a = min(255f, 60f + n * k * 40f).toInt()
            val x = i % s; val y = i / s
            // Tint each dot with its own hue so the scope reads like a colour wheel.
            val cb = x / (s - 1f) - 0.5f; val cr = 0.5f - y / (s - 1f)
            val r = (0.6f + 1.4f * cr).coerceIn(0f, 1f); val b = (0.6f + 1.7f * cb).coerceIn(0f, 1f); val g = (0.6f - 0.34f * cb - 0.71f * cr).coerceIn(0f, 1f)
            out[i] = (a shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
        }
        return Bitmap.createBitmap(out, s, s, Bitmap.Config.ARGB_8888)
    }
}

/** A live scope box, fed from the viewfinder. */
@Composable
fun LiveScope(previewView: PreviewView, mode: Int, width: Dp, height: Dp) {
    var img by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(mode) {
        while (true) {
            val snap = try { previewView.bitmap } catch (_: Throwable) { null }
            if (snap != null) {
                img = withContext(Dispatchers.Default) {
                    val small = Bitmap.createScaledBitmap(snap, 128, (128f * snap.height / snap.width).toInt().coerceIn(32, 256), true)
                    ScopeMath.render(small, mode)
                }
            }
            delay(140)
        }
    }
    val boxW = if (mode == 3) height else width
    Box(
        Modifier.size(boxW, height).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (mode == 3) {
                val cx = size.width / 2f; val cy = size.height / 2f; val r = min(cx, cy) * 0.95f
                drawCircle(Color.White.copy(alpha = 0.2f), r, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                drawCircle(Color.White.copy(alpha = 0.12f), r * 0.5f, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                // Skin-tone line (about 123° on a vectorscope).
                val a = Math.toRadians(-123.0)
                drawLine(Color(0xFFFFB37A).copy(alpha = 0.7f), Offset(cx, cy), Offset(cx + (cos(a) * r).toFloat(), cy + (sin(a) * r).toFloat()), 1.5f)
            } else {
                for (f in listOf(0.1f, 0.5f, 0.9f)) drawLine(Color.White.copy(alpha = 0.15f), Offset(0f, size.height * (1 - f)), Offset(size.width, size.height * (1 - f)), 1f)
            }
        }
        img?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
        Text(listOf("", "WAVEFORM", "PARADE", "VECTOR")[mode.coerceIn(0, 3)], style = Pro.Label.copy(fontSize = 7.sp, color = Pro.Dim), modifier = Modifier.align(Alignment.TopStart).padding(3.dp))
    }
}

/** Focus magnifier: the middle of the viewfinder, enlarged, while focusing by hand. */
@Composable
fun FocusMagnifier(previewView: PreviewView, focusPoint: Pair<Float, Float>?, modifier: Modifier = Modifier) {
    var img by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(focusPoint) {
        while (true) {
            val snap = try { previewView.bitmap } catch (_: Throwable) { null }
            if (snap != null) {
                img = withContext(Dispatchers.Default) {
                    // The tap-to-focus point is in view pixels.
                    val fx = focusPoint?.let { it.first / previewView.width.coerceAtLeast(1) } ?: 0.5f
                    val fy = focusPoint?.let { it.second / previewView.height.coerceAtLeast(1) } ?: 0.5f
                    val cw = (snap.width / 4).coerceAtLeast(16); val ch = (cw * 0.66f).toInt().coerceAtLeast(16)
                    val x = (fx * snap.width - cw / 2).toInt().coerceIn(0, snap.width - cw)
                    val y = (fy * snap.height - ch / 2).toInt().coerceIn(0, snap.height - ch)
                    Bitmap.createBitmap(snap, x, y, cw, ch)
                }
            }
            delay(80)
        }
    }
    Column(modifier.clip(RoundedCornerShape(8.dp)).border(2.dp, Pro.Amber, RoundedCornerShape(8.dp)).background(Color.Black)) {
        Box(Modifier.size(220.dp, 146.dp)) {
            img?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Text("FOCUS ×4", style = Pro.Label.copy(color = Pro.Amber, fontSize = 8.sp), modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
        }
    }
}

@Suppress("unused")
private fun hypot(a: Float, b: Float) = sqrt(a * a + b * b) + atan2(0f, 1f)
