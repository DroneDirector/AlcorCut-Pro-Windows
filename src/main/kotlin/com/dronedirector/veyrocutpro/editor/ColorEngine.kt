package com.dronedirector.veyrocutpro.editor

import com.dronedirector.veyrocutpro.camera.Filter
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** How the source was recorded; log footage is converted to normal-looking Rec.709 first. */
enum class InputColor(val label: String, val detail: String) {
    REC709("Rec.709", "Normal video / photos"),
    FLAT("Flat", "AlcorCut Pro flat profile"),
    DLOG("D-Log", "DJI D-Log"),
    DLOG_M("D-Log M", "DJI D-Log M (approximate)"),
    SLOG3("S-Log3", "Sony S-Log3"),
    VLOG("V-Log", "Panasonic V-Log"),
    LOGC3("LogC3", "ARRI LogC3 (EI 800)"),
    HLG("HLG", "Hybrid Log-Gamma");

    /** Code value 0..1 → scene-linear light (0.18 = mid grey). */
    fun toLinear(x: Float): Float = when (this) {
        REC709 -> x
        FLAT -> ((7f.pow((x - 0.04f) / 0.9f) - 1f) / 6f).coerceAtLeast(0f)
        DLOG -> if (x <= 0.14f) (x - 0.0929f) / 6.025f else (10f.pow(3.89616f * x - 2.27752f) - 0.0108f) / 0.9892f
        DLOG_M -> (2f.pow((x - 0.09f) * 4f / 0.75f) - 1f) / 15f
        SLOG3 -> {
            val cv = x * 1023f
            if (cv >= 171.2102946929f) 10f.pow((cv - 420f) / 261.5f) * 0.19f - 0.01f
            else (cv - 95f) * 0.01125f / (171.2102946929f - 95f)
        }
        VLOG -> if (x < 0.181f) (x - 0.125f) / 5.6f else 10f.pow((x - 0.598206f) / 0.241514f) - 0.00873f
        LOGC3 -> if (x > 5.367655f * 0.010591f + 0.092809f) (10f.pow((x - 0.385537f) / 0.247190f) - 0.052272f) / 5.555556f
        else (x - 0.092809f) / 5.367655f
        HLG -> if (x <= 0.5f) x * x / 3f else (exp((x - 0.55991073f) / 0.17883277f) + 0.28466892f) / 12f
    }

    val isLog: Boolean get() = this != REC709
}

/** One colour wheel (lift, gamma or gain): a tint direction plus a brightness offset. */
data class Wheel(val hue: Float = 0f, val amount: Float = 0f, val luma: Float = 0f) {
    /** Per-channel offsets, roughly ±0.25. */
    fun offsets(): FloatArray {
        val (r, g, b) = hueRgb(hue)
        val m = (r + g + b) / 3f
        return floatArrayOf(luma * 0.25f + (r - m) * amount * 0.3f, luma * 0.25f + (g - m) * amount * 0.3f, luma * 0.25f + (b - m) * amount * 0.3f)
    }
    val isZero get() = amount == 0f && luma == 0f
}

/** One colour of the HSL mixer. */
data class HslBand(val hue: Float = 0f, val sat: Float = 0f, val lum: Float = 0f)

val HSL_CENTERS = floatArrayOf(0f, 30f, 60f, 120f, 180f, 225f, 275f, 320f)
val HSL_NAMES = listOf("Red", "Orange", "Yellow", "Green", "Aqua", "Blue", "Purple", "Magenta")

/**
 * Full colour grade used by the photo and video editors. Everything here is a pure colour
 * transform, so it is baked into one 3D LUT (CPU preview, photo export and GPU video export).
 */
data class ProGrade(
    val input: InputColor = InputColor.REC709,
    val exposure: Float = 0f,          // stops
    val contrast: Float = 0f,          // -1..1
    val pivot: Float = 0.435f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val dehaze: Float = 0f,
    val fade: Float = 0f,
    // Parametric tone curve regions
    val curveHighlights: Float = 0f,
    val curveLights: Float = 0f,
    val curveDarks: Float = 0f,
    val curveShadows: Float = 0f,
    // Primary wheels
    val lift: Wheel = Wheel(),
    val gamma: Wheel = Wheel(),
    val gain: Wheel = Wheel(),
    // Colour grading: shadow / midtone / highlight tints
    val gradeShadows: Wheel = Wheel(200f),
    val gradeMids: Wheel = Wheel(40f),
    val gradeHighlights: Wheel = Wheel(40f),
    val hsl: List<HslBand> = List(8) { HslBand() },
    val look: Filter = Filter.NONE,
    val lookAmount: Float = 1f,
    /** Your own .cube LUT (id in [LutLibrary]), its strength, and whether it is the input transform. */
    val lut: String? = null,
    val lutAmount: Float = 1f,
    val lutInput: Boolean = false,
    /** Point curves: x, y pairs (0..1) for all channels, red, green and blue; null = straight. */
    val curveRgb: List<Float>? = null,
    val curveR: List<Float>? = null,
    val curveG: List<Float>? = null,
    val curveB: List<Float>? = null,
    // Newer settings: every default is 0 / null, so projects and presets saved before they existed look the same.
    /** Colour grading: global tint, blending offset (−0.5..0.5 around the usual 50) and balance (−1..1). */
    val gradeGlobal: Wheel? = null,
    val gradeBlend: Float = 0f,
    val gradeBalance: Float = 0f,
    /** Point Color: picked colours with their own hue / saturation / brightness shifts. */
    val pointColors: List<PointColor>? = null,
    /** Camera calibration (primaries and shadow tint). */
    val calib: Calibration? = null,
    /** Colour chart calibration: a 3 × 3 matrix (linear light, row by row) measured from a photographed 24-patch chart. */
    val chart: List<Float>? = null,
) {
    val isIdentity: Boolean get() = this == ProGrade()

    fun apply(c: FloatArray) {
        var r = c[0]; var g = c[1]; var b = c[2]
        val userLut = if (lut.isNullOrEmpty()) null else LutLibrary.get(lut)

        // 1. Input transform: your LUT (e.g. a camera maker's log → Rec.709 LUT), or the built-in
        //    log → scene linear → Rec.709 with a soft highlight roll-off.
        if (userLut != null && lutInput) {
            val o = floatArrayOf(r, g, b); userLut.apply(o)
            r += (o[0] - r) * lutAmount; g += (o[1] - g) * lutAmount; b += (o[2] - b) * lutAmount
        } else if (input.isLog) {
            r = display(input.toLinear(r)); g = display(input.toLinear(g)); b = display(input.toLinear(b))
        }

        // 2. Exposure and white balance in linear light.
        var lr = lin(r); var lg = lin(g); var lb = lin(b)
        val cm = chart
        if (cm != null && cm.size == 9) {
            val nr = cm[0] * lr + cm[1] * lg + cm[2] * lb; val ng = cm[3] * lr + cm[4] * lg + cm[5] * lb; val nb = cm[6] * lr + cm[7] * lg + cm[8] * lb
            lr = nr.coerceAtLeast(0f); lg = ng.coerceAtLeast(0f); lb = nb.coerceAtLeast(0f)
        }
        if (exposure != 0f) { val k = 2f.pow(exposure); lr *= k; lg *= k; lb *= k }
        if (temperature != 0f || tint != 0f) {
            lr *= 1f + temperature * 0.25f
            lb *= 1f - temperature * 0.25f
            lg *= 1f - tint * 0.18f
        }
        val calib = calib
        if (calib != null && !calib.isZero) {
            val m = calib.matrix()
            val nr = m[0] * lr + m[1] * lg + m[2] * lb; val ng = m[3] * lr + m[4] * lg + m[5] * lb; val nb = m[6] * lr + m[7] * lg + m[8] * lb
            lr = nr; lg = ng; lb = nb
            if (calib.shadowTint != 0f) {
                val sl = 1f - smooth(0f, 0.18f, luma(lr, lg, lb))
                lg *= 1f - calib.shadowTint * 0.25f * sl
            }
        }
        r = gam(lr); g = gam(lg); b = gam(lb)

        // 3. Lift / gamma / gain.
        if (!lift.isZero) { val o = lift.offsets(); r += o[0] * (1f - r); g += o[1] * (1f - g); b += o[2] * (1f - b) }
        if (!gamma.isZero) {
            val o = gamma.offsets()
            r = r.coerceAtLeast(0f).pow(1f / (1f + o[0] * 2f)); g = g.coerceAtLeast(0f).pow(1f / (1f + o[1] * 2f)); b = b.coerceAtLeast(0f).pow(1f / (1f + o[2] * 2f))
        }
        if (!gain.isZero) { val o = gain.offsets(); r *= 1f + o[0] * 2f; g *= 1f + o[1] * 2f; b *= 1f + o[2] * 2f }

        // 4. Dehaze: remove a grey veil.
        if (dehaze != 0f) {
            val k = dehaze * 0.12f
            if (k > 0f) { r = (r - k) / (1f - k); g = (g - k) / (1f - k); b = (b - k) / (1f - k) }
            else { r = -k + r * (1f + k); g = -k + g * (1f + k); b = -k + b * (1f + k) }
        }

        // 5. Tone: contrast, ranges, parametric curve.
        if (contrast != 0f) { r = contrastCurve(r); g = contrastCurve(g); b = contrastCurve(b) }
        var l = luma(r, g, b)
        val d = shadows * 0.22f * (1f - smooth(0f, 0.55f, l)) + highlights * 0.22f * smooth(0.45f, 1f, l) +
            blacks * 0.12f * (1f - smooth(0f, 0.25f, l)) + whites * 0.12f * smooth(0.75f, 1f, l) +
            // Parametric curve: four wide, overlapping regions (a region curve).
            curveShadows * 0.26f * bump(l, 0.10f, 0.26f) + curveDarks * 0.26f * bump(l, 0.34f, 0.28f) +
            curveLights * 0.26f * bump(l, 0.64f, 0.30f) + curveHighlights * 0.26f * bump(l.coerceAtMost(1f), 0.90f, 0.32f)
        r += d; g += d; b += d

        // 5b. Point curves (all channels, then red / green / blue).
        curveRgb?.let { pts -> r = PointCurve.eval(pts, r); g = PointCurve.eval(pts, g); b = PointCurve.eval(pts, b) }
        curveR?.let { r = PointCurve.eval(it, r) }
        curveG?.let { g = PointCurve.eval(it, g) }
        curveB?.let { b = PointCurve.eval(it, b) }

        // 6. Colour grading tints by tonal range.
        l = luma(r, g, b).coerceIn(0f, 1f)
        // Blending (overlap of the ranges) and balance (where shadows end and highlights begin).
        val split = (0.5f + gradeBalance * 0.3f).coerceIn(0.15f, 0.85f)
        val bt = ((0.5f + gradeBlend) * 2f).coerceIn(0.02f, 2f)
        val hs = 1f - split
        val sE0 = split * (1f - bt); val sE1 = maxOf(split * (0.5f + 0.5f * bt), sE0 + 0.01f)
        val hE0 = 1f - hs * (0.5f + 0.5f * bt); val hE1 = maxOf(1f - hs * (1f - bt), hE0 + 0.01f)
        val mW = (0.5f * (0.5f + 0.5f * bt)).coerceAtLeast(0.02f)
        if (!gradeShadows.isZero) tintRange(gradeShadows, 1f - smooth(sE0, sE1, l)).let { r += it[0]; g += it[1]; b += it[2] }
        if (!gradeMids.isZero) tintRange(gradeMids, 1f - abs(l - split) / mW).let { r += it[0]; g += it[1]; b += it[2] }
        if (!gradeHighlights.isZero) tintRange(gradeHighlights, smooth(hE0, hE1, l)).let { r += it[0]; g += it[1]; b += it[2] }
        val gGlobal = gradeGlobal
        if (gGlobal != null && !gGlobal.isZero) tintRange(gGlobal, 1f).let { r += it[0]; g += it[1]; b += it[2] }

        // 7. HSL mixer.
        if (hsl.any { it.hue != 0f || it.sat != 0f || it.lum != 0f }) {
            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV((r.coerceIn(0f, 1f) * 255).toInt(), (g.coerceIn(0f, 1f) * 255).toInt(), (b.coerceIn(0f, 1f) * 255).toInt(), hsv)
            var dh = 0f; var ds = 0f; var dl = 0f
            for (i in 0 until 8) {
                val band = hsl[i]
                if (band.hue == 0f && band.sat == 0f && band.lum == 0f) continue
                val dist = hueDistance(hsv[0], HSL_CENTERS[i])
                val w = (1f - dist / 45f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) } * hsv[1].coerceAtMost(1f)
                dh += band.hue * 30f * w; ds += band.sat * w; dl += band.lum * w
            }
            if (dh != 0f || ds != 0f) {
                val h2 = ((hsv[0] + dh) % 360f + 360f) % 360f
                val s2 = (hsv[1] * (1f + ds)).coerceIn(0f, 1f)
                val col = android.graphics.Color.HSVToColor(floatArrayOf(h2, s2, hsv[2]))
                // keep the fractional precision of the value channel
                val k = if (hsv[2] > 0f) 1f else 1f
                r = ((col shr 16) and 0xFF) / 255f * k; g = ((col shr 8) and 0xFF) / 255f * k; b = (col and 0xFF) / 255f * k
            }
            if (dl != 0f) { val m = dl * 0.25f; r += m; g += m; b += m }
        }

        // 7b. Point Color.
        pointColors?.takeIf { it.isNotEmpty() }?.let { pcs ->
            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV((r.coerceIn(0f, 1f) * 255).toInt(), (g.coerceIn(0f, 1f) * 255).toInt(), (b.coerceIn(0f, 1f) * 255).toInt(), hsv)
            var dh = 0f; var ds = 0f; var dl = 0f
            for (pc in pcs) {
                val hueW = (1f - hueDistance(hsv[0], pc.hue) / (10f + pc.range * 70f)).coerceIn(0f, 1f)
                val satW = (1f - abs(hsv[1] - pc.sat) / (0.25f + pc.range * 0.75f)).coerceIn(0f, 1f)
                val lumW = (1f - abs(hsv[2] - pc.lum) / (0.35f + pc.range * 0.65f)).coerceIn(0f, 1f)
                val w = (hueW * satW * lumW).let { it * it * (3f - 2f * it) }
                dh += pc.dHue * 40f * w; ds += pc.dSat * w; dl += pc.dLum * w
            }
            if (dh != 0f || ds != 0f) {
                val col = android.graphics.Color.HSVToColor(floatArrayOf((((hsv[0] + dh) % 360f) + 360f) % 360f, (hsv[1] * (1f + ds)).coerceIn(0f, 1f), hsv[2]))
                r = ((col shr 16) and 0xFF) / 255f; g = ((col shr 8) and 0xFF) / 255f; b = (col and 0xFF) / 255f
            }
            if (dl != 0f) { val m = dl * 0.3f; r += m; g += m; b += m }
        }

        // 8. Saturation and vibrance.
        l = luma(r, g, b)
        val sat = max(r, max(g, b)) - min(r, min(g, b))
        val s = ((1f + saturation) * (1f + vibrance * (1f - sat.coerceIn(0f, 1f))) * (1f + dehaze * 0.15f)).coerceAtLeast(0f)
        r = l + (r - l) * s; g = l + (g - l) * s; b = l + (b - l) * s

        // 9. Look, blended by strength.
        if (look != Filter.NONE && lookAmount > 0f) {
            val m = look.matrix
            val cr = r.coerceIn(0f, 1f); val cg = g.coerceIn(0f, 1f); val cb = b.coerceIn(0f, 1f)
            val t = floatArrayOf(
                (m[0] * cr + m[1] * cg + m[2] * cb + m[4] / 255f).coerceIn(0f, 1f),
                (m[5] * cr + m[6] * cg + m[7] * cb + m[9] / 255f).coerceIn(0f, 1f),
                (m[10] * cr + m[11] * cg + m[12] * cb + m[14] / 255f).coerceIn(0f, 1f),
            )
            look.look?.apply(t)
            r += (t[0] - r) * lookAmount; g += (t[1] - g) * lookAmount; b += (t[2] - b) * lookAmount
        }

        // 9b. Your LUT as a creative look.
        if (userLut != null && !lutInput && lutAmount > 0f) {
            val o = floatArrayOf(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f)); userLut.apply(o)
            r += (o[0] - r) * lutAmount; g += (o[1] - g) * lutAmount; b += (o[2] - b) * lutAmount
        }

        // 10. Fade.
        if (fade > 0f) {
            val lift = fade * 0.14f
            r = lift + r * (1f - lift * 1.3f); g = lift + g * (1f - lift * 1.3f); b = lift + b * (1f - lift * 1.3f)
        }

        c[0] = r.coerceIn(0f, 1f); c[1] = g.coerceIn(0f, 1f); c[2] = b.coerceIn(0f, 1f)
    }

    private fun contrastCurve(x: Float): Float {
        val v = x.coerceIn(0f, 1f)
        return if (contrast >= 0f) {
            // Sigmoid around the pivot.
            val k = 1f + contrast * 3f
            val p = pivot
            if (v < p) p * (v / p).pow(k) else 1f - (1f - p) * ((1f - v) / (1f - p)).pow(k)
        } else v + (pivot - v) * (-contrast) * 0.6f
    }

    private fun tintRange(w: Wheel, weight: Float): FloatArray {
        val o = w.offsets()
        val k = weight.coerceIn(0f, 1f) * 0.6f
        return floatArrayOf(o[0] * k, o[1] * k, o[2] * k)
    }

    /** Bakes the grade into an N×N×N cube indexed [r][g][b] (ARGB). */
    fun cube(n: Int = 33): Array<Array<IntArray>> {
        val c = FloatArray(3)
        return Array(n) { ri -> Array(n) { gi -> IntArray(n) { bi ->
            c[0] = ri / (n - 1f); c[1] = gi / (n - 1f); c[2] = bi / (n - 1f)
            apply(c)
            (0xFF shl 24) or ((c[0] * 255f + 0.5f).toInt() shl 16) or ((c[1] * 255f + 0.5f).toInt() shl 8) or (c[2] * 255f + 0.5f).toInt()
        } } }
    }

    /** Flat float tables for fast CPU application. */
    fun tables(n: Int = 33): Array<FloatArray> {
        val r = FloatArray(n * n * n); val g = FloatArray(n * n * n); val b = FloatArray(n * n * n)
        val c = FloatArray(3)
        var i = 0
        for (ri in 0 until n) for (gi in 0 until n) for (bi in 0 until n) {
            c[0] = ri / (n - 1f); c[1] = gi / (n - 1f); c[2] = bi / (n - 1f)
            apply(c)
            r[i] = c[0]; g[i] = c[1]; b[i] = c[2]; i++
        }
        return arrayOf(r, g, b)
    }

    companion object {
        fun lin(v: Float) = v.coerceAtLeast(0f).pow(2.2f)
        fun gam(v: Float) = v.coerceAtLeast(0f).pow(1f / 2.2f)
        fun luma(r: Float, g: Float, b: Float) = 0.2126f * r + 0.7152f * g + 0.0722f * b
        fun smooth(e0: Float, e1: Float, x: Float): Float {
            val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
        private fun bump(x: Float, center: Float, width: Float): Float {
            val t = (abs(x - center) / width).coerceIn(0f, 1f)
            return 1f - t * t * (3f - 2f * t)
        }
        private fun hueDistance(a: Float, b: Float): Float { val d = abs(a - b) % 360f; return if (d > 180f) 360f - d else d }

        /** Scene-linear → Rec.709 display with a filmic shoulder (mid grey lands near 0.41). */
        fun display(linear: Float): Float {
            val x = linear.coerceAtLeast(0f) * 1.3f
            val w = 6f
            val t = x * (1f + x / (w * w)) / (1f + x)
            return if (t < 0.018f) 4.5f * t else 1.099f * t.pow(0.45f) - 0.099f
        }

        @Suppress("unused")
        private fun log10(x: Float) = ln(x) / ln(10f)
    }
}

fun hueRgb(h: Float): Triple<Float, Float, Float> {
    val c = android.graphics.Color.HSVToColor(floatArrayOf(((h % 360f) + 360f) % 360f, 1f, 1f))
    return Triple(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)
}

/** Spatial adjustments (not expressible as a LUT). */
data class Detail(
    val sharpen: Float = 0f,    // 0..1
    val denoise: Float = 0f,    // 0..1
    val clarity: Float = 0f,    // -1..1 (photo only)
    val texture: Float = 0f,    // -1..1 (photo only)
    val vignette: Float = 0f,   // -1..1 (negative darkens)
    val grain: Float = 0f,      // 0..1
    /** Image detail / local contrast for video (-1..1). */
    val localDetail: Float = 0f,
    val fx: VideoFx = VideoFx.NONE,
    val fxAmount: Float = 0.6f,
    /** AI noise reduction strength (0 = off). */
    val aiDenoise: Float = 0f,
    // Newer settings are stored as offsets from the usual defaults, so 0 = default and older
    // projects / presets (which don't have them) look exactly as before.
    /** Sharpening radius offset (radius = 1 + this, 0.5..3 px), detail offset (detail = 0.25 + this), edge masking 0..1. */
    val sharpenRadius: Float = 0f,
    val sharpenDetail: Float = 0f,
    val sharpenMasking: Float = 0f,
    /** Grain size offset (0.25 + this) and roughness offset (0.5 + this). */
    val grainSize: Float = 0f,
    val grainRough: Float = 0f,
    /** Post-crop vignette: midpoint offset (0.5 + this), roundness −1..1, feather offset (0.5 + this), highlights 0..1. */
    val vignetteMid: Float = 0f,
    val vignetteRound: Float = 0f,
    val vignetteFeather: Float = 0f,
    val vignetteHighlights: Float = 0f,
    /** Defringe (0..1). */
    val defringePurple: Float = 0f,
    val defringeGreen: Float = 0f,
    /** Colour noise reduction (0..1): smooths blotchy colour specks (e.g. blue patches in dim, warm light). */
    val colorNoise: Float = 0f,
) {
    val sharpRadius get() = 1f + sharpenRadius
    val sharpDetail get() = 0.25f + sharpenDetail
    val grainSz get() = 0.25f + grainSize
    val grainRg get() = 0.5f + grainRough
    val vigMid get() = 0.5f + vignetteMid
    val vigFeather get() = 0.5f + vignetteFeather
    val isZero get() = this == Detail()
    /** Parts the video GPU effect handles. */
    val needsVideoPass get() = sharpen != 0f || denoise != 0f || vignette != 0f || grain != 0f || localDetail != 0f || fx != VideoFx.NONE
}

enum class VideoFx(val label: String) {
    NONE("None"), MONO("Mono"), BLUR("Soft blur"), GLOW("Glow"), PIXELATE("Pixelate"), MIRROR("Mirror"),
    RGB_SPLIT("RGB split"), VHS("VHS"), POSTERIZE("Posterize"), INVERT("Invert"), DREAM("Dream"),
}

/** Smooth curve through points (x, y pairs, 0..1), monotone so it never overshoots. */
object PointCurve {
    fun eval(pts: List<Float>, x: Float): Float {
        val n = pts.size / 2
        if (n < 2) return x
        val xs = FloatArray(n) { pts[it * 2] }; val ys = FloatArray(n) { pts[it * 2 + 1] }
        val v = x.coerceIn(0f, 1f)
        if (v <= xs[0]) return ys[0] + (x - v)
        if (v >= xs[n - 1]) return ys[n - 1] + (x - v)
        var i = 0
        while (i < n - 2 && v > xs[i + 1]) i++
        val h = (xs[i + 1] - xs[i]).coerceAtLeast(1e-4f)
        fun slope(k: Int): Float {
            val dPrev = if (k > 0) (ys[k] - ys[k - 1]) / (xs[k] - xs[k - 1]).coerceAtLeast(1e-4f) else null
            val dNext = if (k < n - 1) (ys[k + 1] - ys[k]) / (xs[k + 1] - xs[k]).coerceAtLeast(1e-4f) else null
            return when {
                dPrev == null -> dNext ?: 0f
                dNext == null -> dPrev
                dPrev * dNext <= 0f -> 0f
                else -> 2f / (1f / dPrev + 1f / dNext)
            }
        }
        val t = (v - xs[i]) / h
        val m0 = slope(i) * h; val m1 = slope(i + 1) * h
        val t2 = t * t; val t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * m0 + (-2 * t3 + 3 * t2) * ys[i + 1] + (t3 - t2) * m1
    }
}
