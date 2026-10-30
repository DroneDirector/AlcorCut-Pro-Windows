package android.graphics

import kotlin.math.cos
import kotlin.math.sin

/** 3×3 transform (affine or perspective), same conventions as on the phone. Own code. */
class Matrix() {
    /** Row-major: [sx kx tx; ky sy ty; p0 p1 p2]. */
    internal val v = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    constructor(src: Matrix?) : this() { if (src != null) src.v.copyInto(v) }

    enum class ScaleToFit { FILL, START, CENTER, END }

    companion object {
        const val MSCALE_X = 0; const val MSKEW_X = 1; const val MTRANS_X = 2
        const val MSKEW_Y = 3; const val MSCALE_Y = 4; const val MTRANS_Y = 5
        const val MPERSP_0 = 6; const val MPERSP_1 = 7; const val MPERSP_2 = 8

        private fun mul(a: FloatArray, b: FloatArray): FloatArray {
            val r = FloatArray(9)
            for (i in 0..2) for (j in 0..2) r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j]
            return r
        }
    }

    val isIdentity: Boolean get() = v.contentEquals(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))
    val isAffine: Boolean get() = v[6] == 0f && v[7] == 0f && v[8] == 1f

    fun reset() { floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f).copyInto(v) }
    fun set(m: Matrix?) { if (m == null) reset() else m.v.copyInto(v) }
    fun getValues(out: FloatArray) { v.copyInto(out) }
    fun setValues(src: FloatArray) { src.copyInto(v, 0, 0, 9) }

    private fun pre(m: FloatArray) { mul(v, m).copyInto(v) }
    private fun post(m: FloatArray) { mul(m, v).copyInto(v) }
    private fun t(dx: Float, dy: Float) = floatArrayOf(1f, 0f, dx, 0f, 1f, dy, 0f, 0f, 1f)
    private fun s(sx: Float, sy: Float, px: Float, py: Float) = floatArrayOf(sx, 0f, px - sx * px, 0f, sy, py - sy * py, 0f, 0f, 1f)
    private fun r(deg: Float, px: Float, py: Float): FloatArray {
        val a = Math.toRadians(deg.toDouble()); val c = cos(a).toFloat(); val sn = sin(a).toFloat()
        return floatArrayOf(c, -sn, px - c * px + sn * py, sn, c, py - sn * px - c * py, 0f, 0f, 1f)
    }
    private fun k(kx: Float, ky: Float, px: Float, py: Float) = floatArrayOf(1f, kx, -kx * py, ky, 1f, -ky * px, 0f, 0f, 1f)

    fun setTranslate(dx: Float, dy: Float) = t(dx, dy).copyInto(v)
    fun setScale(sx: Float, sy: Float) = s(sx, sy, 0f, 0f).copyInto(v)
    fun setScale(sx: Float, sy: Float, px: Float, py: Float) = s(sx, sy, px, py).copyInto(v)
    fun setRotate(deg: Float) = r(deg, 0f, 0f).copyInto(v)
    fun setRotate(deg: Float, px: Float, py: Float) = r(deg, px, py).copyInto(v)
    fun setSkew(kx: Float, ky: Float) = k(kx, ky, 0f, 0f).copyInto(v)
    fun setConcat(a: Matrix, b: Matrix): Boolean { mul(a.v, b.v).copyInto(v); return true }

    fun preTranslate(dx: Float, dy: Float): Boolean { pre(t(dx, dy)); return true }
    fun preScale(sx: Float, sy: Float): Boolean { pre(s(sx, sy, 0f, 0f)); return true }
    fun preScale(sx: Float, sy: Float, px: Float, py: Float): Boolean { pre(s(sx, sy, px, py)); return true }
    fun preRotate(deg: Float): Boolean { pre(r(deg, 0f, 0f)); return true }
    fun preRotate(deg: Float, px: Float, py: Float): Boolean { pre(r(deg, px, py)); return true }
    fun preSkew(kx: Float, ky: Float): Boolean { pre(k(kx, ky, 0f, 0f)); return true }
    fun preConcat(m: Matrix): Boolean { pre(m.v); return true }

    fun postTranslate(dx: Float, dy: Float): Boolean { post(t(dx, dy)); return true }
    fun postScale(sx: Float, sy: Float): Boolean { post(s(sx, sy, 0f, 0f)); return true }
    fun postScale(sx: Float, sy: Float, px: Float, py: Float): Boolean { post(s(sx, sy, px, py)); return true }
    fun postRotate(deg: Float): Boolean { post(r(deg, 0f, 0f)); return true }
    fun postRotate(deg: Float, px: Float, py: Float): Boolean { post(r(deg, px, py)); return true }
    fun postSkew(kx: Float, ky: Float): Boolean { post(k(kx, ky, 0f, 0f)); return true }
    fun postConcat(m: Matrix): Boolean { post(m.v); return true }

    fun invert(out: Matrix): Boolean {
        val a = v
        val c00 = a[4] * a[8] - a[5] * a[7]; val c01 = a[5] * a[6] - a[3] * a[8]; val c02 = a[3] * a[7] - a[4] * a[6]
        val det = a[0] * c00 + a[1] * c01 + a[2] * c02
        if (det == 0f) return false
        val id = 1f / det
        val r = floatArrayOf(
            c00 * id, (a[2] * a[7] - a[1] * a[8]) * id, (a[1] * a[5] - a[2] * a[4]) * id,
            c01 * id, (a[0] * a[8] - a[2] * a[6]) * id, (a[2] * a[3] - a[0] * a[5]) * id,
            c02 * id, (a[1] * a[6] - a[0] * a[7]) * id, (a[0] * a[4] - a[1] * a[3]) * id,
        )
        r.copyInto(out.v)
        return true
    }

    fun mapPoints(pts: FloatArray) = mapPoints(pts, 0, pts, 0, pts.size / 2)
    fun mapPoints(dst: FloatArray, src: FloatArray) = mapPoints(dst, 0, src, 0, src.size / 2)
    fun mapPoints(dst: FloatArray, dstIndex: Int, src: FloatArray, srcIndex: Int, count: Int) {
        for (i in 0 until count) {
            val x = src[srcIndex + i * 2]; val y = src[srcIndex + i * 2 + 1]
            val w = v[6] * x + v[7] * y + v[8]
            val iw = if (w == 0f) 1f else 1f / w
            dst[dstIndex + i * 2] = (v[0] * x + v[1] * y + v[2]) * iw
            dst[dstIndex + i * 2 + 1] = (v[3] * x + v[4] * y + v[5]) * iw
        }
    }

    fun mapVectors(pts: FloatArray) {
        for (i in 0 until pts.size / 2) {
            val x = pts[i * 2]; val y = pts[i * 2 + 1]
            pts[i * 2] = v[0] * x + v[1] * y; pts[i * 2 + 1] = v[3] * x + v[4] * y
        }
    }

    fun mapRadius(r: Float): Float {
        val p = floatArrayOf(r, 0f, 0f, r); mapVectors(p)
        return kotlin.math.sqrt(kotlin.math.hypot(p[0], p[1]) * kotlin.math.hypot(p[2], p[3]))
    }

    fun mapRect(r: RectF): Boolean {
        val p = floatArrayOf(r.left, r.top, r.right, r.top, r.right, r.bottom, r.left, r.bottom)
        mapPoints(p)
        r.set(minOf(p[0], p[2], p[4], p[6]), minOf(p[1], p[3], p[5], p[7]), maxOf(p[0], p[2], p[4], p[6]), maxOf(p[1], p[3], p[5], p[7]))
        return isAffine && v[1] == 0f && v[3] == 0f
    }

    fun mapRect(dst: RectF, src: RectF): Boolean { dst.set(src); return mapRect(dst) }

    fun setRectToRect(src: RectF, dst: RectF, stf: ScaleToFit): Boolean {
        if (src.width() == 0f || src.height() == 0f) { reset(); return false }
        var sx = dst.width() / src.width(); var sy = dst.height() / src.height()
        var tx = dst.left - src.left * sx; var ty = dst.top - src.top * sy
        if (stf != ScaleToFit.FILL) {
            val sc = minOf(sx, sy)
            val dx = dst.width() - src.width() * sc; val dy = dst.height() - src.height() * sc
            val f = when (stf) { ScaleToFit.START -> 0f; ScaleToFit.CENTER -> 0.5f; else -> 1f }
            sx = sc; sy = sc
            tx = dst.left - src.left * sc + dx * f; ty = dst.top - src.top * sc + dy * f
        }
        floatArrayOf(sx, 0f, tx, 0f, sy, ty, 0f, 0f, 1f).copyInto(v)
        return true
    }

    /** Maps [count] source points onto destination points (1–4; 4 gives a perspective transform). */
    fun setPolyToPoly(src: FloatArray, srcIndex: Int, dst: FloatArray, dstIndex: Int, count: Int): Boolean {
        when (count) {
            0 -> { reset(); return true }
            1 -> { setTranslate(dst[dstIndex] - src[srcIndex], dst[dstIndex + 1] - src[srcIndex + 1]); return true }
        }
        if (count < 4) {
            // Affine fit through 2 or 3 points (2: similarity).
            val s = FloatArray(6); val d = FloatArray(6)
            for (i in 0 until count * 2) { s[i] = src[srcIndex + i]; d[i] = dst[dstIndex + i] }
            if (count == 2) {
                // Third point: rotate the second 90° around the first.
                s[4] = s[0] - (s[3] - s[1]); s[5] = s[1] + (s[2] - s[0])
                d[4] = d[0] - (d[3] - d[1]); d[5] = d[1] + (d[2] - d[0])
            }
            val a = solve(arrayOf(
                doubleArrayOf(s[0].toDouble(), s[1].toDouble(), 1.0), doubleArrayOf(s[2].toDouble(), s[3].toDouble(), 1.0), doubleArrayOf(s[4].toDouble(), s[5].toDouble(), 1.0),
            ), doubleArrayOf(d[0].toDouble(), d[2].toDouble(), d[4].toDouble())) ?: return false
            val b = solve(arrayOf(
                doubleArrayOf(s[0].toDouble(), s[1].toDouble(), 1.0), doubleArrayOf(s[2].toDouble(), s[3].toDouble(), 1.0), doubleArrayOf(s[4].toDouble(), s[5].toDouble(), 1.0),
            ), doubleArrayOf(d[1].toDouble(), d[3].toDouble(), d[5].toDouble())) ?: return false
            floatArrayOf(a[0].toFloat(), a[1].toFloat(), a[2].toFloat(), b[0].toFloat(), b[1].toFloat(), b[2].toFloat(), 0f, 0f, 1f).copyInto(v)
            return true
        }
        val m = Array(8) { DoubleArray(8) }; val rhs = DoubleArray(8)
        for (i in 0 until 4) {
            val x = src[srcIndex + i * 2].toDouble(); val y = src[srcIndex + i * 2 + 1].toDouble()
            val u = dst[dstIndex + i * 2].toDouble(); val w = dst[dstIndex + i * 2 + 1].toDouble()
            m[i * 2] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y); rhs[i * 2] = u
            m[i * 2 + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -w * x, -w * y); rhs[i * 2 + 1] = w
        }
        val h = solve(m, rhs) ?: return false
        for (i in 0 until 8) v[i] = h[i].toFloat()
        v[8] = 1f
        return true
    }

    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        val m = Array(n) { i -> a[i].copyOf(n + 1).also { it[n] = b[i] } }
        for (c in 0 until n) {
            var p = c
            for (r in c + 1 until n) if (kotlin.math.abs(m[r][c]) > kotlin.math.abs(m[p][c])) p = r
            if (kotlin.math.abs(m[p][c]) < 1e-12) return null
            val tmp = m[c]; m[c] = m[p]; m[p] = tmp
            for (r in 0 until n) if (r != c) {
                val f = m[r][c] / m[c][c]
                for (k in c..n) m[r][k] -= f * m[c][k]
            }
        }
        return DoubleArray(n) { m[it][n] / m[it][it] }
    }

    override fun equals(other: Any?) = other is Matrix && other.v.contentEquals(v)
    override fun hashCode() = v.contentHashCode()
}

class RectF(@JvmField var left: Float = 0f, @JvmField var top: Float = 0f, @JvmField var right: Float = 0f, @JvmField var bottom: Float = 0f) {
    constructor(r: RectF) : this(r.left, r.top, r.right, r.bottom)
    constructor(r: Rect) : this(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
    fun width() = right - left
    fun height() = bottom - top
    fun centerX() = (left + right) / 2f
    fun centerY() = (top + bottom) / 2f
    fun set(l: Float, t: Float, r: Float, b: Float) { left = l; top = t; right = r; bottom = b }
    fun set(o: RectF) = set(o.left, o.top, o.right, o.bottom)
    val isEmpty: Boolean get() = left >= right || top >= bottom
    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
    fun offset(dx: Float, dy: Float) { left += dx; right += dx; top += dy; bottom += dy }
    fun inset(dx: Float, dy: Float) { left += dx; right -= dx; top += dy; bottom -= dy }
    fun round(out: Rect) = out.set(Math.round(left), Math.round(top), Math.round(right), Math.round(bottom))
}

class Rect(@JvmField var left: Int = 0, @JvmField var top: Int = 0, @JvmField var right: Int = 0, @JvmField var bottom: Int = 0) {
    constructor(r: Rect) : this(r.left, r.top, r.right, r.bottom)
    fun width() = right - left
    fun height() = bottom - top
    fun centerX() = (left + right) / 2
    fun centerY() = (top + bottom) / 2
    fun exactCenterX() = (left + right) / 2f
    fun exactCenterY() = (top + bottom) / 2f
    fun set(l: Int, t: Int, r: Int, b: Int) { left = l; top = t; right = r; bottom = b }
    val isEmpty: Boolean get() = left >= right || top >= bottom
    fun contains(x: Int, y: Int) = x >= left && x < right && y >= top && y < bottom
}

class PointF(@JvmField var x: Float = 0f, @JvmField var y: Float = 0f) {
    fun set(x: Float, y: Float) { this.x = x; this.y = y }
}
