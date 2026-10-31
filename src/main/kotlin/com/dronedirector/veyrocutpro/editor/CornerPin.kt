package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Corner pin: a layer's four corners at [p] (x0,y0 top-left, x1,y1 top-right, x2,y2 bottom-right,
 * x3,y3 bottom-left; 0..1 of the frame, y down) at timeline time [t]. One entry = fixed; many =
 * a planar track (the corners follow a flat surface — a screen, a sign, a wall).
 */
data class PinQuad(val t: Long, val p: List<Float>)

/** The corners at timeline [ms] (between tracked entries: in a straight line), or null. */
fun Layer.pinAt(ms: Long): FloatArray? {
    val q = pin ?: return null
    if (q.isEmpty()) return null
    if (q.size == 1 || ms <= q.first().t) return q.first().p.toFloatArray()
    if (ms >= q.last().t) return q.last().p.toFloatArray()
    var lo = 0; var hi = q.lastIndex
    while (hi - lo > 1) { val m = (lo + hi) / 2; if (q[m].t <= ms) lo = m else hi = m }
    val a = q[lo]; val b = q[hi]
    val u = ((ms - a.t).toFloat() / (b.t - a.t).coerceAtLeast(1)).coerceIn(0f, 1f)
    return FloatArray(8) { a.p[it] + (b.p[it] - a.p[it]) * u }
}

/** A starting rectangle round where the layer is now (the user then drags the corners). */
fun Layer.defaultPin(frameAspect: Float, atMs: Long): List<PinQuad> {
    val a = animAt(atMs)
    val hh = (a.size.coerceAtLeast(0.15f)) / 2f
    val ar = if (kind == LayerKind.VIDEO) mediaAspect else 1.6f
    val hw = (hh * ar / frameAspect).coerceAtMost(0.45f)
    val x = a.x.coerceIn(hw, 1f - hw); val y = a.y.coerceIn(hh, 1f - hh)
    return listOf(PinQuad(0L, listOf(x - hw, y - hh, x + hw, y - hh, x + hw, y + hh, x - hw, y + hh)))
}

/** 3×3 homographies (row-major, h[8] = 1). */
object Homography {
    /** Maps the unit square (0,0)(1,0)(1,1)(0,1) onto quad [q]. */
    fun squareTo(q: FloatArray): DoubleArray? = fit(doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0), q.map { it.toDouble() }.toDoubleArray())

    /** Least-squares homography taking points [src] to [dst] (x,y pairs, at least 4), normalised for accuracy. */
    fun fit(src: DoubleArray, dst: DoubleArray, idx: IntArray? = null): DoubleArray? {
        val ids = idx ?: IntArray(src.size / 2) { it }
        if (ids.size < 4) return null
        fun norm(p: DoubleArray): DoubleArray {
            var cx = 0.0; var cy = 0.0
            for (i in ids) { cx += p[2 * i]; cy += p[2 * i + 1] }
            cx /= ids.size; cy /= ids.size
            var d = 0.0
            for (i in ids) d += sqrt((p[2 * i] - cx).let { it * it } + (p[2 * i + 1] - cy).let { it * it })
            d /= ids.size
            val s = if (d > 1e-12) sqrt(2.0) / d else 1.0
            return doubleArrayOf(s, 0.0, -s * cx, 0.0, s, -s * cy, 0.0, 0.0, 1.0)
        }
        val ts = norm(src); val td = norm(dst)
        val ata = Array(8) { DoubleArray(8) }; val atb = DoubleArray(8)
        val r = DoubleArray(8)
        for (i in ids) {
            val x = ts[0] * src[2 * i] + ts[2]; val y = ts[4] * src[2 * i + 1] + ts[5]
            val u = td[0] * dst[2 * i] + td[2]; val v = td[4] * dst[2 * i + 1] + td[5]
            for (row in 0..1) {
                if (row == 0) { r[0] = x; r[1] = y; r[2] = 1.0; r[3] = 0.0; r[4] = 0.0; r[5] = 0.0; r[6] = -u * x; r[7] = -u * y }
                else { r[0] = 0.0; r[1] = 0.0; r[2] = 0.0; r[3] = x; r[4] = y; r[5] = 1.0; r[6] = -v * x; r[7] = -v * y }
                val b = if (row == 0) u else v
                for (a in 0 until 8) { atb[a] += r[a] * b; for (c in 0 until 8) ata[a][c] += r[a] * r[c] }
            }
        }
        val h = solve(ata, atb) ?: return null
        val hn = doubleArrayOf(h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1.0)
        // Undo the normalisation: H = Td⁻¹ · Hn · Ts.
        val tdInv = invert(td) ?: return null
        val m = mul(mul(tdInv, hn), ts)
        if (abs(m[8]) < 1e-12) return null
        return DoubleArray(9) { m[it] / m[8] }
    }

    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        val m = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) a[i][j] else b[i] } }
        for (c in 0 until n) {
            var p = c
            for (r in c + 1 until n) if (abs(m[r][c]) > abs(m[p][c])) p = r
            if (abs(m[p][c]) < 1e-12) return null
            val t = m[c]; m[c] = m[p]; m[p] = t
            for (r in 0 until n) if (r != c) {
                val f = m[r][c] / m[c][c]
                if (f != 0.0) for (k in c..n) m[r][k] -= f * m[c][k]
            }
        }
        return DoubleArray(n) { m[it][n] / m[it][it] }
    }

    fun mul(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { i -> val r = i / 3; val c = i % 3; a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c] }

    fun invert(m: DoubleArray): DoubleArray? {
        val a = m[0]; val b = m[1]; val c = m[2]; val d = m[3]; val e = m[4]; val f = m[5]; val g = m[6]; val h = m[7]; val i = m[8]
        val A = e * i - f * h; val B = -(d * i - f * g); val C = d * h - e * g
        val det = a * A + b * B + c * C
        if (abs(det) < 1e-15) return null
        val s = 1.0 / det
        return doubleArrayOf(
            A * s, -(b * i - c * h) * s, (b * f - c * e) * s,
            B * s, (a * i - c * g) * s, -(a * f - c * d) * s,
            C * s, -(a * h - b * g) * s, (a * e - b * d) * s,
        )
    }

    fun apply(h: DoubleArray, x: Double, y: Double): Pair<Double, Double> {
        val w = h[6] * x + h[7] * y + h[8]
        return ((h[0] * x + h[1] * y + h[2]) / w) to ((h[3] * x + h[4] * y + h[5]) / w)
    }

    /** Column-major 3×3 for GLSL mat3 uniforms. */
    fun glMat(h: DoubleArray) = floatArrayOf(h[0].toFloat(), h[3].toFloat(), h[6].toFloat(), h[1].toFloat(), h[4].toFloat(), h[7].toFloat(), h[2].toFloat(), h[5].toFloat(), h[8].toFloat())
}

/**
 * Planar tracking: follows a flat surface inside the
 * four corners frame by frame — corner points found inside it are followed with pyramidal
 * Lucas–Kanade (checked forwards and backwards), then the surface's perspective is fitted to them
 * with RANSAC. Own code, on the phone.
 */
object PlanarTracker {
    private const val W = 480

    private class Gray(val w: Int, val h: Int, val px: FloatArray) {
        fun at(x: Float, y: Float): Float {
            val xx = x.coerceIn(0f, w - 1.001f); val yy = y.coerceIn(0f, h - 1.001f)
            val ix = xx.toInt(); val iy = yy.toInt(); val fx = xx - ix; val fy = yy - iy
            val i = iy * w + ix
            val a = px[i]; val b = px[i + 1]; val c = px[i + w]; val d = px[i + w + 1]
            return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy
        }
        fun half(): Gray {
            val nw = max(1, w / 2); val nh = max(1, h / 2)
            val o = FloatArray(nw * nh)
            for (y in 0 until nh) for (x in 0 until nw) {
                val x0 = min(2 * x, w - 1); val x1 = min(2 * x + 1, w - 1); val y0 = min(2 * y, h - 1); val y1 = min(2 * y + 1, h - 1)
                o[y * nw + x] = (px[y0 * w + x0] + px[y0 * w + x1] + px[y1 * w + x0] + px[y1 * w + x1]) * 0.25f
            }
            return Gray(nw, nh, o)
        }
    }

    private fun gray(b: Bitmap): Gray {
        val s = if (b.width == W) b else Bitmap.createScaledBitmap(b, W, (W.toFloat() * b.height / b.width).toInt().coerceAtLeast(8), true)
        val p = IntArray(s.width * s.height); s.getPixels(p, 0, s.width, 0, 0, s.width, s.height)
        return Gray(s.width, s.height, FloatArray(p.size) { val c = p[it]; ((c shr 16) and 255) * 0.299f + ((c shr 8) and 255) * 0.587f + (c and 255) * 0.114f })
    }
    private fun pyramid(g: Gray): List<Gray> { val l = mutableListOf(g); repeat(3) { l += l.last().half() }; return l }

    /** Good corners (Shi–Tomasi) inside quad [q] (pixels). */
    private fun features(g: Gray, q: FloatArray, maxN: Int): List<Pair<Float, Float>> {
        val minX = max(3, listOf(q[0], q[2], q[4], q[6]).minOrNull()!!.toInt()); val maxX = min(g.w - 4, listOf(q[0], q[2], q[4], q[6]).maxOrNull()!!.toInt())
        val minY = max(3, listOf(q[1], q[3], q[5], q[7]).minOrNull()!!.toInt()); val maxY = min(g.h - 4, listOf(q[1], q[3], q[5], q[7]).maxOrNull()!!.toInt())
        if (maxX - minX < 8 || maxY - minY < 8) return emptyList()
        // Shrink a little: the edges of the surface often show the background behind.
        val cx = (q[0] + q[2] + q[4] + q[6]) / 4f; val cy = (q[1] + q[3] + q[5] + q[7]) / 4f
        val qs = FloatArray(8) { if (it % 2 == 0) cx + (q[it] - cx) * 0.92f else cy + (q[it] - cy) * 0.92f }
        val step = max(2, (maxX - minX + maxY - minY) / 120)
        val cand = ArrayList<Triple<Float, Int, Int>>()
        for (y in minY..maxY step step) for (x in minX..maxX step step) {
            if (!inside(qs, x + 0.5f, y + 0.5f)) continue
            var sxx = 0f; var syy = 0f; var sxy = 0f
            for (j in -3..3) for (i in -3..3) {
                val xx = x + i; val yy = y + j
                val gx = g.px[yy * g.w + xx + 1] - g.px[yy * g.w + xx - 1]
                val gy = g.px[(yy + 1) * g.w + xx] - g.px[(yy - 1) * g.w + xx]
                sxx += gx * gx; syy += gy * gy; sxy += gx * gy
            }
            val tr = sxx + syy; val det = sxx * syy - sxy * sxy
            val lmin = tr / 2f - sqrt(max(0f, tr * tr / 4f - det))
            if (lmin > 200f) cand += Triple(lmin, x, y)
        }
        cand.sortByDescending { it.first }
        val out = ArrayList<Pair<Float, Float>>()
        val minD2 = (step * 3f).let { it * it }
        for ((_, x, y) in cand) {
            if (out.any { (it.first - x) * (it.first - x) + (it.second - y) * (it.second - y) < minD2 }) continue
            out += x.toFloat() to y.toFloat()
            if (out.size >= maxN) break
        }
        return out
    }

    private fun inside(q: FloatArray, x: Float, y: Float): Boolean {
        var s = 0
        for (k in 0 until 4) {
            val ax = q[2 * k]; val ay = q[2 * k + 1]; val bx = q[(2 * k + 2) % 8]; val by = q[(2 * k + 3) % 8]
            val c = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
            s += if (c >= 0) 1 else -1
        }
        return abs(s) == 4
    }

    /** Pyramidal Lucas–Kanade: where (x, y) of [a] is in [b], or null. */
    private fun lk(a: List<Gray>, b: List<Gray>, x: Float, y: Float): Pair<Float, Float>? {
        var gx = 0f; var gy = 0f
        val half = 7
        for (lvl in a.indices.reversed()) {
            val s = 1f / (1 shl lvl)
            val A = a[lvl]; val B = b[lvl]
            val px = x * s; val py = y * s
            // Gradient matrix of A around the point.
            var gxx = 0f; var gyy = 0f; var gxy = 0f
            val n = (2 * half + 1) * (2 * half + 1)
            val ix = FloatArray(n); val iy = FloatArray(n); val iv = FloatArray(n)
            var k = 0
            for (j in -half..half) for (i in -half..half) {
                val xx = px + i; val yy = py + j
                val dx = (A.at(xx + 1, yy) - A.at(xx - 1, yy)) * 0.5f
                val dy = (A.at(xx, yy + 1) - A.at(xx, yy - 1)) * 0.5f
                ix[k] = dx; iy[k] = dy; iv[k] = A.at(xx, yy)
                gxx += dx * dx; gyy += dy * dy; gxy += dx * dy; k++
            }
            val det = gxx * gyy - gxy * gxy
            if (det < 1e-3f) return null
            var vx = 0f; var vy = 0f
            for (it in 0 until 12) {
                var bx = 0f; var by = 0f; k = 0
                for (j in -half..half) for (i in -half..half) {
                    val d = iv[k] - B.at(px + i + gx + vx, py + j + gy + vy)
                    bx += d * ix[k]; by += d * iy[k]; k++
                }
                val ex = (gyy * bx - gxy * by) / det; val ey = (gxx * by - gxy * bx) / det
                vx += ex; vy += ey
                if (ex * ex + ey * ey < 0.0009f) break
            }
            gx += vx; gy += vy
            if (lvl > 0) { gx *= 2f; gy *= 2f }
        }
        val nx = x + gx; val ny = y + gy
        if (nx < 0 || ny < 0 || nx >= a[0].w || ny >= a[0].h) return null
        return nx to ny
    }

    /** The perspective change between point sets, robust to points that don't move with the surface. */
    private fun ransac(src: DoubleArray, dst: DoubleArray, thr: Double): DoubleArray? {
        val n = src.size / 2
        if (n < 6) return null
        val rnd = java.util.Random(7)
        var best: IntArray? = null
        repeat(250) {
            val pick = IntArray(4); var c = 0
            while (c < 4) { val r = rnd.nextInt(n); if ((0 until c).none { pick[it] == r }) pick[c++] = r }
            val h = Homography.fit(src, dst, pick) ?: return@repeat
            val inl = (0 until n).filter { i ->
                val (u, v) = Homography.apply(h, src[2 * i], src[2 * i + 1])
                val dx = u - dst[2 * i]; val dy = v - dst[2 * i + 1]
                dx * dx + dy * dy < thr * thr
            }.toIntArray()
            if (best == null || inl.size > best!!.size) best = inl
        }
        val b = best ?: return null
        if (b.size < 6 || b.size < n * 0.3) return null
        return Homography.fit(src, dst, b)
    }

    private fun convex(q: FloatArray): Boolean {
        var sign = 0
        for (k in 0 until 4) {
            val ax = q[2 * k]; val ay = q[2 * k + 1]; val bx = q[(2 * k + 2) % 8]; val by = q[(2 * k + 3) % 8]; val cx = q[(2 * k + 4) % 8]; val cy = q[(2 * k + 5) % 8]
            val z = (bx - ax) * (cy - by) - (by - ay) * (cx - bx)
            val s = if (z > 0) 1 else -1
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    /**
     * Tracks the surface inside [quad0] (0..1 of the frame) of clip [clipIndex] from timeline [fromMs]
     * to [toMs]. Returns the corners per frame; stops where the surface is lost.
     */
    fun track(
        context: Context, clips: List<Clip>, clipIndex: Int, fromMs: Long, toMs: Long, quad0: FloatArray,
        progress: (Int) -> Unit, cancelled: () -> Boolean,
    ): List<PinQuad> {
        val c = clips.getOrNull(clipIndex) ?: return emptyList()
        val clipStart = clipStarts(clips)[clipIndex]
        val until = minOf(toMs, clipStart + c.lengthMs - 1)
        val srcFrom = c.srcAtOffset(fromMs - clipStart); val srcTo = c.srcAtOffset(until - clipStart)
        if (srcTo - srcFrom < 50) return emptyList()
        val out = ArrayList<PinQuad>()
        var prev: List<Gray>? = null
        var q = quad0.copyOf()   // in pixels once the size is known
        var scaled = false
        FrameReader.read(context, c.uri, srcFrom, srcTo, W, 0) { srcMs, bmp ->
            if (cancelled()) return@read false
            val g = gray(bmp)
            val pyr = pyramid(g)
            if (!scaled) { for (i in 0 until 8) q[i] *= if (i % 2 == 0) g.w.toFloat() else g.h.toFloat(); scaled = true }
            val p = prev
            if (p != null) {
                val f = features(p[0], q, 160)
                val src = ArrayList<Double>(); val dst = ArrayList<Double>()
                for ((x, y) in f) {
                    val fw = lk(p, pyr, x, y) ?: continue
                    val bw = lk(pyr, p, fw.first, fw.second) ?: continue
                    val e = (bw.first - x).let { it * it } + (bw.second - y).let { it * it }
                    if (e > 1.0f) continue
                    src += x.toDouble(); src += y.toDouble(); dst += fw.first.toDouble(); dst += fw.second.toDouble()
                }
                val h = ransac(src.toDoubleArray(), dst.toDoubleArray(), 1.5) ?: return@read false
                val nq = FloatArray(8)
                for (k in 0 until 4) { val (u, v) = Homography.apply(h, q[2 * k].toDouble(), q[2 * k + 1].toDouble()); nq[2 * k] = u.toFloat(); nq[2 * k + 1] = v.toFloat() }
                // Lost: the shape folds over, or it left the picture.
                if (!convex(nq) || nq.indices.any { val v = nq[it]; val lim = if (it % 2 == 0) g.w else g.h; v < -lim * 0.5f || v > lim * 1.5f }) return@read false
                q = nq
            }
            prev = pyr
            val tl = clipStart + c.tlOffsetOf(srcMs)
            out += PinQuad(tl, List(8) { i -> q[i] / (if (i % 2 == 0) g.w else g.h) })
            progress((((srcMs - srcFrom) * 100) / (srcTo - srcFrom).coerceAtLeast(1)).toInt().coerceIn(0, 100))
            true
        }
        return out
    }
}

// ---------------------------------------------------------------- export

private const val PIN_VERT = """
    attribute vec4 aFramePosition;
    varying vec2 vUv;
    void main() { gl_Position = aFramePosition; vUv = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5); }
"""

/** Picture layers (text, image, logo, sticker) drawn onto their four corners over the video. */
@OptIn(UnstableApi::class)
class CornerPinEffect(private val bitmap: Bitmap, private val layer: Layer) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = PinOverProgram(bitmap, layer, useHdr)
}

@OptIn(UnstableApi::class)
private class PinOverProgram(private val bitmap: Bitmap, private val l: Layer, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program = try { GlProgram(PIN_VERT, FRAG) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private var tex = 0
    init { program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE) }
    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)
    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            if (tex == 0) {
                val t = IntArray(1); GLES20.glGenTextures(1, t, 0); tex = t[0]
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
                for ((p, v) in listOf(GLES20.GL_TEXTURE_MIN_FILTER to GLES20.GL_LINEAR, GLES20.GL_TEXTURE_MAG_FILTER to GLES20.GL_LINEAR, GLES20.GL_TEXTURE_WRAP_S to GLES20.GL_CLAMP_TO_EDGE, GLES20.GL_TEXTURE_WRAP_T to GLES20.GL_CLAMP_TO_EDGE))
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, p, v)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            }
            val ms = presentationTimeUs / 1000
            val on = l.wholeVideo || ms in l.startMs..l.endMs
            val q = l.pinAt(ms)
            val h = q?.let { Homography.squareTo(it) }?.let { Homography.invert(it) }
            program.use()
            program.setSamplerTexIdUniform("uIn", inputTexId, 0)
            program.setSamplerTexIdUniform("uLayer", tex, 1)
            program.setFloatsUniform("uInv", if (h != null) Homography.glMat(h) else floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))
            program.setFloatUniform("uAlpha", if (on && h != null) l.animAt(ms).opacity.coerceIn(0f, 1f) else 0f)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }
    override fun release() {
        super.release()
        if (tex != 0) GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
        try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }
    companion object {
        const val FRAG = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform sampler2D uIn;
            uniform sampler2D uLayer;
            uniform mat3 uInv;
            uniform float uAlpha;
            varying vec2 vUv;
            void main() {
                vec4 base = texture2D(uIn, vUv);
                vec3 p = uInv * vec3(vUv.x, 1.0 - vUv.y, 1.0);
                vec2 q = p.xy / p.z;
                if (uAlpha <= 0.0 || p.z <= 0.0 || q.x < 0.0 || q.y < 0.0 || q.x > 1.0 || q.y > 1.0) { gl_FragColor = base; return; }
                vec4 c = texture2D(uLayer, q);   // premultiplied
                gl_FragColor = vec4(base.rgb * (1.0 - c.a * uAlpha) + c.rgb * uAlpha, base.a);
            }
        """
    }
}

/**
 * Video layers: the layer's picture warped onto its four corners on a full-frame, see-through canvas
 * (the compositor then lays the canvas over the video). [startMs] = where the layer starts.
 */
@OptIn(UnstableApi::class)
class PinWarpEffect(private val layer: Layer, private val outW: Int, private val outH: Int, private val startMs: Long) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = PinWarpProgram(layer, outW, outH, startMs, useHdr)
}

@OptIn(UnstableApi::class)
private class PinWarpProgram(private val l: Layer, private val outW: Int, private val outH: Int, private val startMs: Long, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program = try { GlProgram(PIN_VERT, FRAG) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private var t0 = Long.MIN_VALUE
    init { program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE) }
    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(outW, outH)
    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        if (t0 == Long.MIN_VALUE) t0 = presentationTimeUs
        val ms = startMs + (presentationTimeUs - t0) / 1000
        val h = l.pinAt(ms)?.let { Homography.squareTo(it) }?.let { Homography.invert(it) }
        try {
            program.use()
            program.setSamplerTexIdUniform("uIn", inputTexId, 0)
            program.setFloatsUniform("uInv", if (h != null) Homography.glMat(h) else floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }
    override fun release() { super.release(); try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) } }
    companion object {
        const val FRAG = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform sampler2D uIn;
            uniform mat3 uInv;
            varying vec2 vUv;
            void main() {
                vec3 p = uInv * vec3(vUv.x, 1.0 - vUv.y, 1.0);
                vec2 q = p.xy / p.z;
                if (p.z <= 0.0 || q.x < 0.0 || q.y < 0.0 || q.x > 1.0 || q.y > 1.0) { gl_FragColor = vec4(0.0); return; }
                gl_FragColor = texture2D(uIn, vec2(q.x, 1.0 - q.y));
            }
        """
    }
}

/** The perspective matrix that draws a w×h picture onto quad [q] (0..1) of a fw×fh view. */
fun pinMatrix(q: FloatArray, w: Float, h: Float, fw: Float, fh: Float): android.graphics.Matrix =
    android.graphics.Matrix().apply {
        setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, FloatArray(8) { if (it % 2 == 0) q[it] * fw else q[it] * fh }, 0, 4)
    }
