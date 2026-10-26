package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Colour chart calibration: photograph the classic 24-patch colour chart (4 rows × 6 columns, from
 * the brown "dark skin" patch to black), mark its first and last patch, and the app works out a
 * colour matrix that makes this camera / light show the chart's true colours. Saved with the edit
 * and in your presets, so it can be used for every photo taken in that light.
 */
object ColorChart {
    /** The chart's published colours (sRGB, D65), patch 1 (dark skin) to 24 (black). */
    private val REF = intArrayOf(
        115, 82, 68, 194, 150, 130, 98, 122, 157, 87, 108, 67, 133, 128, 177, 103, 189, 170,
        214, 126, 44, 80, 91, 166, 193, 90, 99, 94, 60, 108, 157, 188, 64, 224, 163, 46,
        56, 61, 150, 70, 148, 73, 175, 54, 60, 231, 199, 31, 187, 86, 149, 8, 133, 161,
        243, 243, 242, 200, 200, 200, 160, 160, 160, 122, 122, 121, 85, 85, 85, 52, 52, 52,
    )

    private fun lin(v: Float): Float = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)

    data class Result(val matrix: List<Float>, val errBefore: Float, val errAfter: Float)

    /**
     * Measures the chart in [b]: [x1], [y1] = centre of the first patch (dark skin), [x2], [y2] =
     * centre of the last (black), all 0..1 of the picture. Works for a tilted chart too.
     */
    fun solve(b: Bitmap, x1: Float, y1: Float, x2: Float, y2: Float): Result? {
        val w = b.width; val h = b.height
        val ax = x1 * w; val ay = y1 * h; val dx = x2 * w - ax; val dy = y2 * h - ay
        val d = sqrt(dx * dx + dy * dy); if (d < 20f) return null
        // The diagonal runs 5 patches across and 3 down: work out the column and row directions.
        val ang = atan2(dy, dx) - atan2(3f, 5f)
        val pitch = d / sqrt(34f)
        val ux = cos(ang) * pitch; val uy = sin(ang) * pitch
        val vx = -uy; val vy = ux
        val rad = (pitch * 0.22f).toInt().coerceAtLeast(1)
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        val meas = FloatArray(72)
        for (r in 0 until 4) for (c in 0 until 6) {
            val cx = (ax + ux * c + vx * r).toInt(); val cy = (ay + uy * c + vy * r).toInt()
            var sr = 0.0; var sg = 0.0; var sb = 0.0; var n = 0
            for (yy in cy - rad..cy + rad) for (xx in cx - rad..cx + rad) {
                if (xx !in 0 until w || yy !in 0 until h) continue
                val p = px[yy * w + xx]
                sr += lin(((p shr 16) and 255) / 255f); sg += lin(((p shr 8) and 255) / 255f); sb += lin((p and 255) / 255f); n++
            }
            if (n == 0) return null
            val i = (r * 6 + c) * 3
            meas[i] = (sr / n).toFloat(); meas[i + 1] = (sg / n).toFloat(); meas[i + 2] = (sb / n).toFloat()
        }
        // The white patch must be brighter than the black one, or the marks were the wrong way round.
        fun l(k: Int) = meas[k * 3] + meas[k * 3 + 1] + meas[k * 3 + 2]
        if (l(18) < l(23)) return null
        val ref = FloatArray(72) { lin(REF[it] / 255f) }
        // Least squares: M = (Rᵀ X)(Xᵀ X)⁻¹, the neutral patches counted twice (they matter most).
        val xtx = DoubleArray(9); val rtx = DoubleArray(9)
        for (k in 0 until 24) {
            val wgt = if (k >= 18) 2.0 else 1.0
            for (i in 0 until 3) for (j in 0 until 3) {
                xtx[i * 3 + j] += wgt * meas[k * 3 + i] * meas[k * 3 + j]
                rtx[i * 3 + j] += wgt * ref[k * 3 + i] * meas[k * 3 + j]
            }
        }
        val inv = invert(xtx) ?: return null
        val m = FloatArray(9)
        for (i in 0 until 3) for (j in 0 until 3) { var s = 0.0; for (k in 0 until 3) s += rtx[i * 3 + k] * inv[k * 3 + j]; m[i * 3 + j] = s.toFloat() }
        fun err(apply: Boolean): Float {
            var e = 0.0
            for (k in 0 until 24) {
                val r0 = meas[k * 3]; val g0 = meas[k * 3 + 1]; val b0 = meas[k * 3 + 2]
                val o = if (apply) floatArrayOf(m[0] * r0 + m[1] * g0 + m[2] * b0, m[3] * r0 + m[4] * g0 + m[5] * b0, m[6] * r0 + m[7] * g0 + m[8] * b0) else floatArrayOf(r0, g0, b0)
                for (ch in 0 until 3) {
                    val a = gam(o[ch].coerceIn(0f, 1f)) * 255; val t = REF[k * 3 + ch].toFloat()
                    e += ((a - t) * (a - t)).toDouble()
                }
            }
            return sqrt(e / 72).toFloat()
        }
        return Result(m.toList(), err(false), err(true))
    }

    private fun gam(v: Float): Float = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1 / 2.4f) - 0.055f

    private fun invert(a: DoubleArray): DoubleArray? {
        val det = a[0] * (a[4] * a[8] - a[5] * a[7]) - a[1] * (a[3] * a[8] - a[5] * a[6]) + a[2] * (a[3] * a[7] - a[4] * a[6])
        if (kotlin.math.abs(det) < 1e-12) return null
        val k = 1.0 / det
        return doubleArrayOf(
            (a[4] * a[8] - a[5] * a[7]) * k, (a[2] * a[7] - a[1] * a[8]) * k, (a[1] * a[5] - a[2] * a[4]) * k,
            (a[5] * a[6] - a[3] * a[8]) * k, (a[0] * a[8] - a[2] * a[6]) * k, (a[2] * a[3] - a[0] * a[5]) * k,
            (a[3] * a[7] - a[4] * a[6]) * k, (a[1] * a[6] - a[0] * a[7]) * k, (a[0] * a[4] - a[1] * a[3]) * k,
        )
    }
}
