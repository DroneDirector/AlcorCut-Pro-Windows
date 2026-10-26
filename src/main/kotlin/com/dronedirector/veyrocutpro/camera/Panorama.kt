package com.dronedirector.veyrocutpro.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Sweep panorama. While the user turns, the gyroscope says how far they've turned and a frame is
 * taken every ~third of a frame width (aiming for about 180°). Stitching: every frame is projected
 * onto a cylinder (so turning doesn't bend straight lines), aligned to its neighbour in 2D around
 * the gyroscope's estimate, brightness-matched, blended with wide soft seams and trimmed.
 */
object Panorama {

    /** Most frames per panorama (the sweep normally ends at [TARGET_DEG] before that). */
    const val MAX_FRAMES = 24
    /** Sweep the panorama aims for, in degrees. */
    const val TARGET_DEG = 180f
    /** Share of the frame width between frames (the rest overlaps, for alignment). */
    const val STEP = 0.33f
    private const val FRAME_HEIGHT = 1080

    /** Rotates a captured frame upright and scales it to the working height. */
    fun prepareFrame(src: Bitmap, rotationDegrees: Int): Bitmap {
        val upright = if (rotationDegrees != 0) {
            val m = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true).also { if (it !== src) src.recycle() }
        } else src
        val target = if (upright.width > upright.height) FRAME_HEIGHT else (FRAME_HEIGHT * 4 / 3)
        if (upright.height <= target) return upright
        val w = (upright.width * target.toFloat() / upright.height).roundToInt()
        return Bitmap.createScaledBitmap(upright, w, target, true).also { if (it !== upright) upright.recycle() }
    }

    /** A frame on the cylinder: ARGB pixels (alpha 0 = no picture there). */
    private class Cyl(val w: Int, val h: Int, val px: IntArray)

    /**
     * [frames] in the order taken, [angles] the turn (degrees, signed) when each was taken (or null
     * without a gyroscope), [longFovDeg] the camera's field of view across the frame's long side.
     * [direction] > 0 means the user turned right.
     */
    /** [keepEdges] = keep the uneven edges (empty, transparent) instead of trimming them, for boundary warp / AI fill. */
    fun stitch(frames: List<Bitmap>, angles: List<Float>?, longFovDeg: Float, direction: Int, filter: Filter, keepEdges: Boolean = false): Bitmap? {
        if (frames.isEmpty()) return null
        val w0 = frames[0].width; val h0 = frames[0].height
        val fov = Math.toRadians(longFovDeg.coerceIn(30f, 130f).toDouble())
        val f = (max(w0, h0) / (2.0 * tan(fov / 2))).toFloat()
        var cylTaken = frames.map { cylinder(it, f) }
        var turnLeft = direction < 0
        // Double-check the direction with the pictures themselves (the first two frames).
        if (cylTaken.size >= 2) {
            val prior = angles?.takeIf { it.size == frames.size }?.let { (abs(it[1] - it[0]) * Math.PI / 180.0 * f).toFloat() }
            val asRight = register(cylTaken[0], cylTaken[1], prior).score
            val asLeft = register(cylTaken[1], cylTaken[0], prior).score
            if (turnLeft && asRight > asLeft + 0.15f) turnLeft = false
            else if (!turnLeft && asLeft > asRight + 0.15f) turnLeft = true
        }
        val cyl = if (turnLeft) cylTaken.reversed() else cylTaken
        cylTaken = emptyList()
        val angs = angles?.takeIf { it.size == frames.size }?.let { if (turnLeft) it.reversed() else it }
        val cw = cyl[0].w; val ch = cyl[0].h

        // Offsets of each frame relative to the previous one, and brightness gains.
        val offX = IntArray(cyl.size); val offY = IntArray(cyl.size)
        val gains = FloatArray(cyl.size) { 1f }
        for (i in 1 until cyl.size) {
            val prior = angs?.let { (abs(it[i] - it[i - 1]) * Math.PI / 180.0 * f).toFloat() }
            val m = register(cyl[i - 1], cyl[i], prior)
            val dx = m.dx; val dy = m.dy; val gain = m.gain
            offX[i] = offX[i - 1] + dx
            offY[i] = offY[i - 1] + dy
            gains[i] = gains[i - 1] * gain
        }
        // Even out the brightness around the middle frame.
        val mid = gains[gains.size / 2]
        for (i in gains.indices) gains[i] = (gains[i] / mid).coerceIn(0.5f, 2f)

        val minY = offY.min(); val maxY = offY.max()
        val outW = offX.last() + cw
        val outH = ch + (maxY - minY)
        if (outW.toLong() * outH > 60_000_000L) return null
        val out = IntArray(outW * outH)
        for (i in cyl.indices) {
            val c = cyl[i]
            val ox = offX[i]; val oy = offY[i] - minY
            val g = gains[i]
            // Blend across the middle of the overlap with the previous frame.
            val prevEnd = if (i > 0) offX[i - 1] + cw else ox
            val overlap = prevEnd - ox
            val feather = if (overlap > 8) overlap * 0.6f else 0f
            val fStart = ox + (overlap - feather) / 2f
            for (y in 0 until c.h) {
                val oyy = y + oy
                if (oyy !in 0 until outH) continue
                val row = y * c.w
                for (x in 0 until c.w) {
                    val p = c.px[row + x]
                    if ((p ushr 24) == 0) continue
                    val X = x + ox
                    val idx = oyy * outW + X
                    var r = ((p shr 16) and 0xFF) * g; var gg = ((p shr 8) and 0xFF) * g; var b = (p and 0xFF) * g
                    val q = out[idx]
                    if ((q ushr 24) != 0 && feather > 0f && X < fStart + feather) {
                        val a = ((X - fStart) / feather).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
                        r = r * a + ((q shr 16) and 0xFF) * (1 - a)
                        gg = gg * a + ((q shr 8) and 0xFF) * (1 - a)
                        b = b * a + (q and 0xFF) * (1 - a)
                    }
                    out[idx] = (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (gg.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
                }
            }
        }

        // Trim to the area every part of the panorama covers (no empty corners).
        val tops = IntArray(outW) { outH }; val bottoms = IntArray(outW) { -1 }
        for (x in 0 until outW) {
            var y = 0
            while (y < outH && (out[y * outW + x] ushr 24) == 0) y++
            tops[x] = y
            y = outH - 1
            while (y >= 0 && (out[y * outW + x] ushr 24) == 0) y--
            bottoms[x] = y
        }
        val covered = (0 until outW).filter { bottoms[it] - tops[it] > outH / 3 }
        if (covered.isEmpty()) return null
        val x0 = covered.first(); val x1 = covered.last()
        val ts = (x0..x1).map { tops[it] }.sorted(); val bs = (x0..x1).map { bottoms[it] }.sorted()
        val top = if (keepEdges) ts.first() else ts[(ts.size * 0.97).toInt().coerceAtMost(ts.size - 1)]
        val bottom = if (keepEdges) bs.last() else bs[(bs.size * 0.03).toInt()]
        // Columns at the ends that don't reach the full band are trimmed too.
        var l = x0; while (!keepEdges && l < x1 && (tops[l] > top + 2 || bottoms[l] < bottom - 2)) l++
        var r = x1; while (!keepEdges && r > l && (tops[r] > top + 2 || bottoms[r] < bottom - 2)) r--
        val cropW = r - l + 1; val cropH = bottom - top + 1
        if (cropW < 32 || cropH < 32) return null
        var result = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
        result.setPixels(out, top * outW + l, outW, 0, 0, cropW, cropH)
        if (filter != Filter.NONE) {
            val fb = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
            Canvas(fb).drawBitmap(result, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(filter.matrix) })
            result.recycle()
            result = fb
        }
        return result
    }

    /** Projects a flat frame onto a cylinder of radius [f] (pixels). */
    private fun cylinder(b: Bitmap, f: Float): Cyl {
        val w = b.width; val h = b.height
        val src = IntArray(w * h); b.getPixels(src, 0, w, 0, 0, w, h)
        val cx = (w - 1) / 2f; val cy = (h - 1) / 2f
        val halfAng = atan((w / 2f) / f)
        val ow = (2 * f * halfAng).toInt().coerceAtLeast(2)
        val ocx = (ow - 1) / 2f
        val out = IntArray(ow * h)
        for (u in 0 until ow) {
            val th = (u - ocx) / f
            val xs = cx + f * tan(th)
            if (xs < 0f || xs > w - 1f) continue
            val inv = 1f / cos(th)
            val x0 = xs.toInt().coerceAtMost(w - 2); val fx = xs - x0
            for (v in 0 until h) {
                val ys = cy + (v - cy) * inv
                if (ys < 0f || ys > h - 1f) continue
                val y0 = ys.toInt().coerceAtMost(h - 2); val fy = ys - y0
                val i = y0 * w + x0
                out[v * ow + u] = bilerp(src[i], src[i + 1], src[i + w], src[i + w + 1], fx, fy)
            }
        }
        return Cyl(ow, h, out)
    }

    private fun bilerp(a: Int, b: Int, c: Int, d: Int, fx: Float, fy: Float): Int {
        fun ch(s: Int): Int {
            val top = ((a shr s) and 0xFF) * (1 - fx) + ((b shr s) and 0xFF) * fx
            val bot = ((c shr s) and 0xFF) * (1 - fx) + ((d shr s) and 0xFF) * fx
            return (top * (1 - fy) + bot * fy).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Small grey copy of a cylinder frame (-1 = no picture). */
    private fun small(c: Cyl, scale: Int): Triple<Int, Int, FloatArray> {
        val sw = c.w / scale; val sh = c.h / scale
        val g = FloatArray(sw * sh)
        for (y in 0 until sh) for (x in 0 until sw) {
            var s = 0f; var n = 0; var empty = false
            for (j in 0 until scale) for (i in 0 until scale) {
                val p = c.px[(y * scale + j) * c.w + x * scale + i]
                if ((p ushr 24) == 0) { empty = true; continue }
                s += ((p shr 16) and 0xFF) * 0.3f + ((p shr 8) and 0xFF) * 0.59f + (p and 0xFF) * 0.11f; n++
            }
            g[y * sw + x] = if (empty || n == 0) -1f else s / n
        }
        return Triple(sw, sh, g)
    }

    /** Normalised cross-correlation of b placed at (dx, dy) over a (overlap only); also the brightness ratio. */
    private fun zncc(a: FloatArray, b: FloatArray, w: Int, h: Int, dx: Int, dy: Int, step: Int): Pair<Float, Float> {
        var sa = 0.0; var sb = 0.0; var saa = 0.0; var sbb = 0.0; var sab = 0.0; var n = 0
        var y = max(0, dy)
        val yEnd = min(h, h + dy)
        while (y < yEnd) {
            var x = max(0, dx)
            while (x < w) {
                val bx = x - dx; val by = y - dy
                if (bx in 0 until w && by in 0 until h) {
                    val va = a[y * w + x]; val vb = b[by * w + bx]
                    if (va >= 0f && vb >= 0f) {
                        sa += va; sb += vb; saa += va * va; sbb += vb * vb; sab += va * vb; n++
                    }
                }
                x += step
            }
            y += step
        }
        if (n < 60) return -1f to 1f
        val ma = sa / n; val mb = sb / n
        val cov = sab / n - ma * mb
        val va = saa / n - ma * ma; val vb = sbb / n - mb * mb
        if (va < 1e-3 || vb < 1e-3) return 0f to 1f
        return (cov / sqrt(va * vb)).toFloat() to (if (mb > 1) (ma / mb).toFloat() else 1f)
    }

    /**
     * Where frame b sits relative to frame a (full-size pixels: right, down) and the brightness gain
     * that makes b match a. [prior] is the expected right shift from the gyroscope, if known.
     */
    private class Match(val dx: Int, val dy: Int, val gain: Float, val score: Float)

    private fun register(a: Cyl, b: Cyl, prior: Float?): Match {
        val S = 4
        val (sw, sh, ga) = small(a, S)
        val (_, _, gb) = small(b, S)
        val pr = prior?.let { it / S }
        val lo = if (pr != null) max((sw * 0.04f).toInt(), (pr - sw * 0.15f).toInt()) else (sw * 0.04f).toInt()
        val hi = if (pr != null) min((sw * 0.92f).toInt(), (pr + sw * 0.15f).toInt()) else (sw * 0.9f).toInt()
        val maxDy = (sh * 0.08f).toInt()
        var best = -2f; var bx = pr?.roundToInt() ?: (sw * STEP).toInt(); var by = 0
        // Coarse search, then fine search around the best.
        var dx = lo
        while (dx <= hi) {
            var dy = -maxDy
            while (dy <= maxDy) {
                val (s, _) = zncc(ga, gb, sw, sh, dx, dy, 2)
                // Slight preference for the gyroscope's estimate when the scene is plain.
                val score = s - (if (pr != null) abs(dx - pr) / sw * 0.15f else 0f)
                if (score > best) { best = score; bx = dx; by = dy }
                dy += 2
            }
            dx += 2
        }
        var fine = -2f; var fx = bx; var fy = by
        for (ddx in bx - 2..bx + 2) for (ddy in by - 2..by + 2) {
            val (s, _) = zncc(ga, gb, sw, sh, ddx, ddy, 1)
            if (s > fine) { fine = s; fx = ddx; fy = ddy }
        }
        val (_, ratio) = zncc(ga, gb, sw, sh, fx, fy, 1)
        // Not trustworthy (plain wall, darkness): use the gyroscope's estimate.
        if (fine < 0.3f && pr != null) return Match((pr * S).roundToInt(), 0, ratio.coerceIn(0.6f, 1.6f), fine)
        return Match(fx * S, fy * S, ratio.coerceIn(0.6f, 1.6f), fine)
    }
}
