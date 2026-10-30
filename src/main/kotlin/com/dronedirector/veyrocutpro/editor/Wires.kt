package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Find wires and cables (power lines, overhead cables, thin poles): thin, long, dark lines. Found with a
 * line filter at two thicknesses; only long, thin pieces are kept. Returns a selection (white = wire) the
 * size of [src], or null when none are found. Own code.
 */
object Wires {
    fun find(src: Bitmap): Bitmap? {
        val k = min(1f, 1400f / max(src.width, src.height))
        val w = max(16, (src.width * k).toInt()); val h = max(16, (src.height * k).toInt())
        val s = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h); s.getPixels(px, 0, w, 0, 0, w, h); if (s !== src) s.recycle()
        val n = w * h
        val Y = FloatArray(n) { i -> val c = px[i]; (((c shr 16) and 255) * 0.299f + ((c shr 8) and 255) * 0.587f + (c and 255) * 0.114f) / 255f }
        fun blur(src: FloatArray, sigma: Float): FloatArray {
            val r = (sigma * 3).toInt().coerceAtLeast(1)
            val kk = FloatArray(2 * r + 1) { exp(-((it - r) * (it - r)) / (2 * sigma * sigma)) }
            val sum = kk.sum(); for (i in kk.indices) kk[i] /= sum
            val t = FloatArray(n); val o = FloatArray(n)
            for (y in 0 until h) for (x in 0 until w) { var a = 0f; for (j in -r..r) a += kk[j + r] * src[y * w + (x + j).coerceIn(0, w - 1)]; t[y * w + x] = a }
            for (y in 0 until h) for (x in 0 until w) { var a = 0f; for (j in -r..r) a += kk[j + r] * t[(y + j).coerceIn(0, h - 1) * w + x]; o[y * w + x] = a }
            return o
        }
        // Dark-line strength: the bigger curvature across the line (scale-normalised), at two thicknesses.
        val R = FloatArray(n)
        for (sigma in floatArrayOf(1.2f, 2.2f)) {
            val g = blur(Y, sigma)
            for (y in 1 until h - 1) for (x in 1 until w - 1) {
                val i = y * w + x
                val dxx = g[i - 1] - 2 * g[i] + g[i + 1]
                val dyy = g[i - w] - 2 * g[i] + g[i + w]
                val dxy = (g[i - w - 1] + g[i + w + 1] - g[i - w + 1] - g[i + w - 1]) / 4f
                val l1 = ((dxx + dyy) + sqrt((dxx - dyy) * (dxx - dyy) + 4 * dxy * dxy)) / 2f
                val l2 = ((dxx + dyy) - sqrt((dxx - dyy) * (dxx - dyy) + 4 * dxy * dxy)) / 2f
                // A line: strongly curved across (l1 > 0, darker than both sides), hardly along (l2 ≈ 0).
                val v = if (l1 > 0f && kotlin.math.abs(l2) < l1 * 0.5f) l1 * sigma * sigma else 0f
                if (v > R[i]) R[i] = v
            }
        }
        // Threshold: well above the typical line response in the picture.
        val pos = R.filter { it > 0f }.sorted()
        if (pos.size < 50) return null
        val thr = max(0.012f, pos[(pos.size * 0.985).toInt().coerceAtMost(pos.size - 1)])
        val on = BooleanArray(n) { R[it] > thr }
        // Long thin pieces only.
        val keep = BooleanArray(n); val seen = BooleanArray(n)
        val stack = IntArray(n); val comp = IntArray(n)
        val minLen = max(w, h) * 0.07f
        var found = 0
        for (s0 in 0 until n) {
            if (!on[s0] || seen[s0]) continue
            var sp = 0; var cn = 0; stack[sp++] = s0; seen[s0] = true
            var x0 = w; var y0 = h; var x1 = 0; var y1 = 0
            while (sp > 0) {
                val i = stack[--sp]; comp[cn++] = i
                val x = i % w; val y = i / w
                x0 = min(x0, x); y0 = min(y0, y); x1 = max(x1, x); y1 = max(y1, y)
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx; val yy = y + dy
                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
                    val j = yy * w + xx
                    if (on[j] && !seen[j]) { seen[j] = true; stack[sp++] = j }
                }
            }
            val len = sqrt(((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0)).toFloat())
            if (len >= minLen && cn <= len * 7f) { for (q in 0 until cn) keep[comp[q]] = true; found++ }
        }
        if (found == 0) return null
        // A little wider, so the fill covers the whole cable.
        val grow = BooleanArray(n)
        for (y in 0 until h) for (x in 0 until w) if (keep[y * w + x]) for (dy in -2..2) for (dx in -2..2) {
            val xx = x + dx; val yy = y + dy
            if (xx in 0 until w && yy in 0 until h) grow[yy * w + xx] = true
        }
        val m = Bitmap.createBitmap(IntArray(n) { if (grow[it]) -1 else 0xFF000000.toInt() }, w, h, Bitmap.Config.ARGB_8888)
        val out = Bitmap.createScaledBitmap(m, src.width, src.height, true); if (out !== m) m.recycle()
        return out
    }
}
