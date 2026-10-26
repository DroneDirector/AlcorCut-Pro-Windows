package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Photo editor → Retouch: liquify, frequency separation and AI relight. Own code.
 *
 * Liquify strokes are stored in coordinates of the uncropped photo (0..1, like heal spots) and are
 * applied before crop / rotation. Frequency separation and relight work on the finished picture.
 */
data class LiqStroke(
    /** 0 push, 1 pinch, 2 bloat, 3 twirl clockwise, 4 twirl anticlockwise, 5 restore. */
    val tool: Int,
    /** x, y pairs, 0..1 of the uncropped photo. */
    val pts: List<Float>,
    /** Brush radius, share of the photo's long side. */
    val r: Float,
    val strength: Float,
)

data class Retouch(
    val liquify: List<LiqStroke> = emptyList(),
    /** Frequency separation: smoothing of the tone layer (0..1), texture kept (0..2, 1 = as is). */
    val fsSmooth: Float = 0f,
    val fsTexture: Float = 1f,
    /** Size of the detail that counts as texture, share of the long side. */
    val fsRadius: Float = 0.004f,
    /** 0 whole photo, else a People mask (face / body skin) made for it. */
    val fsArea: Int = 0,
    val fsMaskFile: String? = null,
    /** Relight from the AI depth map. */
    val light: Float = 0f,
    val shadow: Float = 0f,
    /** Light position: −1..1 across / down from the centre; height above the photo. */
    val lightX: Float = -0.5f,
    val lightY: Float = -0.6f,
    val warmth: Float = 0f,
    val softness: Float = 0.5f,
    val depthFile: String? = null,
) {
    val fsOn get() = fsSmooth > 0f || fsTexture != 1f
    val relightOn get() = depthFile != null && (light > 0f || shadow > 0f)
    val isIdentity get() = liquify.isEmpty() && !fsOn && !relightOn
    fun files() = listOfNotNull(if (fsOn && fsArea != 0) fsMaskFile else null, if (relightOn) depthFile else null)
}

object RetouchFx {
    val TOOLS = listOf("Push", "Pinch", "Bloat", "Twirl ↻", "Twirl ↺", "Restore")

    // ---------------- Liquify

    /** The warp as a grid of source positions (u, v pairs, 0..1); gw × gh points. */
    private class Field(val gw: Int, val gh: Int) {
        val f = FloatArray(gw * gh * 2).also { a -> for (y in 0 until gh) for (x in 0 until gw) { val i = (y * gw + x) * 2; a[i] = x / (gw - 1f); a[i + 1] = y / (gh - 1f) } }
        fun sample(u: Float, v: Float, out: FloatArray) {
            val fx = (u * (gw - 1)).coerceIn(0f, gw - 1f); val fy = (v * (gh - 1)).coerceIn(0f, gh - 1f)
            val x0 = min(fx.toInt(), gw - 2); val y0 = min(fy.toInt(), gh - 2); val ax = fx - x0; val ay = fy - y0
            for (k in 0..1) {
                val a = f[(y0 * gw + x0) * 2 + k]; val b = f[(y0 * gw + x0 + 1) * 2 + k]
                val c = f[((y0 + 1) * gw + x0) * 2 + k]; val d = f[((y0 + 1) * gw + x0 + 1) * 2 + k]
                out[k] = (a * (1 - ax) + b * ax) * (1 - ay) + (c * (1 - ax) + d * ax) * ay
            }
        }
    }

    private fun field(strokes: List<LiqStroke>, w: Int, h: Int): Field {
        val long = max(w, h).toFloat()
        val gw = (192f * w / long).roundToInt().coerceAtLeast(8); val gh = (192f * h / long).roundToInt().coerceAtLeast(8)
        val fd = Field(gw, gh)
        // Aspect: distances measured in "long side" units.
        val sx = w / long; val sy = h / long
        val tmp = FloatArray(2)
        for (s in strokes) {
            val n = s.pts.size / 2
            if (n == 0) continue
            // Dabs every quarter radius along the stroke.
            val dabs = ArrayList<FloatArray>()
            for (i in 0 until n) {
                val x = s.pts[i * 2]; val y = s.pts[i * 2 + 1]
                if (i == 0) { dabs += floatArrayOf(x, y, 0f, 0f); continue }
                val px = s.pts[i * 2 - 2]; val py = s.pts[i * 2 - 1]
                val dist = hypot((x - px) * sx, (y - py) * sy)
                val steps = max(1, (dist / (s.r * 0.25f)).toInt())
                for (k in 1..steps) {
                    val t = k / steps.toFloat()
                    dabs += floatArrayOf(px + (x - px) * t, py + (y - py) * t, (x - px) / steps, (y - py) / steps)
                }
            }
            for (d in dabs) {
                val cx = d[0]; val cy = d[1]
                val r = s.r
                val x0 = (((cx * sx - r) / sx) * (gw - 1)).toInt().coerceIn(0, gw - 1); val x1 = (((cx * sx + r) / sx) * (gw - 1)).toInt().coerceIn(0, gw - 1)
                val y0 = (((cy * sy - r) / sy) * (gh - 1)).toInt().coerceIn(0, gh - 1); val y1 = (((cy * sy + r) / sy) * (gh - 1)).toInt().coerceIn(0, gh - 1)
                val old = fd.f.copyOf()
                val src = Field(gw, gh); System.arraycopy(old, 0, src.f, 0, old.size)
                for (gy in y0..y1) for (gx in x0..x1) {
                    val u = gx / (gw - 1f); val v = gy / (gh - 1f)
                    val dx = (u - cx) * sx; val dy = (v - cy) * sy
                    val dd = hypot(dx, dy) / r
                    if (dd >= 1f) continue
                    val wgt = (1 - dd * dd).let { it * it } * s.strength
                    var su = u; var sv = v
                    when (s.tool) {
                        0 -> { su = u - d[2] * wgt * 1.6f; sv = v - d[3] * wgt * 1.6f }
                        1, 2 -> { val k = (if (s.tool == 2) -1f else 1f) * wgt * 0.06f; su = u + (u - cx) * k; sv = v + (v - cy) * k }
                        3, 4 -> {
                            val a = (if (s.tool == 3) -1f else 1f) * wgt * 0.08f
                            val rx = dx * cos(a) - dy * sin(a); val ry = dx * sin(a) + dy * cos(a)
                            su = cx + rx / sx; sv = cy + ry / sy
                        }
                    }
                    val i = (gy * gw + gx) * 2
                    if (s.tool == 5) {
                        val k = min(1f, wgt * 0.25f)
                        fd.f[i] = old[i] * (1 - k) + u * k; fd.f[i + 1] = old[i + 1] * (1 - k) + v * k
                    } else {
                        src.sample(su, sv, tmp)
                        fd.f[i] = tmp[0]; fd.f[i + 1] = tmp[1]
                    }
                }
            }
        }
        return fd
    }

    fun liquify(src: Bitmap, strokes: List<LiqStroke>): Bitmap {
        if (strokes.isEmpty()) return src
        val w = src.width; val h = src.height
        val fd = field(strokes, w, h)
        val inp = IntArray(w * h); src.getPixels(inp, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        val t = FloatArray(2)
        for (y in 0 until h) for (x in 0 until w) {
            fd.sample((x + 0.5f) / w, (y + 0.5f) / h, t)
            val fx = (t[0] * w - 0.5f).coerceIn(0f, w - 1f); val fy = (t[1] * h - 0.5f).coerceIn(0f, h - 1f)
            val x0 = min(fx.toInt(), w - 2).coerceAtLeast(0); val y0 = min(fy.toInt(), h - 2).coerceAtLeast(0)
            val ax = fx - x0; val ay = fy - y0
            val a = inp[y0 * w + x0]; val b = inp[y0 * w + min(x0 + 1, w - 1)]; val c = inp[min(y0 + 1, h - 1) * w + x0]; val d = inp[min(y0 + 1, h - 1) * w + min(x0 + 1, w - 1)]
            var o = 0
            for (sh in intArrayOf(24, 16, 8, 0)) {
                val v = (((a ushr sh) and 255) * (1 - ax) + ((b ushr sh) and 255) * ax) * (1 - ay) + (((c ushr sh) and 255) * (1 - ax) + ((d ushr sh) and 255) * ax) * ay
                o = o or ((v + 0.5f).toInt().coerceIn(0, 255) shl sh)
            }
            out[y * w + x] = o
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    // ---------------- Frequency separation

    /** Separable box blur ×3 (≈ gaussian) of one float channel. */
    private fun blur(a: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        if (r < 1) return a.copyOf()
        var cur = a.copyOf(); val t = FloatArray(a.size)
        repeat(3) {
            for (y in 0 until h) {
                var acc = 0f; val o = y * w
                for (x in -r..r) acc += cur[o + x.coerceIn(0, w - 1)]
                for (x in 0 until w) { t[o + x] = acc / (2 * r + 1); acc += cur[o + min(x + r + 1, w - 1)] - cur[o + max(x - r, 0)] }
            }
            for (x in 0 until w) {
                var acc = 0f
                for (y in -r..r) acc += t[y.coerceIn(0, h - 1) * w + x]
                for (y in 0 until h) { cur[y * w + x] = acc / (2 * r + 1); acc += t[min(y + r + 1, h - 1) * w + x] - t[max(y - r, 0) * w + x] }
            }
        }
        return cur
    }

    fun frequencySeparation(px: IntArray, w: Int, h: Int, rt: Retouch, mask: FloatArray?) {
        val r = max(1, (rt.fsRadius * max(w, h) / 1.7f).roundToInt())
        for (sh in intArrayOf(16, 8, 0)) {
            val ch = FloatArray(w * h) { ((px[it] shr sh) and 255).toFloat() }
            val low = blur(ch, w, h, r)
            val lowSmooth = if (rt.fsSmooth > 0f) blur(low, w, h, r * 3) else low
            for (i in ch.indices) {
                val m = mask?.get(i) ?: 1f
                if (m <= 0f) continue
                val high = ch[i] - low[i]
                val tone = low[i] + (lowSmooth[i] - low[i]) * rt.fsSmooth
                val v = tone + high * rt.fsTexture
                ch[i] = ch[i] + (v - ch[i]) * m
            }
            for (i in px.indices) px[i] = (px[i] and (255 shl sh).inv()) or (ch[i].roundToInt().coerceIn(0, 255) shl sh)
        }
    }

    // ---------------- Relight

    /** Light from a chosen direction falling on the shapes the depth map describes. */
    fun relight(px: IntArray, w: Int, h: Int, rt: Retouch, depth: Bitmap) {
        val d = Bitmap.createScaledBitmap(depth, w, h, true)
        val dp = IntArray(w * h); d.getPixels(dp, 0, w, 0, 0, w, h); if (d !== depth) d.recycle()
        var z = FloatArray(w * h) { (dp[it] shr 8 and 255) / 255f }
        val r = max(1, (max(w, h) * (0.002f + rt.softness * 0.012f)).roundToInt())
        z = blur(z, w, h, r)
        // Light direction (towards the light), from the pad: x right, y down, always somewhat in front.
        val lx = rt.lightX; val ly = rt.lightY; val lz = 0.6f
        val ln = sqrt(lx * lx + ly * ly + lz * lz)
        val k = max(w, h) * 0.9f   // depth relief
        val warmR = 1f + rt.warmth * 0.25f; val warmB = 1f - rt.warmth * 0.25f
        // Distance falloff across the frame towards the light.
        val cxL = 0.5f + lx * 0.6f; val cyL = 0.5f + ly * 0.6f
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val gx = (z[y * w + min(x + 1, w - 1)] - z[y * w + max(x - 1, 0)]) * 0.5f * k / max(w, h) * 40f
            val gy = (z[min(y + 1, h - 1) * w + x] - z[max(y - 1, 0) * w + x]) * 0.5f * k / max(w, h) * 40f
            val nx = -gx; val ny = -gy; val nz = 1f
            val nn = sqrt(nx * nx + ny * ny + nz * nz)
            var lam = ((nx * lx + ny * ly + nz * lz) / (nn * ln)).coerceIn(0f, 1f)
            val fall = exp(-(hypot(x.toFloat() / w - cxL, y.toFloat() / h - cyL)) * 1.4f)
            lam = lam * (0.55f + 0.45f * z[i]) * (0.6f + 0.4f * fall)
            val gain = 1f - rt.shadow * 0.6f * (1f - lam) + rt.light * 0.9f * lam
            val c = px[i]
            val rr = ((c shr 16) and 255) * gain * (1f + (warmR - 1f) * lam * rt.light)
            val gg = ((c shr 8) and 255) * gain
            val bb = (c and 255) * gain * (1f + (warmB - 1f) * lam * rt.light)
            px[i] = (c and (0xFF shl 24)) or (rr.roundToInt().coerceIn(0, 255) shl 16) or (gg.roundToInt().coerceIn(0, 255) shl 8) or bb.roundToInt().coerceIn(0, 255)
        }
    }

    @Suppress("unused") private fun ang(x: Float, y: Float) = atan2(y, x)
}
