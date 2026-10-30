package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Remove reflections (photos through glass): reflections are usually soft — weak, blurry edges — while the
 * scene behind the glass has the strong, sharp ones. The picture is rebuilt from its strong edges only
 * (a screened Poisson solve), which fades the soft reflected layer. Own code, after the published method
 * "fast single image reflection suppression via convex optimization" (gradient thresholding).
 */
object Reflections {
    /**
     * [src] full picture; [strength] 0..1 (how strong an edge must be to stay). Returns a new picture.
     * [progress] gets 0..100.
     */
    fun remove(src: Bitmap, strength: Float, progress: (Int) -> Unit = {}, stop: () -> Boolean = { false }): Bitmap? {
        val W = src.width; val H = src.height
        // Solve small (the reflection is soft, so the change is too), then add the change at full size.
        val k = min(1f, 640f / max(W, H))
        val w = max(8, (W * k).toInt()); val h = max(8, (H * k).toInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h); small.getPixels(px, 0, w, 0, 0, w, h); if (small !== src) small.recycle()
        val n = w * h
        val ch = Array(3) { c -> FloatArray(n) { i -> ((px[i] shr (16 - 8 * c)) and 255) / 255f } }
        // Edge strength over all three colours; edges weaker than the threshold are dropped.
        val thr = 0.012f + 0.09f * strength.coerceIn(0f, 1f)
        val keepX = BooleanArray(n); val keepY = BooleanArray(n)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            var gx = 0f; var gy = 0f
            for (c in 0 until 3) {
                if (x + 1 < w) { val d = ch[c][i + 1] - ch[c][i]; gx += d * d }
                if (y + 1 < h) { val d = ch[c][i + w] - ch[c][i]; gy += d * d }
            }
            keepX[i] = x + 1 < w && sqrt(gx) > thr
            keepY[i] = y + 1 < h && sqrt(gy) > thr
        }
        val eps = 0.0008f
        val out = Array(3) { FloatArray(n) }
        for (c in 0 until 3) {
            if (stop()) return null
            val I = ch[c]
            // Right-hand side: eps·I − div(kept edges).
            val b = FloatArray(n)
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                var div = 0f
                if (x + 1 < w && keepX[i]) div += I[i + 1] - I[i]
                if (x > 0 && keepX[i - 1]) div -= I[i] - I[i - 1]
                if (y + 1 < h && keepY[i]) div += I[i + w] - I[i]
                if (y > 0 && keepY[i - w]) div -= I[i] - I[i - w]
                b[i] = eps * I[i] - div
            }
            // (eps + L) T = b, L = graph Laplacian with free edges; conjugate gradients from T = I.
            fun apply(v: FloatArray, r: FloatArray) {
                for (y in 0 until h) for (x in 0 until w) {
                    val i = y * w + x
                    var s = eps * v[i]; val vi = v[i]
                    if (x + 1 < w) s += vi - v[i + 1]
                    if (x > 0) s += vi - v[i - 1]
                    if (y + 1 < h) s += vi - v[i + w]
                    if (y > 0) s += vi - v[i - w]
                    r[i] = s
                }
            }
            val T = I.copyOf(); val r = FloatArray(n); val p = FloatArray(n); val ap = FloatArray(n)
            apply(T, ap)
            for (i in 0 until n) { r[i] = b[i] - ap[i]; p[i] = r[i] }
            var rr = 0.0; for (i in 0 until n) rr += r[i].toDouble() * r[i]
            val iters = 220
            for (it in 0 until iters) {
                if (stop()) return null
                apply(p, ap)
                var pap = 0.0; for (i in 0 until n) pap += p[i].toDouble() * ap[i]
                if (pap <= 1e-20) break
                val a = (rr / pap).toFloat()
                for (i in 0 until n) { T[i] += a * p[i]; r[i] -= a * ap[i] }
                var rr2 = 0.0; for (i in 0 until n) rr2 += r[i].toDouble() * r[i]
                if (rr2 < 1e-10 * n) { rr = rr2; break }
                val beta = (rr2 / rr).toFloat(); rr = rr2
                for (i in 0 until n) p[i] = r[i] + beta * p[i]
                if (it % 20 == 0) progress(((c * iters + it) * 90) / (3 * iters))
            }
            // Keep the overall brightness of the original.
            var mI = 0.0; var mT = 0.0
            for (i in 0 until n) { mI += I[i]; mT += T[i] }
            val shift = ((mI - mT) / n).toFloat()
            for (i in 0 until n) out[c][i] = T[i] + shift - I[i]   // the change
        }
        progress(92)
        // The change, smoothly enlarged, added to the full-size picture.
        val diff = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val dp = IntArray(n) { i ->
            fun e(v: Float) = ((v * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (e(out[0][i]) shl 16) or (e(out[1][i]) shl 8) or e(out[2][i])
        }
        diff.setPixels(dp, 0, w, 0, 0, w, h)
        val big = Bitmap.createScaledBitmap(diff, W, H, true); diff.recycle()
        val res = src.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        val row = IntArray(W); val drow = IntArray(W)
        for (y in 0 until H) {
            res.getPixels(row, 0, W, 0, y, W, 1); big.getPixels(drow, 0, W, 0, y, W, 1)
            for (x in 0 until W) {
                val o = row[x]; val d = drow[x]
                fun ch(sh: Int): Int { val v = (o shr sh) and 255; val dd = (((d shr sh) and 255) - 127.5f) * 2f; return (v + dd).toInt().coerceIn(0, 255) }
                row[x] = (o and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
            res.setPixels(row, 0, W, 0, y, W, 1)
        }
        big.recycle()
        progress(100)
        return res
    }
}
