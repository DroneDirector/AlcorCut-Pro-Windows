package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Upright: finds the straighten angle and perspective that make the photo's lines level and
 * upright. Modes: 0 Auto (balanced), 1 Level (rotation only), 2 Vertical (rotation + verticals),
 * 3 Full (rotation + vertical + horizontal perspective).
 */
object Upright {
    val MODES = listOf("Auto", "Level", "Vertical", "Full")

    /** Returns the new geometry (crop, aspect and flips kept). Slow-ish (≈ 1 s): off the main thread. */
    fun solve(src: Bitmap, g: Geometry, mode: Int): Geometry {
        val k = 256f / max(src.width, src.height)
        val small = Bitmap.createScaledBitmap(src, (src.width * k).toInt().coerceAtLeast(16), (src.height * k).toInt().coerceAtLeast(16), true)
        val base = g.copy(straighten = 0f, vertical = 0f, horizontal = 0f, zoom = 1f, offsetX = 0f, offsetY = 0f, aspect = 0f)
        fun score(s: Float, v: Float, h: Float): Float {
            val img = ImageOps.geometry(small, base.copy(straighten = s, vertical = v, horizontal = h))
            val r = alignment(img, wantVertical = mode != 1, wantHorizontal = true)
            if (img !== small) img.recycle()
            return r
        }
        // Rotation first (coarse to fine).
        var bs = 0f; var best = score(0f, 0f, 0f)
        var step = 2f
        var range = 10f
        while (step >= 0.25f) {
            var a = bs - range
            while (a <= bs + range + 1e-3f) { val sc = score(a, 0f, 0f); if (sc > best) { best = sc; bs = a }; a += step }
            range = step; step /= 2f
        }
        var bv = 0f; var bh = 0f
        if (mode == 0 || mode == 2 || mode == 3) {
            // Vertical perspective (keystone).
            var vb = score(bs, 0f, 0f)
            var vs = 0.25f; var vr = 1f
            while (vs >= 0.03f) {
                var v = bv - vr
                while (v <= bv + vr + 1e-3f) { val sc = score(bs, v.coerceIn(-1f, 1f), 0f); if (sc > vb) { vb = sc; bv = v.coerceIn(-1f, 1f) }; v += vs }
                vr = vs; vs /= 2f
            }
            // Auto: only part of the correction, so it still looks natural.
            if (mode == 0) bv *= 0.7f
        }
        if (mode == 3) {
            var hb = score(bs, bv, 0f)
            var hs = 0.25f; var hr = 1f
            while (hs >= 0.03f) {
                var h = bh - hr
                while (h <= bh + hr + 1e-3f) { val sc = score(bs, bv, h.coerceIn(-1f, 1f)); if (sc > hb) { hb = sc; bh = h.coerceIn(-1f, 1f) }; h += hs }
                hr = hs; hs /= 2f
            }
        }
        small.recycle()
        return g.copy(straighten = bs, vertical = bv, horizontal = bh)
    }

    /** How much strong edge energy runs exactly vertical / horizontal. */
    private fun alignment(b: Bitmap, wantVertical: Boolean, wantHorizontal: Boolean): Float {
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        val l = FloatArray(w * h) { i -> val p = px[i]; if ((p ushr 24) < 200) -1f else (((p shr 16) and 255) * 0.3f + ((p shr 8) and 255) * 0.59f + (p and 255) * 0.11f) }
        var s = 0f
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            if (l[i] < 0f || l[i - 1] < 0f || l[i + 1] < 0f || l[i - w] < 0f || l[i + w] < 0f) continue
            val gx = l[i + 1] - l[i - 1]; val gy = l[i + w] - l[i - w]
            val m = sqrt(gx * gx + gy * gy)
            if (m < 18f) continue
            val a = Math.toDegrees(atan2(gy, gx).toDouble()).toFloat()
            val mod = ((a % 90f) + 90f) % 90f
            val d = min(mod, 90f - mod)
            if (d > 2.5f) continue
            // Gradient across x = a vertical edge.
            val vertEdge = abs(gx) > abs(gy)
            if ((vertEdge && wantVertical) || (!vertEdge && wantHorizontal)) s += m * (1f - d / 2.5f)
        }
        return s
    }

    // ---------------------------------------------------------------- dust spots

    /** High-pass view that makes sensor dust stand out (like "Visualise spots"). [k] 0..1 = sensitivity. */
    fun visualise(src: Bitmap, k: Float): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val l = FloatArray(w * h) { i -> val p = px[i]; ((p shr 16) and 255) * 0.3f + ((p shr 8) and 255) * 0.59f + (p and 255) * 0.11f }
        val bl = boxF(l, w, h, max(2, max(w, h) / 200))
        val th = 2f + (1f - k) * 18f
        for (i in px.indices) { val v = if (abs(l[i] - bl[i]) > th) 255 else 0; px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Finds small dark round blobs in smooth areas (dust on the sensor) and returns heal spots for them. */
    fun findDust(src: Bitmap, k: Float, maxSpots: Int = 40): List<Spot> {
        val sc = 1200f / max(src.width, src.height)
        val b = if (sc < 1f) Bitmap.createScaledBitmap(src, (src.width * sc).toInt(), (src.height * sc).toInt(), true) else src
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        if (b !== src) b.recycle()
        val l = FloatArray(w * h) { i -> val p = px[i]; ((p shr 16) and 255) * 0.3f + ((p shr 8) and 255) * 0.59f + (p and 255) * 0.11f }
        val r = max(3, max(w, h) / 120)
        val mean = boxF(l, w, h, r)
        // Local roughness: dust shows on smooth areas (sky, walls), not in texture.
        val sq = FloatArray(w * h) { l[it] * l[it] }
        val meanSq = boxF(sq, w, h, r)
        val th = 4f + (1f - k) * 10f
        val cand = BooleanArray(w * h)
        for (i in l.indices) {
            val varc = (meanSq[i] - mean[i] * mean[i]).coerceAtLeast(0f)
            if (mean[i] > 60f && mean[i] - l[i] > th && sqrt(varc) < 14f + th) cand[i] = true
        }
        // Connected blobs.
        val seen = BooleanArray(w * h)
        val out = ArrayList<Spot>()
        val maxArea = (r * r * 2)
        val stack = IntArray(w * h)
        for (s0 in cand.indices) {
            if (!cand[s0] || seen[s0]) continue
            var sp = 0; stack[sp++] = s0; seen[s0] = true
            var n = 0; var sx = 0f; var sy = 0f; var minX = w; var maxX = 0; var minY = h; var maxY = 0
            while (sp > 0) {
                val i = stack[--sp]; val x = i % w; val y = i / w
                n++; sx += x; sy += y; minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
                for ((dx, dy) in arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val xx = x + dx; val yy = y + dy
                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
                    val j = yy * w + xx
                    if (cand[j] && !seen[j]) { seen[j] = true; if (sp < stack.size) stack[sp++] = j }
                }
            }
            val bw = maxX - minX + 1; val bh = maxY - minY + 1
            // Round and small.
            if (n < 3 || n > maxArea || bw > 3 * bh || bh > 3 * bw) continue
            val rad = (max(bw, bh) * 0.9f + 2f) / w
            val cx = sx / n / w; val cy = sy / n / h
            // Take the patch from beside it, on the side with the most room.
            val dx = if (cx > 0.5f) -rad * 2.2f else rad * 2.2f
            out += Spot(cx, cy, rad, dx, 0f)
            if (out.size >= maxSpots) break
        }
        return out
    }

    private fun boxF(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(w * h); val out = FloatArray(w * h); val div = 2f * r + 1f
        for (y in 0 until h) {
            var s = 0f; val row = y * w
            for (k in -r..r) s += src[row + k.coerceIn(0, w - 1)]
            for (x in 0 until w) { tmp[row + x] = s / div; s += src[row + (x + r + 1).coerceIn(0, w - 1)] - src[row + (x - r).coerceIn(0, w - 1)] }
        }
        for (x in 0 until w) {
            var s = 0f
            for (k in -r..r) s += tmp[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) { out[y * w + x] = s / div; s += tmp[(y + r + 1).coerceIn(0, h - 1) * w + x] - tmp[(y - r).coerceIn(0, h - 1) * w + x] }
        }
        return out
    }
}
