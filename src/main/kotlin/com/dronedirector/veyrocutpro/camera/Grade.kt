package com.dronedirector.veyrocutpro.camera

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * A colour grade. Every control is a pure colour transform, so the whole grade can be baked into a
 * 3D lookup table: the same table drives the photo preview, saved photos and exported videos.
 */
data class Grade(
    /** Stops, -2..+2. */
    val exposure: Float = 0f,
    /** -1..1 for every control below. */
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    /** 0..1: lifts the blacks for a matte film finish. */
    val fade: Float = 0f,
    /** Split toning: hue 0..360 for shadows and highlights, with strength 0..1. */
    val shadowHue: Float = 200f,
    val shadowTone: Float = 0f,
    val highlightHue: Float = 35f,
    val highlightTone: Float = 0f,
    val look: Filter = Filter.NONE,
    /** 0..1 strength of the look. */
    val lookAmount: Float = 1f,
    /** Detail (photos): -1..1 clarity, texture, dehaze, vignette; 0..1 sharpening. */
    val clarity: Float = 0f,
    val texture: Float = 0f,
    val dehaze: Float = 0f,
    val sharpen: Float = 0f,
    val vignette: Float = 0f,
) {
    val hasDetail: Boolean get() = clarity != 0f || texture != 0f || dehaze != 0f || sharpen != 0f || vignette != 0f
    val isIdentity: Boolean get() = copy(lookAmount = 1f) == Grade()

    /** Grades one colour (components 0..1, modified in place). */
    fun apply(c: FloatArray) {
        var r = c[0]; var g = c[1]; var b = c[2]

        // Look first, blended by its strength.
        if (look != Filter.NONE && lookAmount > 0f) {
            val m = look.matrix
            val lr = (m[0] * r + m[1] * g + m[2] * b + m[4] / 255f).coerceIn(0f, 1f)
            val lg = (m[5] * r + m[6] * g + m[7] * b + m[9] / 255f).coerceIn(0f, 1f)
            val lb = (m[10] * r + m[11] * g + m[12] * b + m[14] / 255f).coerceIn(0f, 1f)
            val t = floatArrayOf(lr, lg, lb)
            look.look?.apply(t)
            r += (t[0] - r) * lookAmount; g += (t[1] - g) * lookAmount; b += (t[2] - b) * lookAmount
        }

        // Exposure in (approximately) linear light.
        if (exposure != 0f) {
            val k = 2f.pow(exposure)
            r = lin(r) * k; g = lin(g) * k; b = lin(b) * k
            r = gam(r); g = gam(g); b = gam(b)
        }

        // White balance.
        r += temperature * 0.08f
        b -= temperature * 0.08f
        g -= tint * 0.06f
        r += tint * 0.02f; b += tint * 0.02f

        // Tonal ranges.
        var l = luma(r, g, b)
        val sh = 1f - smooth(0f, 0.55f, l)
        val hi = smooth(0.45f, 1f, l)
        val bl = 1f - smooth(0f, 0.25f, l)
        val wh = smooth(0.75f, 1f, l)
        val d = shadows * 0.22f * sh + highlights * 0.22f * hi + blacks * 0.12f * bl + whites * 0.12f * wh
        r += d; g += d; b += d

        // Contrast around mid grey, as a gentle S-curve.
        if (contrast != 0f) {
            r = curve(r); g = curve(g); b = curve(b)
        }

        // Split toning.
        l = luma(r, g, b).coerceIn(0f, 1f)
        if (shadowTone > 0f) {
            val (tr, tg, tb) = hueColor(shadowHue)
            val w = shadowTone * 0.18f * (1f - smooth(0f, 0.6f, l))
            r += (tr - 0.5f) * w; g += (tg - 0.5f) * w; b += (tb - 0.5f) * w
        }
        if (highlightTone > 0f) {
            val (tr, tg, tb) = hueColor(highlightHue)
            val w = highlightTone * 0.18f * smooth(0.4f, 1f, l)
            r += (tr - 0.5f) * w; g += (tg - 0.5f) * w; b += (tb - 0.5f) * w
        }

        // Saturation and vibrance (vibrance protects colours that are already strong).
        l = luma(r, g, b)
        val sat = max(r, max(g, b)) - min(r, min(g, b))
        val s = ((1f + saturation) * (1f + vibrance * (1f - sat.coerceIn(0f, 1f)))).coerceAtLeast(0f)
        r = l + (r - l) * s; g = l + (g - l) * s; b = l + (b - l) * s

        // Fade: lifted blacks, softly compressed whites.
        if (fade > 0f) {
            val lift = fade * 0.14f
            r = lift + r * (1f - lift * 1.3f); g = lift + g * (1f - lift * 1.3f); b = lift + b * (1f - lift * 1.3f)
        }

        c[0] = r.coerceIn(0f, 1f); c[1] = g.coerceIn(0f, 1f); c[2] = b.coerceIn(0f, 1f)
    }

    private fun curve(x: Float): Float {
        val k = contrast
        val v = x.coerceIn(0f, 1f)
        val s = v * v * (3f - 2f * v)
        return if (k >= 0f) v + (s - v) * k * 1.4f else v + (0.5f - v) * (-k) * 0.6f
    }

    /** Bakes the grade into an N×N×N cube indexed [r][g][b], ARGB values. */
    fun cube(n: Int = 33): Array<Array<IntArray>> {
        val c = FloatArray(3)
        return Array(n) { ri ->
            Array(n) { gi ->
                IntArray(n) { bi ->
                    c[0] = ri / (n - 1f); c[1] = gi / (n - 1f); c[2] = bi / (n - 1f)
                    apply(c)
                    (0xFF shl 24) or ((c[0] * 255f + 0.5f).toInt() shl 16) or
                        ((c[1] * 255f + 0.5f).toInt() shl 8) or (c[2] * 255f + 0.5f).toInt()
                }
            }
        }
    }

    companion object {
        private fun lin(v: Float) = v.coerceAtLeast(0f).pow(2.2f)
        private fun gam(v: Float) = v.coerceAtLeast(0f).pow(1f / 2.2f)
        private fun luma(r: Float, g: Float, b: Float) = 0.2126f * r + 0.7152f * g + 0.0722f * b
        private fun smooth(e0: Float, e1: Float, x: Float): Float {
            val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        /** Fully saturated colour for a hue, components 0..1. */
        fun hueColor(h: Float): Triple<Float, Float, Float> {
            val c = android.graphics.Color.HSVToColor(floatArrayOf(((h % 360f) + 360f) % 360f, 1f, 1f))
            return Triple(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)
        }
    }
}

/** Fast lookup-table application for bitmaps (trilinear interpolation). */
class GradeLut(grade: Grade, private val n: Int = 33) {
    private val r = FloatArray(n * n * n)
    private val g = FloatArray(n * n * n)
    private val b = FloatArray(n * n * n)

    init {
        val c = FloatArray(3)
        var i = 0
        for (ri in 0 until n) for (gi in 0 until n) for (bi in 0 until n) {
            c[0] = ri / (n - 1f); c[1] = gi / (n - 1f); c[2] = bi / (n - 1f)
            grade.apply(c)
            r[i] = c[0]; g[i] = c[1]; b[i] = c[2]
            i++
        }
    }

    /** Grades [src] into a new bitmap. */
    fun apply(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val row = IntArray(w)
        val s = (n - 1) / 255f
        val nn = n * n
        for (y in 0 until h) {
            src.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val p = row[x]
                val fr = ((p shr 16) and 0xFF) * s
                val fg = ((p shr 8) and 0xFF) * s
                val fb = (p and 0xFF) * s
                val r0 = fr.toInt().coerceAtMost(n - 2); val g0 = fg.toInt().coerceAtMost(n - 2); val b0 = fb.toInt().coerceAtMost(n - 2)
                val dr = fr - r0; val dg = fg - g0; val db = fb - b0
                val i000 = r0 * nn + g0 * n + b0
                val i100 = i000 + nn; val i010 = i000 + n; val i001 = i000 + 1
                val i110 = i100 + n; val i101 = i100 + 1; val i011 = i010 + 1; val i111 = i110 + 1
                fun lerp3(t: FloatArray): Float {
                    val c00 = t[i000] + (t[i100] - t[i000]) * dr
                    val c01 = t[i001] + (t[i101] - t[i001]) * dr
                    val c10 = t[i010] + (t[i110] - t[i010]) * dr
                    val c11 = t[i011] + (t[i111] - t[i011]) * dr
                    val c0 = c00 + (c10 - c00) * dg
                    val c1 = c01 + (c11 - c01) * dg
                    return c0 + (c1 - c0) * db
                }
                val outR = (lerp3(r) * 255f + 0.5f).toInt().coerceIn(0, 255)
                val outG = (lerp3(g) * 255f + 0.5f).toInt().coerceIn(0, 255)
                val outB = (lerp3(b) * 255f + 0.5f).toInt().coerceIn(0, 255)
                row[x] = (0xFF shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
            out.setPixels(row, 0, w, 0, y, w, 1)
        }
        return out
    }
}

/** Favourites, kept by the app (works for every file and Android version). */
object Favorites {
    private const val PREFS = "procam"
    private const val KEY = "favorites"

    fun all(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun toggle(context: Context, uri: Uri): Boolean {
        val set = all(context).toMutableSet()
        val key = uri.toString()
        val now = if (key in set) { set.remove(key); false } else { set.add(key); true }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, set).apply()
        return now
    }

    fun remove(context: Context, uri: Uri) {
        val set = all(context).toMutableSet()
        if (set.remove(uri.toString())) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, set).apply()
        }
    }
}

/** Star ratings (0–5) and pick / reject flags for culling a shoot in the gallery. */
object Ratings {
    data class Mark(val stars: Int = 0, val flag: Int = 0)   // flag: 1 pick, -1 reject
    private const val PREFS = "procam_ratings"

    fun all(context: Context): Map<String, Mark> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.all.mapNotNull { (k, v) ->
            val s = v as? String ?: return@mapNotNull null
            val parts = s.split(',')
            k to Mark(parts.getOrNull(0)?.toIntOrNull() ?: 0, parts.getOrNull(1)?.toIntOrNull() ?: 0)
        }.toMap()
    }

    fun set(context: Context, uri: Uri, m: Mark) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (m.stars == 0 && m.flag == 0) e.remove(uri.toString()) else e.putString(uri.toString(), "${m.stars},${m.flag}")
        e.apply()
    }
}

/**
 * Gallery grade → Detail: clarity (midtone local contrast), texture (fine detail), dehaze,
 * sharpening and vignette, worked on the picture's brightness. Own code.
 */
object GradeDetail {
    private fun blur(a: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        if (r < 1) return a.copyOf()
        var cur = a.copyOf(); val t = FloatArray(a.size)
        repeat(2) {
            for (y in 0 until h) {
                var acc = 0f; val o = y * w
                for (x in -r..r) acc += cur[o + x.coerceIn(0, w - 1)]
                for (x in 0 until w) { t[o + x] = acc / (2 * r + 1); acc += cur[o + minOf(x + r + 1, w - 1)] - cur[o + maxOf(x - r, 0)] }
            }
            for (x in 0 until w) {
                var acc = 0f
                for (y in -r..r) acc += t[y.coerceIn(0, h - 1) * w + x]
                for (y in 0 until h) { cur[y * w + x] = acc / (2 * r + 1); acc += t[minOf(y + r + 1, h - 1) * w + x] - t[maxOf(y - r, 0) * w + x] }
            }
        }
        return cur
    }

    fun apply(src: Bitmap, g: Grade): Bitmap {
        if (!g.hasDetail) return src
        val w = src.width; val h = src.height; val n = w * h
        val px = IntArray(n); src.getPixels(px, 0, w, 0, 0, w, h)
        val rr = FloatArray(n); val gg = FloatArray(n); val bb = FloatArray(n)
        for (i in 0 until n) { val c = px[i]; rr[i] = ((c shr 16) and 255) / 255f; gg[i] = ((c shr 8) and 255) / 255f; bb[i] = (c and 255) / 255f }
        val long = maxOf(w, h)
        // Dehaze: take out (or add) the veil, estimated from the darkest channel nearby.
        if (g.dehaze != 0f) {
            if (g.dehaze > 0f) {
                val dark = blur(FloatArray(n) { minOf(rr[it], gg[it], bb[it]) }, w, h, maxOf(2, long / 40))
                for (i in 0 until n) {
                    val k = (dark[i] * g.dehaze * 0.85f).coerceAtMost(0.9f)
                    rr[i] = (rr[i] - k) / (1f - k); gg[i] = (gg[i] - k) / (1f - k); bb[i] = (bb[i] - k) / (1f - k)
                }
            } else {
                val a = -g.dehaze * 0.5f
                for (i in 0 until n) { rr[i] += (0.82f - rr[i]) * a; gg[i] += (0.84f - gg[i]) * a; bb[i] += (0.88f - bb[i]) * a }
            }
        }
        val l = FloatArray(n) { 0.2126f * rr[it] + 0.7152f * gg[it] + 0.0722f * bb[it] }
        val delta = FloatArray(n)
        // Clarity: broad local contrast, strongest in the midtones.
        if (g.clarity != 0f) {
            val lo = blur(l, w, h, maxOf(2, long / 60))
            for (i in 0 until n) { val m = 1f - kotlin.math.abs(l[i] - 0.5f) * 1.6f; delta[i] += (l[i] - lo[i]) * g.clarity * 1.2f * m.coerceIn(0f, 1f) }
        }
        // Texture: fine detail (skin, foliage, fabric) without halos.
        if (g.texture != 0f) {
            val lo = blur(l, w, h, maxOf(1, long / 400))
            for (i in 0 until n) delta[i] += (l[i] - lo[i]) * g.texture * 1.4f
        }
        // Sharpening: a small unsharp mask.
        if (g.sharpen > 0f) {
            val lo = blur(l, w, h, 1)
            for (i in 0 until n) delta[i] += (l[i] - lo[i]) * g.sharpen * 1.6f
        }
        val cx = w / 2f; val cy = h / 2f; val rmax = kotlin.math.sqrt(cx * cx + cy * cy)
        for (i in 0 until n) {
            var r = rr[i] + delta[i]; var gr = gg[i] + delta[i]; var b = bb[i] + delta[i]
            if (g.vignette != 0f) {
                val dx = (i % w - cx) / rmax; val dy = (i / w - cy) / rmax
                val d = (dx * dx + dy * dy)
                val f = if (g.vignette < 0f) 1f + g.vignette * d * 0.9f else 1f + g.vignette * d * 0.6f
                if (g.vignette < 0f) { r *= f; gr *= f; b *= f } else { r += (1f - r) * (f - 1f); gr += (1f - gr) * (f - 1f); b += (1f - b) * (f - 1f) }
            }
            px[i] = (px[i] and (0xFF shl 24)) or ((r.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 16) or ((gr.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 8) or (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}
