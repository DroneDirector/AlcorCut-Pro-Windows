package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Crop & rotate settings for the photo editor. */
data class Geometry(
    /** Width / height of the crop, or 0 = free (keep image shape). */
    val aspect: Float = 0f,
    val rotate90: Int = 0,         // quarter turns
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val straighten: Float = 0f,    // degrees, -45..45
    val zoom: Float = 1f,          // 1..4, crop tighter
    val offsetX: Float = 0f,       // -1..1
    val offsetY: Float = 0f,
    /** Perspective (keystone): vertical and horizontal, -1..1. */
    val vertical: Float = 0f,
    val horizontal: Float = 0f,
) {
    val isIdentity get() = this == Geometry()
}

object ImageOps {

    /** Applies geometry: rotation, flip, straighten (auto-zoomed to avoid empty corners), crop. */
    fun geometry(src0: Bitmap, g: Geometry): Bitmap {
        if (g.isIdentity) return src0
        val src = PhotoLocal.perspective(src0, g.vertical, g.horizontal)
        val turns = ((g.rotate90 % 4) + 4) % 4
        val rw = if (turns % 2 == 1) src.height else src.width
        val rh = if (turns % 2 == 1) src.width else src.height
        // Crop rectangle in the rotated image.
        val imgAspect = rw.toFloat() / rh
        val target = if (g.aspect > 0f) g.aspect else imgAspect
        var cw: Float; var ch: Float
        if (target > imgAspect) { cw = rw.toFloat(); ch = cw / target } else { ch = rh.toFloat(); cw = ch * target }
        // Straightening: shrink so the rotated picture still covers the crop.
        val a = Math.toRadians(abs(g.straighten).toDouble())
        val cosA = cos(a).toFloat(); val sinA = sin(a).toFloat()
        val fit = min(rw / (cw * cosA + ch * sinA), rh / (cw * sinA + ch * cosA)).coerceAtMost(1f)
        cw = cw * fit / g.zoom; ch = ch * fit / g.zoom
        val outW = cw.toInt().coerceAtLeast(1); val outH = ch.toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val m = Matrix()
        // Move source centre to origin, rotate/flip, then into the crop window (with pan).
        m.postTranslate(-src.width / 2f, -src.height / 2f)
        m.postScale(if (g.flipH) -1f else 1f, if (g.flipV) -1f else 1f)
        m.postRotate(turns * 90f + g.straighten)
        val maxPanX = (rw - cw) / 2f; val maxPanY = (rh - ch) / 2f
        m.postTranslate(outW / 2f - g.offsetX * maxPanX, outH / 2f - g.offsetY * maxPanY)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        if (src !== src0) src.recycle()
        return out
    }

    /** Colour LUT (from [ProGrade.tables]) applied with trilinear interpolation. */
    fun applyLut(px: IntArray, t: Array<FloatArray>, n: Int = 33) {
        val tr = t[0]; val tg = t[1]; val tb = t[2]
        val s = (n - 1) / 255f
        val nn = n * n
        for (i in px.indices) {
            val p = px[i]
            val fr = ((p shr 16) and 0xFF) * s; val fg = ((p shr 8) and 0xFF) * s; val fb = (p and 0xFF) * s
            val r0 = min(fr.toInt(), n - 2); val g0 = min(fg.toInt(), n - 2); val b0 = min(fb.toInt(), n - 2)
            val dr = fr - r0; val dg = fg - g0; val db = fb - b0
            val i000 = r0 * nn + g0 * n + b0
            val i100 = i000 + nn; val i010 = i000 + n; val i001 = i000 + 1
            val i110 = i100 + n; val i101 = i100 + 1; val i011 = i010 + 1; val i111 = i110 + 1
            val outR = tri(tr, i000, i100, i010, i001, i110, i101, i011, i111, dr, dg, db)
            val outG = tri(tg, i000, i100, i010, i001, i110, i101, i011, i111, dr, dg, db)
            val outB = tri(tb, i000, i100, i010, i001, i110, i101, i011, i111, dr, dg, db)
            px[i] = (0xFF shl 24) or (to8(outR) shl 16) or (to8(outG) shl 8) or to8(outB)
        }
    }

    private fun tri(
        t: FloatArray, i000: Int, i100: Int, i010: Int, i001: Int, i110: Int, i101: Int, i011: Int, i111: Int,
        dr: Float, dg: Float, db: Float,
    ): Float {
        val c00 = t[i000] + (t[i100] - t[i000]) * dr
        val c01 = t[i001] + (t[i101] - t[i001]) * dr
        val c10 = t[i010] + (t[i110] - t[i010]) * dr
        val c11 = t[i011] + (t[i111] - t[i011]) * dr
        val c0 = c00 + (c10 - c00) * dg
        val c1 = c01 + (c11 - c01) * dg
        return c0 + (c1 - c0) * db
    }

    private fun to8(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)

    /** Separable box blur of the three channels; [radius] in pixels. */
    /**
     * Runs [op] on bands of rows of the picture: each band is copied with [margin] extra rows above
     * and below (the reach of the op's blur), processed, and only its own rows are written back —
     * after the next band has read the untouched rows it needs. Small pictures run in one go.
     */
    private fun banded(px: IntArray, w: Int, h: Int, margin: Int, op: (IntArray, Int) -> Unit) {
        val rows = max(max(64, 3_000_000 / max(1, w)), margin)
        if (h <= rows + 2 * margin) { op(px, h); return }
        var pending: IntArray? = null; var pendingY = 0
        var y0 = 0
        while (y0 < h) {
            val y1 = min(h, y0 + rows)
            val a = max(0, y0 - margin); val b = min(h, y1 + margin)
            val band = px.copyOfRange(a * w, b * w)
            op(band, b - a)
            pending?.let { System.arraycopy(it, 0, px, pendingY * w, it.size) }
            pending = band.copyOfRange((y0 - a) * w, (y1 - a) * w); pendingY = y0
            y0 = y1
        }
        pending?.let { System.arraycopy(it, 0, px, pendingY * w, it.size) }
    }

    private fun boxBlur(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
        if (radius < 1) return src.copyOf()
        val tmp = IntArray(w * h)
        val out = IntArray(w * h)
        val div = radius * 2 + 1
        // Horizontal
        for (y in 0 until h) {
            var sr = 0; var sg = 0; var sb = 0
            val row = y * w
            for (k in -radius..radius) { val p = src[row + k.coerceIn(0, w - 1)]; sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF }
            for (x in 0 until w) {
                tmp[row + x] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val pOut = src[row + (x - radius).coerceIn(0, w - 1)]
                val pIn = src[row + (x + radius + 1).coerceIn(0, w - 1)]
                sr += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
                sg += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
                sb += (pIn and 0xFF) - (pOut and 0xFF)
            }
        }
        // Vertical
        for (x in 0 until w) {
            var sr = 0; var sg = 0; var sb = 0
            for (k in -radius..radius) { val p = tmp[k.coerceIn(0, h - 1) * w + x]; sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF }
            for (y in 0 until h) {
                out[y * w + x] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val pOut = tmp[(y - radius).coerceIn(0, h - 1) * w + x]
                val pIn = tmp[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                sr += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
                sg += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
                sb += (pIn and 0xFF) - (pOut and 0xFF)
            }
        }
        return out
    }

    /**
     * Spatial adjustments. Radii scale with the picture so the preview and the full-size export
     * look the same.
     */
    /**
     * Colour noise reduction: the colour (chroma) is smoothed over a wide area while the brightness
     * keeps all its detail, strongest in the shadows where colour blotches show. Real colour edges
     * are kept (big chroma differences aren't smoothed).
     */
    private fun colourNoise(px: IntArray, w: Int, h: Int, amount: Float, scale: Float) {
        // The colour is smoothed on a small copy (at most ~1024 px across) — little memory even for
        // full-size photos — and the smooth colour is then laid back under every full-size pixel.
        val f = max(1, (max(w, h) + 1023) / 1024)
        val sw = (w + f - 1) / f; val sh = (h + f - 1) / f; val sn = sw * sh
        val cb = FloatArray(sn); val cr = FloatArray(sn); val cnt = IntArray(sn)
        for (y in 0 until h) { val so = (y / f) * sw
            for (x in 0 until w) {
                val c = px[y * w + x]; val r = ((c shr 16) and 255).toFloat(); val g = ((c shr 8) and 255).toFloat(); val b = (c and 255).toFloat()
                val l = 0.299f * r + 0.587f * g + 0.114f * b
                val k = so + x / f; cb[k] += b - l; cr[k] += r - l; cnt[k]++
            } }
        for (k in 0 until sn) if (cnt[k] > 0) { cb[k] /= cnt[k]; cr[k] /= cnt[k] }
        // Wide: the blotches in dim light are large (up to ~3 % of the picture's width).
        val rad = max(1, ((max(w, h) * (0.006f + 0.022f * amount)) / f).toInt())
        val t = FloatArray(sn)
        fun blur(a: FloatArray) {
            repeat(2) {
                for (yy in 0 until sh) { var acc = 0f; val off = yy * sw
                    for (x in -rad..rad) acc += a[off + x.coerceIn(0, sw - 1)]
                    for (x in 0 until sw) { t[off + x] = acc / (2 * rad + 1); acc += a[off + min(x + rad + 1, sw - 1)] - a[off + max(x - rad, 0)] } }
                for (x in 0 until sw) { var acc = 0f
                    for (yy in -rad..rad) acc += t[yy.coerceIn(0, sh - 1) * sw + x]
                    for (yy in 0 until sh) { a[yy * sw + x] = acc / (2 * rad + 1); acc += t[min(yy + rad + 1, sh - 1) * sw + x] - t[max(yy - rad, 0) * sw + x] } }
            }
        }
        blur(cb); blur(cr)
        val th = 25f + 90f * amount
        for (y in 0 until h) {
            val fy = ((y + 0.5f) / f - 0.5f).coerceIn(0f, sh - 1f); val y0 = min(fy.toInt(), max(sh - 2, 0)); val ay = fy - y0; val y1 = min(y0 + 1, sh - 1)
            for (x in 0 until w) {
                val fx = ((x + 0.5f) / f - 0.5f).coerceIn(0f, sw - 1f); val x0 = min(fx.toInt(), max(sw - 2, 0)); val ax = fx - x0; val x1 = min(x0 + 1, sw - 1)
                fun s(a: FloatArray) = (a[y0 * sw + x0] * (1 - ax) + a[y0 * sw + x1] * ax) * (1 - ay) + (a[y1 * sw + x0] * (1 - ax) + a[y1 * sw + x1] * ax) * ay
                val i = y * w + x
                val c = px[i]; val r0 = ((c shr 16) and 255).toFloat(); val g0 = ((c shr 8) and 255).toFloat(); val b0 = (c and 255).toFloat()
                val l = 0.299f * r0 + 0.587f * g0 + 0.114f * b0
                val pb = b0 - l; val pr = r0 - l
                val bb = s(cb); val rr = s(cr)
                val edge = (1f - (abs(pb - bb) + abs(pr - rr)) / (th * 2f)).coerceIn(0f, 1f)
                val k = (amount * 1.4f * edge * (1f - (l / 255f) * 0.45f)).coerceIn(0f, 1f)
                // Near-black: colour there is almost only noise, so it is also turned down.
                val shadow = 1f - amount * 0.7f * ((70f - l) / 70f).coerceIn(0f, 1f)
                if (k <= 0f && shadow >= 1f) continue
                val ncb = (pb + (bb - pb) * k) * shadow; val ncr = (pr + (rr - pr) * k) * shadow
                val r = l + ncr; val b = l + ncb; val g = (l - 0.299f * r - 0.114f * b) / 0.587f
                px[i] = (c and (0xFF shl 24)) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
            }
        }
    }

    fun detail(px: IntArray, w: Int, h: Int, d: Detail) {
        if (d.isZero) return
        val scale = max(w, h) / 1080f

        if (d.colorNoise > 0f) try { colourNoise(px, w, h, d.colorNoise, scale) } catch (_: OutOfMemoryError) {}
        val ref = max(w, h)
        // The blur-based steps run in bands of rows (each with enough rows around it), so a full-size
        // photo needs only a little extra memory; the result is the same as doing the whole picture at once.
        if (d.denoise > 0f) {
            // Edge-aware smoothing: blend towards a blur only where the difference is small.
            val r = max(1, (2 * scale).toInt())
            val threshold = 10f + d.denoise * 40f
            banded(px, w, h, r + 1) { band, bh ->
                val blur = boxBlur(band, w, bh, r)
                for (i in band.indices) {
                    val a = band[i]; val b = blur[i]
                    val diff = (abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) + abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) + abs((a and 0xFF) - (b and 0xFF))) / 3f
                    val wgt = (1f - diff / threshold).coerceIn(0f, 1f) * d.denoise
                    if (wgt > 0f) band[i] = mix(a, b, wgt)
                }
            }
        }
        if (d.clarity != 0f) { val r = max(2, (18 * scale).toInt()); banded(px, w, h, r + 1) { b, bh -> unsharp(b, w, bh, r, d.clarity * 0.6f, midtonesOnly = true) } }
        if (d.texture != 0f) { val r = max(1, (4 * scale).toInt()); banded(px, w, h, r + 1) { b, bh -> unsharp(b, w, bh, r, d.texture * 0.6f, midtonesOnly = false) } }
        if (d.sharpen > 0f) {
            if (d.sharpenRadius == 0f && d.sharpenDetail == 0f && d.sharpenMasking == 0f) {
                val r = max(1, (1.2f * scale).toInt()); banded(px, w, h, r + 1) { b, bh -> unsharp(b, w, bh, r, d.sharpen * 1.2f, midtonesOnly = false) }
            } else {
                val m = max(1, (d.sharpRadius * 1.2f * scale).toInt()) + max(1, ref / 1400) + 3
                banded(px, w, h, m) { b, bh -> proSharpen(b, w, bh, d, scale, ref) }
            }
        }
        Develop.defringe(px, w, h, d.defringePurple, d.defringeGreen)
        // Vignette after the crop (post-crop vignette). Untouched shape controls keep the original look.
        if (d.vignette != 0f) {
            if (d.vignetteMid == 0f && d.vignetteRound == 0f && d.vignetteFeather == 0f && d.vignetteHighlights == 0f) legacyVignette(px, w, h, d.vignette)
            else Develop.vignette(px, w, h, d.vignette, d.vigMid, d.vignetteRound, d.vigFeather, d.vignetteHighlights)
        }
        if (d.grain > 0f) {
            if (d.grainSize == 0f && d.grainRough == 0f) legacyGrain(px, d.grain)
            else Develop.grain(px, w, h, d.grain, d.grainSz, d.grainRg)
        }
    }

    private fun legacyVignette(px: IntArray, w: Int, h: Int, amount: Float) {
        val cx = w / 2f; val cy = h / 2f
        val maxD = sqrt(cx * cx + cy * cy)
        for (y in 0 until h) for (x in 0 until w) {
            val dx = (x - cx) / maxD; val dy = (y - cy) / maxD
            val dist = sqrt(dx * dx + dy * dy)
            val f = ((dist - 0.35f) / 0.65f).coerceIn(0f, 1f).let { it * it }
            val k = 1f + amount * f
            val i = y * w + x
            val p = px[i]
            px[i] = (0xFF shl 24) or (clamp8(((p shr 16) and 0xFF) * k) shl 16) or (clamp8(((p shr 8) and 0xFF) * k) shl 8) or clamp8((p and 0xFF) * k)
        }
    }

    private fun legacyGrain(px: IntArray, grain: Float) {
        val amt = grain * 28f
        var seed = 0x2545F491
        for (i in px.indices) {
            seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
            val n = ((seed and 0xFFFF) / 65535f - 0.5f) * amt
            val p = px[i]
            px[i] = (0xFF shl 24) or (clamp8(((p shr 16) and 0xFF) + n) shl 16) or (clamp8(((p shr 8) and 0xFF) + n) shl 8) or clamp8((p and 0xFF) + n)
        }
    }

    /** Sharpening with Radius, Detail (halo control) and edge Masking. */
    private fun proSharpen(px: IntArray, w: Int, h: Int, d: Detail, scale: Float, ref: Int = max(w, h)) {
        val radius = max(1, (d.sharpRadius * 1.2f * scale).toInt())
        val blur = boxBlur(px, w, h, radius)
        val mask = Develop.edgeMask(px, w, h, d.sharpenMasking, ref)
        val amount = d.sharpen * 1.2f * (0.6f + d.sharpDetail * 0.8f)
        // Low Detail damps big differences (halos); high Detail lets fine texture through fully.
        val limit = 18f + d.sharpDetail * 120f
        for (i in px.indices) {
            val k = amount * mask[i]
            if (k <= 0f) continue
            val a = px[i]; val b = blur[i]
            fun ch(sh: Int): Int {
                val av = (a shr sh) and 0xFF
                var diff = (av - ((b shr sh) and 0xFF)).toFloat()
                if (abs(diff) > limit) diff = limit * (if (diff > 0) 1f else -1f) + (diff - limit * (if (diff > 0) 1f else -1f)) * 0.3f
                return clamp8(av + diff * k)
            }
            px[i] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }

    private fun unsharp(px: IntArray, w: Int, h: Int, radius: Int, amount: Float, midtonesOnly: Boolean) {
        val blur = boxBlur(px, w, h, radius)
        for (i in px.indices) {
            val a = px[i]; val b = blur[i]
            val ar = (a shr 16) and 0xFF; val ag = (a shr 8) and 0xFF; val ab = a and 0xFF
            var k = amount
            if (midtonesOnly) {
                val l = (ar * 54 + ag * 183 + ab * 19) shr 8
                val t = 1f - abs(l - 128) / 128f
                k *= t
            }
            px[i] = (0xFF shl 24) or
                (clamp8(ar + (ar - ((b shr 16) and 0xFF)) * k) shl 16) or
                (clamp8(ag + (ag - ((b shr 8) and 0xFF)) * k) shl 8) or
                clamp8(ab + (ab - (b and 0xFF)) * k)
        }
    }

    private fun clamp8(v: Float) = v.toInt().coerceIn(0, 255)
    private fun clamp8(v: Int) = v.coerceIn(0, 255)

    private fun mix(a: Int, b: Int, t: Float): Int {
        val r = (((a shr 16) and 0xFF) * (1 - t) + ((b shr 16) and 0xFF) * t).toInt()
        val g = (((a shr 8) and 0xFF) * (1 - t) + ((b shr 8) and 0xFF) * t).toInt()
        val bl = ((a and 0xFF) * (1 - t) + (b and 0xFF) * t).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    /**
     * Photo editor render: healing and lens fixes on the original, then geometry, colour, detail
     * and the local masks.
     */
    fun renderPhoto(
        src: Bitmap, grade: ProGrade, detail: Detail, geometry: Geometry, tables: Array<FloatArray>?,
        lens: LensFix?, spots: List<Spot>?, masks: List<PhotoMask>?, aiMasks: Map<String, Bitmap> = emptyMap(),
        bg: PhotoBackground? = null,
        sky: SkySwap? = null,
        lensBlur: LensBlur? = null,
        retouch: Retouch? = null,
    ): Bitmap {
        var b = src
        val srcW = src.width; val srcH = src.height
        if (!spots.isNullOrEmpty()) b = PhotoLocal.heal(b, spots)
        if (retouch != null && retouch.liquify.isNotEmpty()) { val l = RetouchFx.liquify(b, retouch.liquify); if (b !== src && l !== b) b.recycle(); b = l }
        if (lens != null && !lens.isIdentity) { val l = PhotoLocal.lens(b, lens); if (b !== src && l !== b) b.recycle(); b = l }
        val g = geometry(b, geometry)
        if (b !== src && g !== b) b.recycle()
        val w = g.width; val h = g.height
        val px = IntArray(w * h)
        g.getPixels(px, 0, w, 0, 0, w, h)
        if (g !== src) g.recycle()
        if (tables != null) applyLut(px, tables)
        detail(px, w, h, detail)
        // AI selections are made on the uncropped photo: give them the same crop / rotation.
        val used = (masks.orEmpty().filter { it.visible }.flatMap { m -> listOfNotNull(m.maskFile) + m.subs.orEmpty().mapNotNull { it.maskFile } } +
            listOfNotNull(bg?.takeIf { it.active }?.maskFile, sky?.takeIf { it.active }?.maskFile, lensBlur?.takeIf { it.active }?.depthFile) + retouch?.files().orEmpty()).distinct()
        val placed = HashMap<String, Bitmap>()
        for (f in used) aiMasks[f]?.let { m ->
            val s = Bitmap.createScaledBitmap(m, srcW, srcH, true)
            val gm = geometry(s, geometry)
            if (gm !== s) s.recycle()
            placed[f] = gm
        }
        // Retouch: frequency separation (skin) and relight, before the local masks.
        if (retouch != null && retouch.fsOn) {
            val m = if (retouch.fsArea != 0) retouch.fsMaskFile?.let { f -> placed[f]?.let { PhotoLocal.weights(PhotoMask(type = MaskType.SUBJECT, maskFile = f), w, h, it) } } else null
            if (retouch.fsArea == 0 || m != null) RetouchFx.frequencySeparation(px, w, h, retouch, m)
        }
        if (retouch != null && retouch.relightOn) placed[retouch.depthFile]?.let { RetouchFx.relight(px, w, h, retouch, it) }
        // Lens blur (depth of field from the AI depth map), before the local masks.
        if (lensBlur != null && lensBlur.active) placed[lensBlur.depthFile]?.let { d -> LensBlurFx.apply(px, w, h, d, lensBlur) }
        if (!masks.isNullOrEmpty()) PhotoLocal.applyMasks(px, w, h, masks, placed)
        // Sky replacement.
        if (sky != null && sky.active) placed[sky.maskFile]?.let { m ->
            val wt = PhotoLocal.weights(PhotoMask(type = MaskType.SKY, maskFile = sky.maskFile), w, h, m)
            val custom = sky.imagePath?.let { aiMasks[it] }
            val skyPx = if (custom != null) {
                // Cover the frame with your picture (keep its top).
                val sc = maxOf(w.toFloat() / custom.width, h.toFloat() / custom.height)
                val cw = (custom.width * sc).toInt().coerceAtLeast(w); val ch = (custom.height * sc).toInt().coerceAtLeast(h)
                val big = Bitmap.createScaledBitmap(custom, cw, ch, true)
                val arr = IntArray(w * h); big.getPixels(arr, 0, w, (cw - w) / 2, 0, w, h)
                if (big !== custom) big.recycle()
                arr
            } else Skies.preset(sky.preset, w, h)
            Skies.apply(px, w, h, wt, skyPx, sky)
        }
        var hasAlpha = false
        if (bg != null && bg.active) placed[bg.maskFile]?.let { m ->
            val wt = PhotoLocal.weights(PhotoMask(type = MaskType.SUBJECT, maskFile = bg.maskFile), w, h, m)
            PhotoLocal.background(px, w, h, wt, bg)
            hasAlpha = bg.mode == 2
        }
        placed.values.forEach { it.recycle() }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).also { if (hasAlpha) it.setHasAlpha(true) }
    }

    /** Full render: geometry, colour, detail. */
    fun render(src: Bitmap, grade: ProGrade, detail: Detail, geometry: Geometry, tables: Array<FloatArray>?): Bitmap {
        val g = geometry(src, geometry)
        val w = g.width; val h = g.height
        val px = IntArray(w * h)
        g.getPixels(px, 0, w, 0, 0, w, h)
        if (g !== src) g.recycle()
        if (tables != null) applyLut(px, tables)
        detail(px, w, h, detail)
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** 3 × 64-bin RGB histogram + luma, normalised 0..1. */
    fun histogram(bmp: Bitmap): Array<FloatArray> {
        val w = bmp.width; val h = bmp.height
        val step = max(1, (w * h) / 60000)
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val r = IntArray(64); val g = IntArray(64); val b = IntArray(64); val l = IntArray(64)
        var i = 0
        while (i < px.size) {
            val p = px[i]
            val pr = (p shr 16) and 0xFF; val pg = (p shr 8) and 0xFF; val pb = p and 0xFF
            r[pr shr 2]++; g[pg shr 2]++; b[pb shr 2]++
            l[((pr * 54 + pg * 183 + pb * 19) shr 8) shr 2]++
            i += step
        }
        val m = maxOf(r.max(), g.max(), b.max(), l.max()).coerceAtLeast(1).toFloat()
        return arrayOf(FloatArray(64) { r[it] / m }, FloatArray(64) { g[it] / m }, FloatArray(64) { b[it] / m }, FloatArray(64) { l[it] / m })
    }
}
