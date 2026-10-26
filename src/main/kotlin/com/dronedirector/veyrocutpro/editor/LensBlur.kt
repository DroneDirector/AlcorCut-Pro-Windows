package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Lens Blur: a shallow depth of field made from the AI depth map. Everything nearer or farther
 * than the focus gets blurred by how far it is from it, with bokeh of the chosen shape.
 */
data class LensBlur(
    /** Depth map (grey PNG, white = near) of the uncropped photo, in the app's files. */
    val depthFile: String? = null,
    val amount: Float = 0.5f,
    /** Depth in focus (0 far … 1 near) and how deep the sharp zone is. */
    val focus: Float = 0.8f,
    val range: Float = 0.12f,
    /** 0 circle, 1 hexagon (6 blades), 2 octagon, 3 ring (mirror lens), 4 cat's eye. */
    val shape: Int = 0,
    /** Makes bright points glow into bokeh balls (0..1). */
    val boost: Float = 0.4f,
    val on: Boolean = true,
) {
    val active get() = on && depthFile != null && amount > 0f
}

val BOKEH_SHAPES = listOf("Circle", "Hexagon", "Octagon", "Ring", "Cat's eye")

object LensBlurFx {
    /** Works out the depth of [bmp] with the on-phone AI and saves it. Returns the file or null. Slow: off the main thread. */
    fun analyse(context: Context, bmp: Bitmap): String? {
        val (d, w, h) = DepthModel.infer(context, bmp) ?: return null
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
        for (v in d) { if (v < lo) lo = v; if (v > hi) hi = v }
        val k = if (hi - lo > 1e-6f) 255f / (hi - lo) else 0f
        val px = IntArray(w * h) { i -> val g = ((d[i] - lo) * k).toInt().coerceIn(0, 255); (0xFF shl 24) or (g shl 16) or (g shl 8) or g }
        val b = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val dir = java.io.File(context.filesDir, "masks").apply { mkdirs() }
        val f = java.io.File(dir, "depth_${System.currentTimeMillis()}.png")
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.path
    }

    /** Depth 0..1 at (x, y) of a depth map (both 0..1 of the picture). */
    fun depthAt(depth: Bitmap, x: Float, y: Float): Float {
        val px = (x * (depth.width - 1)).toInt().coerceIn(0, depth.width - 1); val py = (y * (depth.height - 1)).toInt().coerceIn(0, depth.height - 1)
        return ((depth.getPixel(px, py) shr 16) and 0xFF) / 255f
    }

    /** Kernel offsets (dx, dy) for a bokeh of [shape] and radius [r] (pixels). */
    private fun kernel(shape: Int, r: Int): IntArray {
        val out = ArrayList<Int>()
        val rr = r.toFloat().coerceAtLeast(0.5f)
        for (dy in -r..r) for (dx in -r..r) {
            val d = sqrt((dx * dx + dy * dy).toFloat())
            val inside = when (shape) {
                1, 2 -> {
                    // Regular polygon with n blades.
                    val n = if (shape == 1) 6 else 8
                    val a = atan2(dy.toFloat(), dx.toFloat())
                    val seg = (2 * PI / n).toFloat()
                    val local = ((a % seg) + seg) % seg - seg / 2
                    d * cos(local) <= rr * cos(seg / 2)
                }
                3 -> d <= rr && d >= rr * 0.55f
                4 -> d <= rr && sqrt(((dx - rr * 0.35f) * (dx - rr * 0.35f) + dy * dy)) <= rr * 1.05f
                else -> d <= rr
            }
            if (inside) { out += dx; out += dy }
        }
        if (out.isEmpty()) { out += 0; out += 0 }
        return out.toIntArray()
    }

    /** Blurs [px] (w×h) with the depth map [depth] (already placed to match the picture). */
    fun apply(px: IntArray, w: Int, h: Int, depth: Bitmap, lb: LensBlur) {
        val dm = if (depth.width == w && depth.height == h) depth else Bitmap.createScaledBitmap(depth, w, h, true)
        val dp = IntArray(w * h); dm.getPixels(dp, 0, w, 0, 0, w, h)
        if (dm !== depth) dm.recycle()
        val maxR = max(w, h) * 0.025f * lb.amount * 2f
        if (maxR < 0.8f) return
        // Blur radius per pixel from the distance to the focus zone.
        val coc = FloatArray(w * h)
        for (i in dp.indices) {
            val d = ((dp[i] shr 16) and 0xFF) / 255f
            val off = (abs(d - lb.focus) - lb.range).coerceAtLeast(0f)
            coc[i] = (off / 0.5f).coerceIn(0f, 1f) * maxR
        }
        // Linear-ish light with bright points boosted, so highlights bloom into bokeh.
        val lr = FloatArray(w * h); val lg = FloatArray(w * h); val lbArr = FloatArray(w * h)
        val boost = lb.boost * 3f
        for (i in px.indices) {
            val p = px[i]
            var r = ((p shr 16) and 0xFF) / 255f; var g = ((p shr 8) and 0xFF) / 255f; var b = (p and 0xFF) / 255f
            r *= r; g *= g; b *= b
            val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
            if (boost > 0f && l > 0.6f) { val k = 1f + boost * ((l - 0.6f) / 0.4f); r *= k; g *= k; b *= k }
            lr[i] = r; lg[i] = g; lbArr[i] = b
        }
        // A few blur levels, each made at a reduced size so big radii stay fast, then mixed by each pixel's radius.
        val levels = floatArrayOf(0f, maxR * 0.12f, maxR * 0.25f, maxR * 0.45f, maxR * 0.7f, maxR)
        val outR = FloatArray(w * h); val outG = FloatArray(w * h); val outB = FloatArray(w * h)
        var prev: Array<FloatArray>? = arrayOf(lr, lg, lbArr)
        for (li in 1 until levels.size) {
            val rad = levels[li]
            val cur = blurLevel(lr, lg, lbArr, w, h, rad, lb.shape)
            val r0 = levels[li - 1]
            val p = prev!!
            for (i in coc.indices) {
                val c = coc[i]
                if (c < r0 || c > rad) continue
                val t = if (rad - r0 > 1e-3f) (c - r0) / (rad - r0) else 1f
                outR[i] = p[0][i] + (cur[0][i] - p[0][i]) * t
                outG[i] = p[1][i] + (cur[1][i] - p[1][i]) * t
                outB[i] = p[2][i] + (cur[2][i] - p[2][i]) * t
            }
            prev = cur
        }
        for (i in px.indices) {
            val c = coc[i]
            val r: Float; val g: Float; val b: Float
            if (c <= 0.001f) { r = lr[i]; g = lg[i]; b = lbArr[i] } else { r = outR[i]; g = outG[i]; b = outB[i] }
            fun enc(v: Float) = (sqrt(v.coerceIn(0f, 1f)) * 255f + 0.5f).toInt().coerceIn(0, 255)
            px[i] = (px[i] and 0xFF000000.toInt()) or (enc(r) shl 16) or (enc(g) shl 8) or enc(b)
        }
    }

    private fun blurLevel(r: FloatArray, g: FloatArray, b: FloatArray, w: Int, h: Int, radius: Float, shape: Int): Array<FloatArray> {
        // Work at a size where the kernel is at most ~7 px.
        val s = min(1f, 7f / radius.coerceAtLeast(0.5f))
        val sw = max(1, (w * s).toInt()); val sh = max(1, (h * s).toInt())
        fun down(a: FloatArray): FloatArray {
            if (s >= 0.999f) return a
            val o = FloatArray(sw * sh); val cnt = IntArray(sw * sh)
            for (y in 0 until h) { val yy = min(sh - 1, (y * s).toInt()); for (x in 0 until w) { val k = yy * sw + min(sw - 1, (x * s).toInt()); o[k] += a[y * w + x]; cnt[k]++ } }
            for (i in o.indices) if (cnt[i] > 0) o[i] /= cnt[i]
            return o
        }
        val dr = down(r); val dg = down(g); val db = down(b)
        val kr = (radius * s).toInt().coerceAtLeast(1)
        val ker = kernel(shape, kr)
        val n = ker.size / 2
        val br = FloatArray(sw * sh); val bg = FloatArray(sw * sh); val bb = FloatArray(sw * sh)
        for (y in 0 until sh) for (x in 0 until sw) {
            var ar = 0f; var ag = 0f; var ab = 0f
            var k = 0
            while (k < ker.size) {
                val xx = (x + ker[k]).coerceIn(0, sw - 1); val yy = (y + ker[k + 1]).coerceIn(0, sh - 1)
                val j = yy * sw + xx
                ar += dr[j]; ag += dg[j]; ab += db[j]
                k += 2
            }
            val i = y * sw + x
            br[i] = ar / n; bg[i] = ag / n; bb[i] = ab / n
        }
        if (s >= 0.999f) return arrayOf(br, bg, bb)
        // Back to full size (bilinear).
        fun up(a: FloatArray): FloatArray {
            val o = FloatArray(w * h)
            for (y in 0 until h) {
                val fy = ((y + 0.5f) * s - 0.5f).coerceIn(0f, sh - 1f); val y0 = fy.toInt(); val y1 = min(y0 + 1, sh - 1); val ty = fy - y0
                for (x in 0 until w) {
                    val fx = ((x + 0.5f) * s - 0.5f).coerceIn(0f, sw - 1f); val x0 = fx.toInt(); val x1 = min(x0 + 1, sw - 1); val tx = fx - x0
                    val t = a[y0 * sw + x0] + (a[y0 * sw + x1] - a[y0 * sw + x0]) * tx
                    val bt = a[y1 * sw + x0] + (a[y1 * sw + x1] - a[y1 * sw + x0]) * tx
                    o[y * w + x] = t + (bt - t) * ty
                }
            }
            return o
        }
        return arrayOf(up(br), up(bg), up(bb))
    }
}
