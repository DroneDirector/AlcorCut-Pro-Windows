package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Kinds of local-adjustment masks. */
enum class MaskType(val label: String) { LINEAR("Linear gradient"), RADIAL("Radial"), BRUSH("Brush"), SUBJECT("Subject (AI)"), SKY("Sky (AI)"), COLOR_RANGE("Colour range"), LUMA_RANGE("Brightness range"), PEN("Shape (pen)"), OBJECT("Object (AI)"), DEPTH("Depth range (AI)"), PEOPLE("People (AI)") }

/** One brush stroke: points (x, y pairs, 0..1 of the picture), size (share of the width), add or erase. */
data class BrushStroke(val points: List<Float>, val size: Float, val erase: Boolean = false, val soft: Float = 0.5f)

/**
 * A local adjustment: where (gradient, ellipse, brush strokes or an AI selection) and what
 * (its own light / colour settings applied on top of the global edit). Coordinates are 0..1
 * of the edited (cropped) picture.
 */
data class PhotoMask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: MaskType = MaskType.RADIAL,
    // Linear: full effect at (x0, y0), fading out to nothing at (x1, y1).
    val x0: Float = 0.5f, val y0: Float = 0.1f, val x1: Float = 0.5f, val y1: Float = 0.5f,
    // Radial: centre, radii (share of width / height) and feather.
    val cx: Float = 0.5f, val cy: Float = 0.5f, val rx: Float = 0.25f, val ry: Float = 0.25f,
    val feather: Float = 0.5f,
    val invert: Boolean = false,
    val strokes: List<BrushStroke> = emptyList(),
    /** AI masks: path of the saved selection (grey PNG) in the app's files. */
    val maskFile: String? = null,
    val grade: ProGrade = ProGrade(),
    val amount: Float = 1f,
    val visible: Boolean = true,
    // Colour range: hue (degrees) ± half of hueWidth; brightness range: lumLow..lumHigh (0..1).
    val hue: Float = 210f,
    val hueWidth: Float = 40f,
    val lumLow: Float = 0.7f,
    val lumHigh: Float = 1f,
    /** Softness of the range edges, 0..1. */
    val rangeSoft: Float = 0.4f,
    /** Shape (pen) masks on video: the curve's points (x, y pairs, 0..1), and its planar track (source ms) from reference corners [pref]. */
    val pts: List<Float>? = null,
    val ptrack: List<PinQuad>? = null,
    val pref: List<Float>? = null,
    /** Combined with more selections: each sub-mask adds (1), subtracts (2) or intersects (3) — its [combine]. */
    val subs: List<PhotoMask>? = null,
    val combine: Int = 0,
    /** People masks: which parts ([PeopleMask.PARTS] indices). */
    val parts: List<Int>? = null,
    /** Detail inside the mask, −1..1: Texture (fine detail; minus smooths skin) and Clarity (mid-size contrast). */
    val texture: Float = 0f,
    val clarity: Float = 0f,
    /** A name shown in the mask list (e.g. Dodge / Burn); null = the mask's kind. */
    val label: String? = null,
    /** Noise reduction inside the mask, 0..1 (e.g. only the sky or the shadows). */
    val noise: Float = 0f,
    /** AI masks: Refine edge 0..1 — the edge follows fine detail such as hair and fur in the photo. */
    val refine: Float = 0f,
)

/** A healed spot: centre and radius (share of the width) and where the patch is taken from (offset, same units). */
/** A healed spot; [eye] 1 = red-eye fix, 2 = pet-eye fix (then the spot isn't a patch copy). */
data class Spot(val x: Float, val y: Float, val r: Float, val dx: Float, val dy: Float, val clone: Boolean = false, val eye: Int = 0)

/** What to do with everything except the subject (needs an AI subject selection). */
data class PhotoBackground(
    val mode: Int = 0, val blur: Float = 0.5f, val color: Int = 0xFFFFFFFF.toInt(), val maskFile: String? = null,
    /** Double exposure: the second picture shown inside the subject, and how strongly. */
    val imagePath: String? = null, val amount: Float = 0.85f,
) {
    /** 0 off, 1 blur (portrait), 2 remove (transparent), 3 colour, 4 double exposure, 5 pop out (darker background, glow round the subject). */
    val active get() = mode != 0 && maskFile != null
}

/** Sky replacement: the AI sky selection filled with a built-in sky or your own picture. */
data class SkySwap(val maskFile: String? = null, val preset: Int = 0, val imagePath: String? = null, val amount: Float = 1f, val warm: Float = 0.3f) {
    val active get() = maskFile != null && amount > 0f
}

object Skies {
    val names = listOf("Clear blue", "Golden hour", "Sunset", "Dramatic", "Night")

    /** A built-in sky as a w×h picture (top to horizon). */
    fun preset(i: Int, w: Int, h: Int): IntArray {
        val stops: List<Pair<Float, Int>> = when (i) {
            1 -> listOf(0f to 0xFF5A7FC2.toInt(), 0.6f to 0xFFE9B784.toInt(), 1f to 0xFFF8D49A.toInt())
            2 -> listOf(0f to 0xFF2E2463.toInt(), 0.5f to 0xFFC4506A.toInt(), 0.8f to 0xFFF27B4E.toInt(), 1f to 0xFFFFC77D.toInt())
            3 -> listOf(0f to 0xFF2B3038.toInt(), 0.55f to 0xFF59616B.toInt(), 1f to 0xFFA8B0B8.toInt())
            4 -> listOf(0f to 0xFF03081A.toInt(), 0.7f to 0xFF0F2347.toInt(), 1f to 0xFF27426E.toInt())
            else -> listOf(0f to 0xFF1F5FC9.toInt(), 0.65f to 0xFF6FA6E8.toInt(), 1f to 0xFFBFDCF7.toInt())
        }
        val out = IntArray(w * h)
        val rnd = java.util.Random(7L + i)
        for (y in 0 until h) {
            val t = y.toFloat() / (h - 1).coerceAtLeast(1)
            var k = 0
            while (k < stops.size - 2 && t > stops[k + 1].first) k++
            val (t0, c0) = stops[k]; val (t1, c1) = stops[k + 1]
            val f = ((t - t0) / (t1 - t0).coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
            for (x in 0 until w) {
                // A little noise so the gradient doesn't band.
                val n = (rnd.nextFloat() - 0.5f) * 3f
                fun ch(s: Int) = (((c0 shr s) and 0xFF) * (1 - f) + ((c1 shr s) and 0xFF) * f + n).toInt().coerceIn(0, 255)
                out[y * w + x] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
        }
        if (i == 4) repeat(w * h / 2500) { val p = rnd.nextInt(w * (h * 2 / 3).coerceAtLeast(1)); out[p] = 0xFFFFFFFF.toInt() }
        return out
    }

    /** Puts the sky into [px] where the sky mask is; the rest gets a touch of the sky's warmth. */
    fun apply(px: IntArray, w: Int, h: Int, wt: FloatArray, sky: IntArray, s: SkySwap) {
        var sr = 0L; var sg = 0L; var sb = 0L
        for (c in sky) { sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF }
        val n = sky.size.coerceAtLeast(1)
        val tr = sr / n / 255f; val tg = sg / n / 255f; val tb = sb / n / 255f
        val avg = (tr + tg + tb) / 3f
        for (i in px.indices) {
            val a = (wt[i] * s.amount).coerceIn(0f, 1f)
            val o = px[i]; val k = sky[i]
            var r = ((o shr 16) and 0xFF).toFloat(); var g = ((o shr 8) and 0xFF).toFloat(); var b = (o and 0xFF).toFloat()
            // Match the foreground's colour to the new light, gently.
            val m = s.warm * (1f - a)
            r *= 1f + m * (tr - avg); g *= 1f + m * (tg - avg); b *= 1f + m * (tb - avg)
            r = r * (1 - a) + ((k shr 16) and 0xFF) * a
            g = g * (1 - a) + ((k shr 8) and 0xFF) * a
            b = b * (1 - a) + (k and 0xFF) * a
            px[i] = (o and 0xFF000000.toInt()) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
        }
    }
}

/** Lens corrections. */
data class LensFix(
    /** + removes barrel distortion (wide lenses), − removes pincushion. */
    val distortion: Float = 0f,
    /** Brightens dark corners. */
    val vignette: Float = 0f,
    /** Lines up the red and blue channels (colour fringes at the edges). */
    val fringe: Float = 0f,
    /** Measured colour-fringe fix (AUTO): how much red / blue are scaled from the centre (e.g. 0.001 = 0.1 %). */
    val caR: Float = 0f,
    val caB: Float = 0f,
    /** Name of the lens profile used (shown in the panel), or null. */
    val profile: String? = null,
) {
    val isIdentity get() = this == LensFix()
}

object PhotoLocal {

    // ------------------------------------------------------------------ lens

    fun lens(src: Bitmap, l: LensFix): Bitmap {
        if (l.isIdentity) return src
        var b = src
        if (l.distortion != 0f) b = distort(b, l.distortion)
        val ca = l.fringe != 0f || l.caR != 0f || l.caB != 0f
        if (l.vignette != 0f || ca) {
            val w = b.width; val h = b.height
            val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
            val out = IntArray(w * h)
            val cx = w / 2f; val cy = h / 2f
            val maxR2 = cx * cx + cy * cy
            val kr = 1f + l.fringe * 0.004f + l.caR; val kb = 1f - l.fringe * 0.004f + l.caB
            val vig = l.vignette * 0.7f
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                var r: Int; var g: Int; var bl: Int
                val c = px[i]
                g = (c shr 8) and 0xFF
                if (ca) {
                    r = (sample(px, w, h, cx + (x - cx) * kr, cy + (y - cy) * kr) shr 16) and 0xFF
                    bl = sample(px, w, h, cx + (x - cx) * kb, cy + (y - cy) * kb) and 0xFF
                } else { r = (c shr 16) and 0xFF; bl = c and 0xFF }
                if (vig != 0f) {
                    val d2 = ((x - cx) * (x - cx) + (y - cy) * (y - cy)) / maxR2
                    val k = 1f + vig * d2 * d2 * 1.6f
                    r = (r * k).toInt().coerceIn(0, 255); g = (g * k).toInt().coerceIn(0, 255); bl = (bl * k).toInt().coerceIn(0, 255)
                }
                out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
            }
            val nb = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
            if (b !== src) b.recycle()
            b = nb
        }
        return b
    }

    /**
     * Measures lateral colour fringing: finds how much the red and the blue picture must be scaled
     * from the centre to line up with green on the strong edges towards the corners.
     */
    fun measureCa(src: Bitmap): Pair<Float, Float> {
        val k = min(1f, 1400f / maxOf(src.width, src.height))
        val b = if (k < 1f) Bitmap.createScaledBitmap(src, (src.width * k).toInt().coerceAtLeast(8), (src.height * k).toInt().coerceAtLeast(8), true) else src
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        if (b !== src) b.recycle()
        val cx = w / 2f; val cy = h / 2f; val maxR = sqrt(cx * cx + cy * cy)
        // Edge points in the outer part of the picture where green changes strongly along the radius.
        val pts = ArrayList<Int>()
        fun gAt(x: Int, y: Int) = (px[y * w + x] shr 8) and 0xFF
        for (y in 2 until h - 2 step 2) for (x in 2 until w - 2 step 2) {
            val dx = x - cx; val dy = y - cy; val r = sqrt(dx * dx + dy * dy)
            if (r < maxR * 0.45f) continue
            val ux = dx / r; val uy = dy / r
            val g1 = gAt((x + ux * 2).toInt().coerceIn(0, w - 1), (y + uy * 2).toInt().coerceIn(0, h - 1))
            val g0 = gAt((x - ux * 2).toInt().coerceIn(0, w - 1), (y - uy * 2).toInt().coerceIn(0, h - 1))
            if (abs(g1 - g0) > 40) pts += y * w + x
        }
        if (pts.size < 200) return 0f to 0f
        val use = if (pts.size > 30000) pts.shuffled(java.util.Random(7)).take(30000) else pts
        fun cost(shift: Int, s: Float): Double {
            var e = 0.0
            for (i in use) {
                val x = i % w; val y = i / w
                val v = (sample(px, w, h, cx + (x - cx) * (1f + s), cy + (y - cy) * (1f + s)) shr shift) and 0xFF
                val g = (px[i] shr 8) and 0xFF
                val d = (v - g).toDouble(); e += d * d
            }
            return e
        }
        fun best(shift: Int): Float {
            var bs = 0f; var be = cost(shift, 0f)
            var s = -0.005f
            while (s <= 0.005f) { val c = cost(shift, s); if (c < be) { be = c; bs = s }; s += 0.00025f }
            return bs
        }
        return best(16) to best(0)
    }

    /**
     * Generic lens profiles by the lens's 35 mm-equivalent focal length (from the photo's camera
     * details): typical distortion and corner shading for that kind of lens. A starting point —
     * fine-tune with the sliders.
     */
    fun genericProfile(focal35: Int): Pair<String, LensFix>? = when {
        focal35 <= 0 -> null
        focal35 <= 16 -> "Ultra-wide (${focal35} mm)" to LensFix(distortion = 0.35f, vignette = 0.35f)
        focal35 <= 20 -> "Wide (${focal35} mm)" to LensFix(distortion = 0.22f, vignette = 0.28f)
        focal35 <= 28 -> "Standard wide (${focal35} mm)" to LensFix(distortion = 0.08f, vignette = 0.2f)
        focal35 <= 55 -> "Normal (${focal35} mm)" to LensFix(distortion = 0.03f, vignette = 0.12f)
        else -> "Telephoto (${focal35} mm)" to LensFix(distortion = -0.04f, vignette = 0.08f)
    }

    /** Bilinear sample (clamped). */
    private fun sample(px: IntArray, w: Int, h: Int, fx: Float, fy: Float): Int {
        val x = fx.coerceIn(0f, w - 1.001f); val y = fy.coerceIn(0f, h - 1.001f)
        val x0 = x.toInt(); val y0 = y.toInt(); val tx = x - x0; val ty = y - y0
        val a = px[y0 * w + x0]; val b = px[y0 * w + x0 + 1]; val c = px[(y0 + 1) * w + x0]; val d = px[(y0 + 1) * w + x0 + 1]
        fun ch(s: Int): Int {
            val va = (a shr s) and 0xFF; val vb = (b shr s) and 0xFF; val vc = (c shr s) and 0xFF; val vd = (d shr s) and 0xFF
            return ((va * (1 - tx) + vb * tx) * (1 - ty) + (vc * (1 - tx) + vd * tx) * ty).toInt()
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Radial distortion fix with a warped mesh, zoomed just enough to leave no empty edges. */
    private fun distort(src: Bitmap, amount: Float): Bitmap {
        val w = src.width; val h = src.height
        val k = -amount * 0.18f
        val n = 40
        val verts = FloatArray((n + 1) * (n + 1) * 2)
        val cx = w / 2f; val cy = h / 2f
        val norm = sqrt(cx * cx + cy * cy)
        // Corner position after the warp decides the zoom that hides empty edges.
        val corner = 1f + k
        val edge = 1f + k * (min(cx, cy) / norm).let { it * it }
        val zoom = 1f / min(1f, min(corner, edge)).coerceAtLeast(0.5f)
        var i = 0
        for (gy in 0..n) for (gx in 0..n) {
            val x = gx * w.toFloat() / n; val y = gy * h.toFloat() / n
            val dx = (x - cx) / norm; val dy = (y - cy) / norm
            val f = (1f + k * (dx * dx + dy * dy)) * zoom
            verts[i++] = cx + (x - cx) * f; verts[i++] = cy + (y - cy) * f
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmapMesh(src, n, n, verts, 0, null, 0, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    // ------------------------------------------------------------------ perspective

    /** Keystone fix: [v] > 0 straightens converging verticals (buildings shot from below), [hz] the same sideways. */
    fun perspective(src: Bitmap, v: Float, hz: Float): Bitmap {
        if (v == 0f && hz == 0f) return src
        val w = src.width.toFloat(); val h = src.height.toFloat()
        val iv = abs(v) * 0.22f * w; val ih = abs(hz) * 0.22f * h
        // Source trapezoid that becomes the full rectangle.
        var tl = floatArrayOf(0f, 0f); var tr = floatArrayOf(w, 0f); var br = floatArrayOf(w, h); var bl = floatArrayOf(0f, h)
        if (v > 0) { tl = floatArrayOf(tl[0] + iv, tl[1]); tr = floatArrayOf(tr[0] - iv, tr[1]) }
        if (v < 0) { bl = floatArrayOf(bl[0] + iv, bl[1]); br = floatArrayOf(br[0] - iv, br[1]) }
        if (hz > 0) { tl = floatArrayOf(tl[0], tl[1] + ih); bl = floatArrayOf(bl[0], bl[1] - ih) }
        if (hz < 0) { tr = floatArrayOf(tr[0], tr[1] + ih); br = floatArrayOf(br[0], br[1] - ih) }
        val m = Matrix()
        m.setPolyToPoly(floatArrayOf(tl[0], tl[1], tr[0], tr[1], br[0], br[1], bl[0], bl[1]), 0, floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, 4)
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    // ------------------------------------------------------------------ healing

    /**
     * Picks where to copy a patch from for a spot at (x, y) radius r (all 0..1 of the width):
     * a nearby area whose surroundings look like the spot's surroundings and which is itself calm
     * (no edges or objects that would be copied in). Only reads the area around the spot.
     */
    fun findSource(src: Bitmap, x: Float, y: Float, r: Float): Pair<Float, Float> {
        val w = src.width; val h = src.height
        val R = (r * w).coerceAtLeast(2f)
        val reach = (R * 5.6f).toInt()
        val cxF = x * w; val cyF = y * h
        val rx0 = max(0, (cxF - reach).toInt()); val ry0 = max(0, (cyF - reach).toInt())
        val rx1 = min(w, (cxF + reach).toInt() + 1); val ry1 = min(h, (cyF + reach).toInt() + 1)
        val rw = rx1 - rx0; val rh = ry1 - ry0
        if (rw < 4 || rh < 4) return (2.4f * r) to 0f
        val px = IntArray(rw * rh); src.getPixels(px, 0, rw, rx0, ry0, rw, rh)
        val cx = cxF - rx0; val cy = cyF - ry0
        fun lum(xx: Float, yy: Float): Float {
            val ix = xx.toInt().coerceIn(0, rw - 1); val iy = yy.toInt().coerceIn(0, rh - 1)
            val c = px[iy * rw + ix]
            return ((c shr 16) and 0xFF) * 0.3f + ((c shr 8) and 0xFF) * 0.59f + (c and 0xFF) * 0.11f
        }
        fun rgbDiff(ax: Float, ay: Float, bx: Float, by: Float): Float {
            val c1 = px[ay.toInt().coerceIn(0, rh - 1) * rw + ax.toInt().coerceIn(0, rw - 1)]
            val c2 = px[by.toInt().coerceIn(0, rh - 1) * rw + bx.toInt().coerceIn(0, rw - 1)]
            val dr = ((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF)
            val dg = ((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF)
            val db = (c1 and 0xFF) - (c2 and 0xFF)
            return (dr * dr + dg * dg + db * db).toFloat()
        }
        // How busy (edgy) the inside of a disk is.
        fun busy(ox: Float, oy: Float): Float {
            var sum = 0f; var n = 0
            for (k in 0 until 40) {
                val a = k * 2.399963f   // golden-angle spiral through the disk
                val rr = sqrt((k + 0.5f) / 40f) * R * 0.95f
                val sx = ox + cos(a) * rr; val sy = oy + sin(a) * rr
                val g = abs(lum(sx + 1.5f, sy) - lum(sx - 1.5f, sy)) + abs(lum(sx, sy + 1.5f) - lum(sx, sy - 1.5f))
                sum += g; n++
            }
            return sum / n
        }
        val ringBusy = run {
            var sum = 0f; var n = 0
            for (k in 0 until 32) { val a = k * Math.PI.toFloat() / 16; val sx = cx + cos(a) * R * 1.3f; val sy = cy + sin(a) * R * 1.3f
                sum += abs(lum(sx + 1.5f, sy) - lum(sx - 1.5f, sy)) + abs(lum(sx, sy + 1.5f) - lum(sx, sy - 1.5f)); n++ }
            sum / n
        }
        var best = Float.MAX_VALUE; var bdx = 2.4f * r; var bdy = 0f
        for (dist in floatArrayOf(1.7f, 2.2f, 2.9f, 3.8f, 4.6f)) for (k in 0 until 24) {
            val a = k * Math.PI.toFloat() / 12
            val ox = cos(a) * R * dist; val oy = sin(a) * R * dist
            val qx = cx + ox; val qy = cy + oy
            if (qx - R * 1.45f < 0 || qx + R * 1.45f >= rw || qy - R * 1.45f < 0 || qy + R * 1.45f >= rh) continue
            if (cxF + ox - R < 0 || cxF + ox + R >= w || cyF + oy - R < 0 || cyF + oy + R >= h) continue
            // The ring just outside the spot vs the ring around the candidate.
            var err = 0f; var cnt = 0
            for (j in 0 until 32) {
                val b = j * Math.PI.toFloat() / 16
                for (rr in floatArrayOf(1.1f, 1.4f)) {
                    val sx = cx + cos(b) * R * rr; val sy = cy + sin(b) * R * rr
                    err += rgbDiff(sx, sy, sx + ox, sy + oy); cnt++
                }
            }
            val ring = err / cnt
            // Prefer calm patches, about as textured as the spot's surroundings; slight preference for near ones.
            val texture = busy(qx, qy)
            val score = ring + 18f * max(0f, texture - ringBusy * 1.2f).let { it * it } + dist * 6f
            if (score < best) { best = score; bdx = ox / w; bdy = oy / w }
        }
        return bdx to bdy
    }

    /**
     * Heal: copies the patch and corrects its colour smoothly from the edge inward (the difference
     * around the edge is spread across the spot, like a membrane), so it melts into its surroundings.
     * Clone stamp: an exact copy with a soft edge.
     */
    fun heal(src: Bitmap, spots: List<Spot>): Bitmap {
        if (spots.isEmpty()) return src
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val N = 64
        val cr = FloatArray(N); val cg = FloatArray(N); val cb = FloatArray(N)
        for (s in spots) {
            if (s.eye > 0) { fixEye(px, w, h, s); continue }
            val R = (s.r * w).coerceAtLeast(1.5f)
            val cx = s.x * w; val cy = s.y * h
            val ox = s.dx * w; val oy = s.dy * w
            fun at(x: Int, y: Int) = px[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)]
            // Colour difference (target − source) around the edge, each a small 3×3 average.
            var ar = 0f; var ag = 0f; var ab = 0f
            if (!s.clone) {
                for (k in 0 until N) {
                    val b = k * 2 * Math.PI / N
                    val sx = (cx + cos(b) * R * 1.04).toInt(); val sy = (cy + sin(b) * R * 1.04).toInt()
                    var r = 0f; var g = 0f; var bl = 0f
                    for (j in -1..1) for (i in -1..1) {
                        val c1 = at(sx + i, sy + j); val c2 = at((sx + i + ox).toInt(), (sy + j + oy).toInt())
                        r += ((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF)
                        g += ((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF)
                        bl += (c1 and 0xFF) - (c2 and 0xFF)
                    }
                    cr[k] = r / 9f; cg[k] = g / 9f; cb[k] = bl / 9f
                    ar += cr[k]; ag += cg[k]; ab += cb[k]
                }
                ar /= N; ag /= N; ab /= N
            }
            val edge = if (s.clone) 0.7f else 0.82f
            val x0 = max(0, (cx - R).toInt()); val x1 = min(w - 1, (cx + R).toInt())
            val y0 = max(0, (cy - R).toInt()); val y1 = min(h - 1, (cy + R).toInt())
            val out = IntArray((x1 - x0 + 1) * (y1 - y0 + 1))
            val ow = x1 - x0 + 1
            for (y in y0..y1) for (x in x0..x1) {
                val o = px[y * w + x]
                out[(y - y0) * ow + (x - x0)] = o
                val ddx = x - cx; val ddy = y - cy
                val d = sqrt(ddx * ddx + ddy * ddy) / R
                if (d >= 1f) continue
                val sxp = (x + ox).toInt(); val syp = (y + oy).toInt()
                if (sxp !in 0 until w || syp !in 0 until h) continue
                val t = 1f - ((d - edge) / (1f - edge)).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
                var kr = 0f; var kg = 0f; var kb = 0f
                if (!s.clone) {
                    // Edge correction in this direction, fading to the average towards the centre.
                    val ang = (kotlin.math.atan2(ddy, ddx) / (2 * Math.PI).toFloat() * N + N) % N
                    val k0 = ang.toInt() % N; val k1 = (k0 + 1) % N; val f = ang - ang.toInt()
                    val er = cr[k0] + (cr[k1] - cr[k0]) * f; val eg = cg[k0] + (cg[k1] - cg[k0]) * f; val eb = cb[k0] + (cb[k1] - cb[k0]) * f
                    val m = d * d * (3 - 2 * d)
                    kr = ar + (er - ar) * m; kg = ag + (eg - ag) * m; kb = ab + (eb - ab) * m
                }
                val c = px[syp * w + sxp]
                val r = ((((c shr 16) and 0xFF) + kr) * t + ((o shr 16) and 0xFF) * (1 - t)).toInt().coerceIn(0, 255)
                val g = ((((c shr 8) and 0xFF) + kg) * t + ((o shr 8) and 0xFF) * (1 - t)).toInt().coerceIn(0, 255)
                val bl = (((c and 0xFF) + kb) * t + (o and 0xFF) * (1 - t)).toInt().coerceIn(0, 255)
                out[(y - y0) * ow + (x - x0)] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
            }
            // Write back after the whole spot is done (so the spot never copies from itself mid-way).
            for (y in y0..y1) System.arraycopy(out, (y - y0) * ow, px, y * w + x0, ow)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Red eye: red pixels in the pupil get the red of their green / blue (dark, natural pupil). Pet eye: the bright
     * green / yellow / white glow is darkened to a black pupil, keeping a tiny catchlight. Soft round edge.
     */
    private fun fixEye(px: IntArray, w: Int, h: Int, s: Spot) {
        val R = (s.r * w).coerceAtLeast(2f)
        val cx = s.x * w; val cy = s.y * h
        val x0 = max(0, (cx - R).toInt()); val x1 = min(w - 1, (cx + R).toInt())
        val y0 = max(0, (cy - R).toInt()); val y1 = min(h - 1, (cy + R).toInt())
        for (y in y0..y1) for (x in x0..x1) {
            val dx = x - cx; val dy = y - cy
            val d = sqrt(dx * dx + dy * dy) / R
            if (d >= 1f) continue
            val t = 1f - ((d - 0.7f) / 0.3f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
            val c = px[y * w + x]
            val r = ((c shr 16) and 0xFF).toFloat(); val g = ((c shr 8) and 0xFF).toFloat(); val b = (c and 0xFF).toFloat()
            var nr = r; var ng = g; var nb = b
            if (s.eye == 1) {
                val gb = (g + b) / 2f
                // How red: only clearly red pixels change, so the iris and skin round the pupil stay.
                val red = ((r - gb) / (r + 1f) - 0.15f).coerceIn(0f, 0.5f) * 2f
                if (red > 0f) { nr = r + (gb * 0.9f - r) * red; ng = g * (1f - 0.1f * red); nb = b * (1f - 0.1f * red) }
            } else {
                val v = maxOf(r, g, b) / 255f
                if (v > 0.97f && d < 0.35f) continue      // the catchlight
                val k = ((v - 0.25f) / 0.45f).coerceIn(0f, 1f)
                if (k > 0f) { val l = (r * 0.3f + g * 0.59f + b * 0.11f) * 0.12f + 6f; nr = r + (l - r) * k; ng = g + (l - g) * k; nb = b + (l - b) * k }
            }
            val fr = (r + (nr - r) * t).toInt().coerceIn(0, 255); val fg = (g + (ng - g) * t).toInt().coerceIn(0, 255); val fb = (b + (nb - b) * t).toInt().coerceIn(0, 255)
            px[y * w + x] = (c and 0xFF000000.toInt()) or (fr shl 16) or (fg shl 8) or fb
        }
    }

    /** Eyes of every face in [b] (ML Kit landmarks), as red-eye spots where the pupil is clearly red. */
    fun findRedEyes(b: Bitmap): List<Spot> {
        val det = com.google.mlkit.vision.face.FaceDetection.getClient(
            com.google.mlkit.vision.face.FaceDetectorOptions.Builder()
                .setLandmarkMode(com.google.mlkit.vision.face.FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setPerformanceMode(com.google.mlkit.vision.face.FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).build(),
        )
        val w = b.width; val h = b.height
        return try {
            val faces = com.google.android.gms.tasks.Tasks.await(det.process(com.google.mlkit.vision.common.InputImage.fromBitmap(b, 0)))
            val out = ArrayList<Spot>()
            for (f in faces) {
                val r = f.boundingBox.width() * 0.075f
                for (lm in listOf(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE, com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)) {
                    val p = f.getLandmark(lm)?.position ?: continue
                    // Only eyes that really are red.
                    var red = 0; var n = 0
                    for (y in (p.y - r).toInt()..(p.y + r).toInt()) for (x in (p.x - r).toInt()..(p.x + r).toInt()) {
                        if (x !in 0 until w || y !in 0 until h) continue
                        val c = b.getPixel(x, y); val cr = (c shr 16) and 0xFF; val cg = (c shr 8) and 0xFF; val cb = c and 0xFF
                        n++; if (cr > 80 && cr > (cg + cb) * 0.8f) red++
                    }
                    if (n > 0 && red > n * 0.08f) out += Spot(p.x / w, p.y / h, r * 1.1f / w, 0f, 0f, eye = 1)
                }
            }
            out
        } catch (_: Throwable) { emptyList() } finally { try { det.close() } catch (_: Throwable) {} }
    }

    // ------------------------------------------------------------------ masks

    /** Mask strength 0..1 for every pixel of a w×h picture. */
    fun weights(m: PhotoMask, w: Int, h: Int, aiMask: Bitmap? = null, px: IntArray? = null, aiAll: Map<String, Bitmap> = emptyMap()): FloatArray {
        val out = FloatArray(w * h)
        when (m.type) {
            MaskType.PEN -> {
                val b = PenMask.raster(m, 0L, w, h)
                val px2 = IntArray(w * h); b.getPixels(px2, 0, w, 0, 0, w, h); b.recycle()
                for (i in px2.indices) out[i] = ((px2[i] shr 16) and 255) / 255f
            }
            MaskType.LINEAR -> {
                val ax = m.x0 * w; val ay = m.y0 * h; val bx = m.x1 * w; val by = m.y1 * h
                val dx = bx - ax; val dy = by - ay
                val len2 = (dx * dx + dy * dy).coerceAtLeast(1f)
                for (y in 0 until h) for (x in 0 until w) {
                    val t = (((x - ax) * dx + (y - ay) * dy) / len2).coerceIn(0f, 1f)
                    out[y * w + x] = 1f - t * t * (3 - 2 * t)
                }
            }
            MaskType.RADIAL -> {
                val cx = m.cx * w; val cy = m.cy * h; val rx = (m.rx * w).coerceAtLeast(1f); val ry = (m.ry * h).coerceAtLeast(1f)
                val inner = (1f - m.feather.coerceIn(0.02f, 1f))
                for (y in 0 until h) for (x in 0 until w) {
                    val dx = (x - cx) / rx; val dy = (y - cy) / ry
                    val d = sqrt(dx * dx + dy * dy)
                    val t = ((d - inner) / (1f - inner).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                    out[y * w + x] = 1f - t * t * (3 - 2 * t)
                }
            }
            MaskType.BRUSH -> {
                val scale = min(1f, 1024f / max(w, h))
                val mw = (w * scale).toInt().coerceAtLeast(1); val mh = (h * scale).toInt().coerceAtLeast(1)
                val mb = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888)
                val c = Canvas(mb)
                for (s in m.strokes) {
                    val rad = s.size * mw / 2f
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.WHITE
                        strokeWidth = rad * 2
                        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                        style = Paint.Style.STROKE
                        if (s.soft > 0f) maskFilter = BlurMaskFilter((rad * s.soft).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
                        if (s.erase) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    }
                    val pts = s.points
                    if (pts.size == 2) {
                        p.style = Paint.Style.FILL
                        c.drawCircle(pts[0] * mw, pts[1] * mh, rad, p)
                    } else {
                        val path = android.graphics.Path()
                        path.moveTo(pts[0] * mw, pts[1] * mh)
                        var i = 2
                        while (i + 1 < pts.size) { path.lineTo(pts[i] * mw, pts[i + 1] * mh); i += 2 }
                        c.drawPath(path, p)
                    }
                }
                fillFrom(mb, out, w, h)
                mb.recycle()
            }
            MaskType.SUBJECT, MaskType.SKY, MaskType.OBJECT, MaskType.PEOPLE -> if (aiMask != null) {
                fillFrom(aiMask, out, w, h)
                if (m.refine > 0f && px != null && px.size >= w * h) refineEdge(out, px, w, h, m.refine)
            }
            MaskType.DEPTH -> if (aiMask != null) {
                // The depth map (white = near) selected by range, like a brightness range.
                fillFrom(aiMask, out, w, h)
                val soft = 0.01f + m.rangeSoft * 0.3f
                for (i in out.indices) { val d = out[i]; out[i] = smooth(m.lumLow - soft, m.lumLow, d) * (1f - smooth(m.lumHigh, m.lumHigh + soft, d)) }
            }
            MaskType.COLOR_RANGE, MaskType.LUMA_RANGE -> if (px != null && px.size >= w * h) rangeWeights(m, px, out)
        }
        if (m.invert) for (i in out.indices) out[i] = 1f - out[i]
        // Add / subtract / intersect further selections.
        m.subs?.forEach { sub ->
            if (!sub.visible) return@forEach
            val sw = weights(sub.copy(amount = 1f, subs = null), w, h, sub.maskFile?.let { aiAll[it] }, px, aiAll)
            when (sub.combine) {
                2 -> for (i in out.indices) out[i] *= 1f - sw[i] * sub.amount
                3 -> for (i in out.indices) out[i] *= 1f - (1f - sw[i]) * sub.amount
                else -> for (i in out.indices) out[i] = max(out[i], sw[i] * sub.amount)
            }
        }
        if (m.amount != 1f) for (i in out.indices) out[i] *= m.amount
        return out
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        if (e1 <= e0) return if (x >= e1) 1f else 0f
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    /** Colour-range / brightness-range selection from the pixels themselves. */
    private fun rangeWeights(m: PhotoMask, px: IntArray, out: FloatArray) {
        val hsv = FloatArray(3)
        for (i in out.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            if (m.type == MaskType.COLOR_RANGE) {
                android.graphics.Color.RGBToHSV(r, g, b, hsv)
                var d = abs(hsv[0] - m.hue); if (d > 180f) d = 360f - d
                val inner = m.hueWidth / 2f
                val soft = 4f + m.rangeSoft * 60f
                // Grey pixels have no real colour, so they stay out.
                out[i] = (1f - smooth(inner, inner + soft, d)) * smooth(0.04f, 0.18f, hsv[1]) * smooth(0.03f, 0.12f, hsv[2])
            } else {
                val l = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                val soft = 0.01f + m.rangeSoft * 0.3f
                out[i] = smooth(m.lumLow - soft, m.lumLow, l) * (1f - smooth(m.lumHigh, m.lumHigh + soft, l))
            }
        }
    }

    /** Reads a mask picture's brightness (resized to w×h) into [out]. */
    private fun fillFrom(mb: Bitmap, out: FloatArray, w: Int, h: Int) {
        val s = if (mb.width == w && mb.height == h) mb else Bitmap.createScaledBitmap(mb, w, h, true)
        val px = IntArray(w * h); s.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val a = (c ushr 24) and 0xFF
            val l = ((c shr 16) and 0xFF).coerceAtLeast((c shr 8) and 0xFF)
            out[i] = (if (mb.hasAlpha() && a < 255) a else l) / 255f
        }
        if (s !== mb) s.recycle()
    }

    /** Portrait blur, transparent or coloured background outside the subject ([wt] = subject 0..1). */
    fun background(px: IntArray, w: Int, h: Int, wt: FloatArray, bg: PhotoBackground, second: Bitmap? = null) {
        when (bg.mode) {
            5 -> {
                // Pop out (thumbnails): the background darker and softer, a glow of [bg.color] round the subject.
                val sw = max(1, w / 4); val sh = max(1, h / 4)
                val full = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
                val small = Bitmap.createScaledBitmap(full, sw, sh, true); full.recycle()
                val sp = IntArray(sw * sh); small.getPixels(sp, 0, sw, 0, 0, sw, sh); small.recycle()
                val soft = boxBlur(sp, sw, sh, (max(sw, sh) * 0.006f).toInt().coerceAtLeast(1))
                // The subject's shape, blurred wide: the glow.
                val mw = IntArray(sw * sh)
                for (y in 0 until sh) for (x in 0 until sw) {
                    val v = (wt[(y * h / sh).coerceAtMost(h - 1) * w + (x * w / sw).coerceAtMost(w - 1)] * 255).toInt().coerceIn(0, 255)
                    mw[y * sw + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                }
                val glow = boxBlur(mw, sw, sh, (max(sw, sh) * 0.025f * (0.5f + bg.blur)).toInt().coerceAtLeast(2))
                fun up(a: IntArray): IntArray {
                    val b = Bitmap.createScaledBitmap(Bitmap.createBitmap(a, sw, sh, Bitmap.Config.ARGB_8888), w, h, true)
                    return IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h); b.recycle() }
                }
                val bp = up(soft); val gp = up(glow)
                val dim = 0.5f
                for (i in px.indices) {
                    val c = bp[i]
                    val d = (0xFF shl 24) or ((((c shr 16) and 0xFF) * dim).toInt() shl 16) or ((((c shr 8) and 0xFF) * dim).toInt() shl 8) or ((c and 0xFF) * dim).toInt()
                    val g = ((gp[i] and 0xFF) / 255f * (1f - wt[i]) * 2.2f).coerceIn(0f, 1f)
                    px[i] = mixPx(mixPx(d, bg.color, g * 0.95f), px[i], wt[i])
                }
            }
            4 -> {
                // Double exposure: the second picture screened into the subject, the background a plain colour.
                val sp = second?.let { s ->
                    val sc = maxOf(w.toFloat() / s.width, h.toFloat() / s.height)
                    val cw = (s.width * sc).toInt().coerceAtLeast(w); val ch = (s.height * sc).toInt().coerceAtLeast(h)
                    val big = Bitmap.createScaledBitmap(s, cw, ch, true)
                    val arr = IntArray(w * h); big.getPixels(arr, 0, w, (cw - w) / 2, (ch - h) / 2, w, h)
                    if (big !== s) big.recycle()
                    arr
                }
                val k = bg.amount.coerceIn(0f, 1f)
                for (i in px.indices) {
                    val a = px[i]
                    val inner = if (sp == null) a else {
                        val b = sp[i]
                        fun scr(x: Int, y: Int) = 255 - (255 - x) * (255 - y) / 255
                        val r = scr((a shr 16) and 0xFF, (b shr 16) and 0xFF); val g = scr((a shr 8) and 0xFF, (b shr 8) and 0xFF); val bl = scr(a and 0xFF, b and 0xFF)
                        mixPx(a, (0xFF shl 24) or (r shl 16) or (g shl 8) or bl, k)
                    }
                    // The edge fades into the background with a little of the second picture in it.
                    val outer = if (sp == null) bg.color else mixPx(bg.color, sp[i], 0.12f * k)
                    px[i] = mixPx(outer, inner, wt[i])
                }
            }
            1 -> {
                // Blur at a quarter size (fast, and a wide soft blur looks like a real lens).
                val sw = max(1, w / 4); val sh = max(1, h / 4)
                val full = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
                val small = Bitmap.createScaledBitmap(full, sw, sh, true)
                full.recycle()
                val sp = IntArray(sw * sh); small.getPixels(sp, 0, sw, 0, 0, sw, sh); small.recycle()
                val r = (bg.blur * max(sw, sh) * 0.03f).toInt().coerceIn(1, 60)
                val blurred = boxBlur(sp, sw, sh, r)
                val bb = Bitmap.createScaledBitmap(Bitmap.createBitmap(blurred, sw, sh, Bitmap.Config.ARGB_8888), w, h, true)
                val bp = IntArray(w * h); bb.getPixels(bp, 0, w, 0, 0, w, h); bb.recycle()
                for (i in px.indices) px[i] = mixPx(bp[i], px[i], wt[i])
            }
            2 -> for (i in px.indices) px[i] = ((wt[i] * 255).toInt().coerceIn(0, 255) shl 24) or (px[i] and 0x00FFFFFF)
            3 -> for (i in px.indices) px[i] = mixPx(bg.color, px[i], wt[i])
        }
    }

    private fun mixPx(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val r = (((a shr 16) and 0xFF) * (1 - k) + ((b shr 16) and 0xFF) * k).toInt()
        val g = (((a shr 8) and 0xFF) * (1 - k) + ((b shr 8) and 0xFF) * k).toInt()
        val bl = ((a and 0xFF) * (1 - k) + (b and 0xFF) * k).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun boxBlur(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        var a = src.copyOf(); val t = IntArray(src.size)
        repeat(3) {
            for (y in 0 until h) {
                var sr = 0; var sg = 0; var sb = 0
                for (x in -r..r) { val c = a[y * w + x.coerceIn(0, w - 1)]; sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF }
                for (x in 0 until w) {
                    val n = 2 * r + 1
                    t[y * w + x] = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                    val add = a[y * w + (x + r + 1).coerceAtMost(w - 1)]; val rem = a[y * w + (x - r).coerceAtLeast(0)]
                    sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF); sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF); sb += (add and 0xFF) - (rem and 0xFF)
                }
            }
            for (x in 0 until w) {
                var sr = 0; var sg = 0; var sb = 0
                for (y in -r..r) { val c = t[y.coerceIn(0, h - 1) * w + x]; sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF }
                for (y in 0 until h) {
                    val n = 2 * r + 1
                    a[y * w + x] = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                    val add = t[(y + r + 1).coerceAtMost(h - 1) * w + x]; val rem = t[(y - r).coerceAtLeast(0) * w + x]
                    sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF); sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF); sb += (add and 0xFF) - (rem and 0xFF)
                }
            }
        }
        return a
    }

    /** Applies every visible mask's own grade where the mask is. */
    fun applyMasks(px: IntArray, w: Int, h: Int, masks: List<PhotoMask>, aiMasks: Map<String, Bitmap>) {
        // Range masks look at the picture before any mask changed it.
        val orig = if (masks.any { mk -> (listOf(mk) + mk.subs.orEmpty()).any { it.type == MaskType.COLOR_RANGE || it.type == MaskType.LUMA_RANGE || it.refine > 0f } }) px.copyOf() else null
        for (m in masks) {
            val detail = m.texture != 0f || m.clarity != 0f || m.noise > 0f
            if (!m.visible || (m.grade.isIdentity && !detail)) continue
            val wt = weights(m, w, h, m.maskFile?.let { aiMasks[it] }, orig, aiMasks)
            if (detail) localDetail(px, w, h, wt, m.texture, m.clarity, m.noise)
            if (m.grade.isIdentity) continue
            val t = m.grade.tables()
            val one = IntArray(1)
            for (i in px.indices) {
                val a = wt[i]
                if (a < 0.003f) continue
                one[0] = px[i]
                ImageOps.applyLut(one, t)
                val o = px[i]; val g = one[0]
                val r = (((o shr 16) and 0xFF) * (1 - a) + ((g shr 16) and 0xFF) * a).toInt()
                val gg = (((o shr 8) and 0xFF) * (1 - a) + ((g shr 8) and 0xFF) * a).toInt()
                val b = ((o and 0xFF) * (1 - a) + (g and 0xFF) * a).toInt()
                px[i] = (0xFF shl 24) or (r shl 16) or (gg shl 8) or b
            }
        }
    }

    /**
     * Texture / Clarity inside a mask. Minus texture smooths skin: fine detail is softened but edges
     * (eyes, lips, hair lines) stay, as only small differences are evened out.
     */
    private fun localDetail(px: IntArray, w: Int, h: Int, wt: FloatArray, texture: Float, clarity: Float, noise: Float = 0f) {
        val scale = maxOf(w, h)
        fun pass(amount: Float, r: Int, smoothLimit: Int) {
            if (amount == 0f) return
            val bl = boxBlur(px, w, h, r)
            for (i in px.indices) {
                val a = wt[i]; if (a < 0.003f) continue
                val o = px[i]; val b = bl[i]
                fun ch(s: Int): Int {
                    val ov = (o shr s) and 255; val bv = (b shr s) and 255; val d = ov - bv
                    val v = if (amount < 0f) {
                        // Edge-aware smoothing: big jumps (real edges) are kept.
                        val keep = (kotlin.math.abs(d).toFloat() / smoothLimit).coerceIn(0f, 1f)
                        ov - d * (-amount) * (1f - keep * keep)
                    } else ov + d * amount * 1.6f
                    return (ov + (v - ov) * a).toInt().coerceIn(0, 255)
                }
                px[i] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
        }
        // Noise reduction: edge-aware smoothing of small, low-contrast specks (twice, for grainy skies).
        if (noise > 0f) { pass(-noise, maxOf(1, scale / 1000), 22 + (noise * 26).toInt()); pass(-noise * 0.7f, maxOf(2, scale / 500), 14 + (noise * 16).toInt()) }
        pass(texture, maxOf(1, scale / 700), 34)
        pass(clarity, maxOf(2, scale / 90), 60)
    }

    /**
     * Refine edge: a guided filter that lets the mask's edge follow the photo's own edges, so hair,
     * fur and leaves are picked out strand by strand instead of a smooth outline. Only the border
     * area changes; [k] 0..1 is how far it may move.
     */
    private fun refineEdge(m: FloatArray, px: IntArray, w: Int, h: Int, k: Float) {
        val n = w * h
        val g = FloatArray(n) { val c = px[it]; (0.299f * ((c shr 16) and 255) + 0.587f * ((c shr 8) and 255) + 0.114f * (c and 255)) / 255f }
        val r = maxOf(2, (maxOf(w, h) / 220f * (0.6f + k)).toInt())
        val eps = 0.0004f + (1f - k) * 0.004f
        fun box(a: FloatArray): FloatArray {
            val t = FloatArray(n); val o = FloatArray(n)
            for (y in 0 until h) {
                var s = 0f; val row = y * w
                for (x in -r..r) s += a[row + x.coerceIn(0, w - 1)]
                for (x in 0 until w) { t[row + x] = s; s += a[row + (x + r + 1).coerceAtMost(w - 1)] - a[row + (x - r).coerceAtLeast(0)] }
            }
            val d = (2f * r + 1) * (2f * r + 1)
            for (x in 0 until w) {
                var s = 0f
                for (y in -r..r) s += t[y.coerceIn(0, h - 1) * w + x]
                for (y in 0 until h) { o[y * w + x] = s / d; s += t[(y + r + 1).coerceAtMost(h - 1) * w + x] - t[(y - r).coerceAtLeast(0) * w + x] }
            }
            return o
        }
        val mI = box(g); val mP = box(m)
        val ip = FloatArray(n) { g[it] * m[it] }; val ii = FloatArray(n) { g[it] * g[it] }
        val cIp = box(ip); val cII = box(ii)
        val a = FloatArray(n); val b = FloatArray(n)
        for (i in 0 until n) {
            val varI = cII[i] - mI[i] * mI[i]; val cov = cIp[i] - mI[i] * mP[i]
            a[i] = cov / (varI + eps); b[i] = mP[i] - a[i] * mI[i]
        }
        val ma = box(a); val mb = box(b)
        for (i in 0 until n) {
            val q = (ma[i] * g[i] + mb[i]).coerceIn(0f, 1f)
            // Only near the border (where the original mask is in between).
            val border = 1f - kotlin.math.abs(mP[i] * 2f - 1f)
            val t = (border * 1.5f).coerceAtMost(1f) * k
            m[i] = m[i] + (q - m[i]) * t
        }
    }
}

