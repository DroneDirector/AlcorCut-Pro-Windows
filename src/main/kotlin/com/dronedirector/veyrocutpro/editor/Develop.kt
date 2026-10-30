package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Camera calibration: moves the red / green / blue primaries (hue −1..1, saturation −1..1) and tints the shadows. */
data class Calibration(
    val shadowTint: Float = 0f,
    val redHue: Float = 0f, val redSat: Float = 0f,
    val greenHue: Float = 0f, val greenSat: Float = 0f,
    val blueHue: Float = 0f, val blueSat: Float = 0f,
) {
    val isZero get() = this == Calibration()

    /** 3×3 matrix for linear RGB (rows keep white as white). */
    fun matrix(): FloatArray {
        fun col(hueDeg: Float, dh: Float, ds: Float): FloatArray {
            val (r, g, b) = hueRgb(hueDeg + dh * 30f)
            val s = (1f + ds * 0.6f).coerceAtLeast(0f)
            val m = (r + g + b) / 3f
            return floatArrayOf(m + (r - m) * s, m + (g - m) * s, m + (b - m) * s)
        }
        val cr = col(0f, redHue, redSat); val cg = col(120f, greenHue, greenSat); val cb = col(240f, blueHue, blueSat)
        val m = floatArrayOf(cr[0], cg[0], cb[0], cr[1], cg[1], cb[1], cr[2], cg[2], cb[2])
        for (row in 0 until 3) {
            val s = m[row * 3] + m[row * 3 + 1] + m[row * 3 + 2]
            if (abs(s) > 1e-4f) for (k in 0 until 3) m[row * 3 + k] /= s
        }
        return m
    }
}

/**
 * Point Color: a colour picked on the photo ([hue] 0..360, [sat] 0..1, [lum] 0..1) and how far
 * around it to reach ([range] 0..1), then the shifts for that colour.
 */
data class PointColor(
    val hue: Float = 0f, val sat: Float = 0.5f, val lum: Float = 0.5f,
    val range: Float = 0.5f,
    val dHue: Float = 0f, val dSat: Float = 0f, val dLum: Float = 0f,
)

object Develop {
    // ---------------------------------------------------------------- auto tone / white balance

    /** Auto Tone: only the Light sliders (and a touch of vibrance). */
    fun autoTone(bmp: Bitmap, g: ProGrade): ProGrade {
        val a = AutoEnhance.gradeFor(bmp)
        return g.copy(exposure = a.exposure, contrast = a.contrast, highlights = a.highlights, shadows = a.shadows, whites = a.whites, blacks = a.blacks, vibrance = maxOf(g.vibrance, a.vibrance))
    }

    /** Auto white balance from the midtones. */
    fun autoWb(bmp: Bitmap, g: ProGrade): ProGrade {
        val a = AutoEnhance.gradeFor(bmp)
        return g.copy(temperature = (g.temperature + a.temperature * 2f).coerceIn(-1f, 1f), tint = (g.tint + a.tint).coerceIn(-1f, 1f))
    }

    /**
     * White-balance eyedropper: the picked spot of the *rendered* photo (0..255 RGB, averaged) should
     * become neutral. Returns new temperature and tint.
     */
    fun wbFromSample(r8: Float, g8: Float, b8: Float, t0: Float, tint0: Float): Pair<Float, Float> {
        val r = ProGrade.lin(r8 / 255f) / (1f + t0 * 0.25f)
        val b = ProGrade.lin(b8 / 255f) / (1f - t0 * 0.25f).coerceAtLeast(0.05f)
        val gg = ProGrade.lin(g8 / 255f) / (1f - tint0 * 0.18f).coerceAtLeast(0.05f)
        if (r + b < 1e-4f || gg < 1e-4f) return t0 to tint0
        val t = ((b - r) / (0.25f * (r + b))).coerceIn(-1f, 1f)
        val nr = r * (1f + t * 0.25f); val nb = b * (1f - t * 0.25f)
        val tint = ((1f - ((nr + nb) / 2f) / gg) / 0.18f).coerceIn(-1f, 1f)
        return t to tint
    }

    /** Average colour (0..255) of a small square around (x, y) in [bmp]. */
    fun sample(bmp: Bitmap, x: Int, y: Int, radius: Int = 3): FloatArray {
        var sr = 0f; var sg = 0f; var sb = 0f; var n = 0
        for (yy in (y - radius)..(y + radius)) for (xx in (x - radius)..(x + radius)) {
            if (xx !in 0 until bmp.width || yy !in 0 until bmp.height) continue
            val p = bmp.getPixel(xx, yy)
            sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF; n++
        }
        if (n == 0) return floatArrayOf(128f, 128f, 128f)
        return floatArrayOf(sr / n, sg / n, sb / n)
    }

    // ---------------------------------------------------------------- warnings and mask previews

    /** Clipping overlay: red where any channel is blown out, blue where the shadows are crushed. */
    fun clipOverlay(bmp: Bitmap): Bitmap {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val p = px[i]
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            px[i] = when {
                r >= 254 || g >= 254 || b >= 254 -> 0xE6FF2020.toInt()
                r <= 1 && g <= 1 && b <= 1 -> 0xE62060FF.toInt()
                else -> 0
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Where sharpening lands (white) with the current Masking. */
    fun sharpenMaskPreview(bmp: Bitmap, d: Detail): Bitmap {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        val m = edgeMask(px, w, h, d.sharpenMasking)
        for (i in px.indices) { val v = (m[i] * 255f).toInt().coerceIn(0, 255); px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** 0..1 per pixel: 1 on edges, 0 on smooth areas, as [masking] (0..1) rises. masking 0 = everywhere. */
    fun edgeMask(px: IntArray, w: Int, h: Int, masking: Float, ref: Int = max(w, h)): FloatArray {
        val out = FloatArray(w * h)
        if (masking <= 0.001f) { out.fill(1f); return out }
        val l = FloatArray(w * h)
        for (i in px.indices) { val p = px[i]; l[i] = (((p shr 16) and 0xFF) * 54 + ((p shr 8) and 0xFF) * 183 + (p and 0xFF) * 19) / 65280f }
        // Light blur first so noise doesn't count as an edge.
        val bl = blurF(l, w, h, max(1, ref / 1400))
        val th = masking * 0.12f
        val soft = 0.03f + masking * 0.04f
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val gx = bl[y * w + min(x + 1, w - 1)] - bl[y * w + max(x - 1, 0)]
            val gy = bl[min(y + 1, h - 1) * w + x] - bl[max(y - 1, 0) * w + x]
            val gm = sqrt(gx * gx + gy * gy)
            out[i] = ProGrade.smooth(th - soft, th + soft, gm)
        }
        return out
    }

    private fun blurF(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        if (r < 1) return src.copyOf()
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

    // ---------------------------------------------------------------- spatial develop steps

    /** Removes purple / green fringes along high-contrast edges (amounts 0..1). */
    fun defringe(px: IntArray, w: Int, h: Int, purple: Float, green: Float) {
        if (purple <= 0f && green <= 0f) return
        val m = edgeMask(px, w, h, 0.35f)
        val hsv = FloatArray(3)
        for (i in px.indices) {
            val e = m[i]; if (e < 0.05f) continue
            val p = px[i]
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            if (hsv[1] < 0.12f) continue
            val hue = hsv[0]
            val wP = if (purple > 0f) (1f - abs(hue - 290f) / 45f).coerceIn(0f, 1f) * purple else 0f
            val wG = if (green > 0f) (1f - abs(hue - 115f) / 45f).coerceIn(0f, 1f) * green else 0f
            val k = (max(wP, wG) * e).coerceIn(0f, 1f)
            if (k <= 0f) continue
            val l = (r * 54 + g * 183 + b * 19) shr 8
            px[i] = (0xFF shl 24) or (mix8(r, l, k) shl 16) or (mix8(g, l, k) shl 8) or mix8(b, l, k)
        }
    }

    private fun mix8(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt().coerceIn(0, 255)

    /** Film grain: [amount] 0..1, [size] 0..1 (grain size), [rough] 0..1 (irregular clumps). Monochrome, like film. */
    fun grain(px: IntArray, w: Int, h: Int, amount: Float, size: Float, rough: Float) {
        if (amount <= 0f) return
        val scale = max(w, h) / 1080f
        val cell = (1f + size * 3.5f) * scale.coerceAtLeast(0.35f)
        val gw = (w / cell).toInt() + 2; val gh = (h / cell).toInt() + 2
        val noise = FloatArray(gw * gh)
        var seed = 0x2545F491
        for (i in noise.indices) { seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5); noise[i] = (seed and 0xFFFF) / 65535f - 0.5f }
        val amt = amount * 34f
        var s2 = 0x1234567
        for (y in 0 until h) {
            val fy = y / cell; val y0 = fy.toInt(); val ty = fy - y0
            for (x in 0 until w) {
                val fx = x / cell; val x0 = fx.toInt(); val tx = fx - x0
                val a = noise[y0 * gw + x0]; val b = noise[y0 * gw + x0 + 1]; val c = noise[(y0 + 1) * gw + x0]; val d = noise[(y0 + 1) * gw + x0 + 1]
                var n = (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * ty
                if (rough > 0f) { s2 = s2 xor (s2 shl 13); s2 = s2 xor (s2 ushr 17); s2 = s2 xor (s2 shl 5); n += ((s2 and 0xFFFF) / 65535f - 0.5f) * rough * 0.6f }
                val i = y * w + x; val p = px[i]
                val l = ((p shr 16) and 0xFF) * 0.2126f + ((p shr 8) and 0xFF) * 0.7152f + (p and 0xFF) * 0.0722f
                // Strongest in the midtones, like real film.
                val k = n * amt * (0.45f + 0.55f * (1f - abs(l - 128f) / 128f))
                px[i] = (0xFF shl 24) or (c8(((p shr 16) and 0xFF) + k) shl 16) or (c8(((p shr 8) and 0xFF) + k) shl 8) or c8((p and 0xFF) + k)
            }
        }
    }

    /**
     * Post-crop vignette: [amount] −1..1 (negative darkens), [mid] 0..1 how far in it starts,
     * [round] −1..1 (−1 = follows the frame, +1 = a circle), [feather] 0..1, [highlights] 0..1 keeps
     * bright parts bright when darkening.
     */
    fun vignette(px: IntArray, w: Int, h: Int, amount: Float, mid: Float, round: Float, feather: Float, highlights: Float) {
        if (amount == 0f) return
        val cx = w / 2f; val cy = h / 2f
        val circ = (round + 1f) / 2f   // 0 = frame shaped, 1 = circle
        val ax = cx + (min(cx, cy) - cx) * circ; val ay = cy + (min(cx, cy) - cy) * circ
        val start = 0.15f + mid * 0.7f
        val width = (0.08f + feather * 0.9f)
        val pw = 2f + (1f - circ) * 4f   // superellipse exponent: more rectangular towards the frame
        for (y in 0 until h) {
            val dy = abs(y - cy) / ay
            for (x in 0 until w) {
                val dx = abs(x - cx) / ax
                val dist = (dx.pow(pw) + dy.pow(pw)).pow(1f / pw)
                val f = ProGrade.smooth(start, start + width, dist)
                if (f <= 0f) continue
                val i = y * w + x; val p = px[i]
                val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                var k = 1f + amount * f
                if (amount < 0f && highlights > 0f) {
                    val l = (r * 54 + g * 183 + b * 19) shr 8
                    k += (1f - k) * highlights * ProGrade.smooth(150f, 255f, l.toFloat())
                }
                px[i] = (0xFF shl 24) or (c8(r * k) shl 16) or (c8(g * k) shl 8) or c8(b * k)
            }
        }
    }

    private fun c8(v: Float) = v.toInt().coerceIn(0, 255)

    // ---------------------------------------------------------------- presets with an amount

    /** [a] → [b] by [t] for every number in two grades (lists / wheels too); other values come from [b] when t > 0.5. */
    fun lerpGrade(a: ProGrade, b: ProGrade, t: Float): ProGrade = lerpAny(a, b, t) as ProGrade
    fun lerpDetail(a: Detail, b: Detail, t: Float): Detail = lerpAny(a, b, t) as Detail

    /** t may go above 1 (numbers keep moving the same way, e.g. a preset at 150 %). */
    private fun lerpAny(a: Any?, b: Any?, t: Float): Any? {
        if (t == 1f) return b
        if (t <= 0f) return a
        if (a == null || b == null) return if (t > 0.5f) b else a
        if (a is Float && b is Float) return a + (b - a) * t
        if (a is List<*> && b is List<*>) {
            if (a.size != b.size) return if (t > 0.5f) b else a
            return a.indices.map { lerpAny(a[it], b[it], t) }
        }
        if (a.javaClass != b.javaClass || !a.javaClass.name.startsWith("com.dronedirector") || a.javaClass.isEnum) return if (t > 0.5f) b else a
        // Data class: copy a, set each field.
        val gson = ProjectStore.gson
        val copy = gson.fromJson(gson.toJson(a), a.javaClass)
        var cls: Class<*>? = a.javaClass
        while (cls != null && cls != Any::class.java) {
            for (f in cls.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
                try { f.isAccessible = true; f.set(copy, lerpAny(f.get(a), f.get(b), t)) } catch (_: Throwable) {}
            }
            cls = cls.superclass
        }
        return copy
    }
}

/** What the develop panels need from the photo editor's workspace. */
class DevUi(
    val picking: Int, val setPicking: (Int) -> Unit,
    val clipWarn: Boolean, val setClipWarn: (Boolean) -> Unit,
    val sharpMask: Boolean, val setSharpMask: (Boolean) -> Unit,
    val autoTone: () -> Unit, val autoWb: () -> Unit,
    val importXmp: () -> Unit, val exportXmp: () -> Unit,
    /** Lens Blur: work out the depth map (busy text while it runs, or null). */
    val analyseDepth: () -> Unit = {}, val depthBusy: Boolean = false,
    /** Upright (mode, or −1 to undo); busy while it works. */
    val upright: (Int) -> Unit = {}, val uprightBusy: Boolean = false,
    /** Targeted adjustment in the HSL mixer: 0 hue, 1 saturation, 2 luminance. */
    val tatMode: Int = 1, val setTatMode: (Int) -> Unit = {},
    /** Smart crop to a shape (width / height), keeping the subject in; busy while it looks. */
    val smartCrop: (Float) -> Unit = {}, val smartBusy: Boolean = false,
    /** Adaptive presets: run one (AI masks with their adjustments); busy text while it works. */
    val adaptive: (AdaptivePreset) -> Unit = {}, val adaptiveBusy: String? = null,
)
