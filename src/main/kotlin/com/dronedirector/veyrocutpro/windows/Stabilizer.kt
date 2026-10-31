package com.dronedirector.veyrocutpro.camera

import kotlin.math.abs

/**
 * Digital stabilization on the PC: how the picture moved since the last one is measured (brightness
 * profiles across and down), the shake is taken out and the smooth camera movement kept; the picture
 * is cut in by [margin] to have room to move. Used for video (Stabilization: IN-APP) and the
 * hyperlapse. Own code.
 */
class Stabilizer(private val margin: Float = 0.1f, private val smoothing: Float = 0.92f) {
    private var prevCols: FloatArray? = null
    private var prevRows: FloatArray? = null
    private var pathX = 0f; private var pathY = 0f
    private var smoothX = 0f; private var smoothY = 0f
    private var work = IntArray(0)

    fun reset() { prevCols = null; prevRows = null; pathX = 0f; pathY = 0f; smoothX = 0f; smoothY = 0f }

    /** Steadies [px] ([w]×[h], in place). */
    fun apply(px: IntArray, w: Int, h: Int) {
        val step = maxOf(2, w / 320)
        val cw = w / step; val ch = h / step
        val cols = FloatArray(cw); val rows = FloatArray(ch)
        for (yi in 0 until ch) {
            val row = yi * step * w
            for (xi in 0 until cw) {
                val c = px[row + xi * step]
                val l = (((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8
                cols[xi] += l.toFloat(); rows[yi] += l.toFloat()
            }
        }
        val pc = prevCols; val pr = prevRows
        prevCols = cols; prevRows = rows
        if (pc != null && pr != null && pc.size == cw && pr.size == ch) {
            pathX += shift(pc, cols) * step
            pathY += shift(pr, rows) * step
        }
        smoothX = smoothX * smoothing + pathX * (1 - smoothing)
        smoothY = smoothY * smoothing + pathY * (1 - smoothing)
        val maxDx = w * margin / 2; val maxDy = h * margin / 2
        val dx = (pathX - smoothX).coerceIn(-maxDx, maxDx)
        val dy = (pathY - smoothY).coerceIn(-maxDy, maxDy)
        // Keep the path within reach of the margin.
        if (abs(pathX - smoothX) > maxDx) smoothX = pathX - dx
        if (abs(pathY - smoothY) > maxDy) smoothY = pathY - dy
        if (work.size != w * h) work = IntArray(w * h)
        System.arraycopy(px, 0, work, 0, w * h)
        val k = 1f - margin
        val x0 = w * margin / 2 + dx; val y0 = h * margin / 2 + dy
        parallel(w * h) { a, b ->
            for (i in a until b) {
                val y = i / w; val x = i % w
                val sx = (x0 + x * k).coerceIn(0f, w - 1.001f); val sy = (y0 + y * k).coerceIn(0f, h - 1.001f)
                val ix = sx.toInt(); val iy = sy.toInt(); val fx = sx - ix; val fy = sy - iy
                val p00 = work[iy * w + ix]; val p10 = work[iy * w + ix + 1]; val p01 = work[(iy + 1) * w + ix]; val p11 = work[(iy + 1) * w + ix + 1]
                fun ch(sh: Int): Int {
                    val t0 = ((p00 shr sh) and 0xFF) * (1 - fx) + ((p10 shr sh) and 0xFF) * fx
                    val t1 = ((p01 shr sh) and 0xFF) * (1 - fx) + ((p11 shr sh) and 0xFF) * fx
                    return (t0 * (1 - fy) + t1 * fy + 0.5f).toInt()
                }
                px[i] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
        }
    }

    /** How far the profile moved (positive: the picture moved toward lower x). */
    private fun shift(prev: FloatArray, cur: FloatArray): Float {
        val n = prev.size; val max = n / 8
        var best = 0; var bestErr = Float.MAX_VALUE
        for (s in -max..max) {
            var err = 0f; var cnt = 0
            for (x in 1 until n) {
                val p = x + s
                if (p < 1 || p >= n) continue
                err += abs((prev[p] - prev[p - 1]) - (cur[x] - cur[x - 1])); cnt++
            }
            if (cnt < n / 2) continue
            err /= cnt
            if (err < bestErr) { bestErr = err; best = s }
        }
        return -best.toFloat()
    }
}
