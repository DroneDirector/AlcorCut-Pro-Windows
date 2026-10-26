package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cbrt
import kotlin.math.pow

/**
 * Soft proof with a printer's ICC profile (Photo editor → Export → PRINTER PROFILE): the photo is
 * sent through the profile to the printer's inks and back, so the screen shows what that paper and
 * printer can reproduce. Own colour engine (no colour-management library): reads ICC v2 / v4
 * lut8, lut16, lutAtoB and lutBtoA tables with curv / para curves, PCS in Lab or XYZ.
 */
object IccProof {
    class Lut(
        val inCh: Int, val outCh: Int,
        val pre: Array<FloatArray>?, val matrix: FloatArray?, val grid: IntArray, val clut: FloatArray?,
        val post: Array<FloatArray>?, val bCurves: Array<FloatArray>?, val mCurves: Array<FloatArray>?, val aCurves: Array<FloatArray>?,
        val legacyLab: Boolean, /** lutAtoB / lutBtoA order (B, M, CLUT, A curves). */ val modern: Boolean, val aToB: Boolean,
    )

    class Profile(val name: String, val pcsLab: Boolean, val deviceCh: Int, val toDevice: Map<Int, Lut>, val toPcs: Map<Int, Lut>, val white: FloatArray)

    /** 0 off; else the imported profile is used. */
    var on by mutableStateOf(false)
    /** 0 perceptual, 1 relative colorimetric. */
    var intent by mutableIntStateOf(0)
    /** Show the paper's own white (and ink black) instead of screen white. */
    var paper by mutableStateOf(true)
    var profile by mutableStateOf<Profile?>(null)
    private var lutKey = ""
    private var cached: IntArray? = null
    const val N = 33

    // ---------- reading
    private class R(val b: ByteBuffer) {
        fun u32(o: Int) = b.getInt(o).toLong() and 0xFFFFFFFFL
        fun u16(o: Int) = b.getShort(o).toInt() and 0xFFFF
        fun u8(o: Int) = b.get(o).toInt() and 0xFF
        fun s15(o: Int) = b.getInt(o) / 65536f
        fun sig(o: Int) = String(ByteArray(4) { b.get(o + it) }, Charsets.US_ASCII)
    }

    private fun curve(r: R, o: Int): Pair<FloatArray, Int> {
        return when (r.sig(o)) {
            "curv" -> {
                val n = r.u32(o + 8).toInt()
                val t = when (n) {
                    0 -> FloatArray(256) { it / 255f }
                    1 -> { val g = r.u16(o + 12) / 256f; FloatArray(256) { (it / 255f).pow(g) } }
                    else -> FloatArray(n) { r.u16(o + 12 + it * 2) / 65535f }
                }
                t to (12 + (if (n == 1) 2 else n * 2) + 3) / 4 * 4
            }
            "para" -> {
                val fn = r.u16(o + 8)
                val np = intArrayOf(1, 3, 4, 5, 7).getOrElse(fn) { 1 }
                val p = FloatArray(7) { if (it < np) r.s15(o + 12 + it * 4) else 0f }
                val g = p[0]; val a = p[1]; val b = p[2]; val c = p[3]; val d = p[4]; val e = p[5]; val f = p[6]
                val t = FloatArray(1024) { i ->
                    val x = i / 1023f
                    val y = when (fn) {
                        0 -> x.pow(g)
                        1 -> if (x >= -b / a) (a * x + b).coerceAtLeast(0f).pow(g) else 0f
                        2 -> if (x >= -b / a) (a * x + b).coerceAtLeast(0f).pow(g) + c else c
                        3 -> if (x >= d) (a * x + b).coerceAtLeast(0f).pow(g) else c * x
                        else -> if (x >= d) (a * x + b).coerceAtLeast(0f).pow(g) + e else c * x + f
                    }
                    y.coerceIn(0f, 1f)
                }
                t to (12 + np * 4 + 3) / 4 * 4
            }
            else -> throw IllegalArgumentException("curve")
        }
    }

    private fun curves(r: R, o: Int, n: Int): Array<FloatArray> {
        var p = o
        return Array(n) { val (c, len) = curve(r, p); p += len; c }
    }

    private fun lut(r: R, o: Int): Lut? {
        val type = r.sig(o)
        val inCh = r.u8(o + 8); val outCh = r.u8(o + 9)
        if (inCh !in 1..8 || outCh !in 1..8) return null
        when (type) {
            "mft2", "mft1" -> {
                val wide = type == "mft2"
                val g = r.u8(o + 10)
                val mtx = FloatArray(9) { r.s15(o + 12 + it * 4) }
                var p = o + 48
                val inN: Int; val outN: Int
                if (wide) { inN = r.u16(p); outN = r.u16(p + 2); p += 4 } else { inN = 256; outN = 256 }
                fun v(at: Int) = if (wide) r.u16(at) / 65535f else r.u8(at) / 255f
                val sz = if (wide) 2 else 1
                val pre = Array(inCh) { c -> FloatArray(inN) { v(p + (c * inN + it) * sz) } }
                p += inCh * inN * sz
                var cells = 1; repeat(inCh) { cells *= g }
                val clut = FloatArray(cells * outCh) { v(p + it * sz) }
                p += cells * outCh * sz
                val post = Array(outCh) { c -> FloatArray(outN) { v(p + (c * outN + it) * sz) } }
                return Lut(inCh, outCh, pre, mtx, IntArray(inCh) { g }, clut, post, null, null, null, legacyLab = wide, modern = false, aToB = false)
            }
            "mAB ", "mBA " -> {
                val aToB = type == "mAB "
                val oB = r.u32(o + 12).toInt(); val oM = r.u32(o + 16).toInt(); val oMx = r.u32(o + 20).toInt(); val oC = r.u32(o + 24).toInt(); val oA = r.u32(o + 28).toInt()
                val bN = if (aToB) outCh else inCh; val aN = if (aToB) inCh else outCh
                val bC = if (oB != 0) curves(r, o + oB, bN) else null
                val mC = if (oM != 0) curves(r, o + oM, bN) else null
                val aC = if (oA != 0) curves(r, o + oA, aN) else null
                val mtx = if (oMx != 0) FloatArray(12) { r.s15(o + oMx + it * 4) } else null
                var grid = IntArray(0); var clut: FloatArray? = null
                if (oC != 0) {
                    val q = o + oC
                    val cin = inCh
                    grid = IntArray(cin) { r.u8(q + it) }
                    val prec = r.u8(q + 16)
                    var cells = 1; grid.forEach { cells *= it }
                    val cout = outCh
                    clut = FloatArray(cells * cout) { if (prec == 1) r.u8(q + 20 + it) / 255f else r.u16(q + 20 + it * 2) / 65535f }
                }
                return Lut(inCh, outCh, null, mtx, grid, clut, null, bC, mC, aC, legacyLab = false, modern = true, aToB = aToB)
            }
        }
        return null
    }

    fun read(bytes: ByteArray): Profile {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val r = R(b)
        if (bytes.size < 132 || r.sig(36) != "acsp") throw IllegalArgumentException("Not an ICC profile")
        val space = r.sig(16); val pcs = r.sig(20)
        val ch = when (space) { "CMYK" -> 4; "RGB " -> 3; "GRAY" -> 1; "CMY " -> 3; else -> when { space.endsWith("CLR") -> space[0].digitToIntOrNull(16) ?: 0; else -> 0 } }
        if (ch == 0) throw IllegalArgumentException("Unsupported colour space ($space)")
        val n = r.u32(128).toInt()
        val tags = HashMap<String, Int>()
        for (i in 0 until n) { val e = 132 + i * 12; tags[r.sig(e)] = r.u32(e + 4).toInt() }
        fun l(t: String) = tags[t]?.let { try { lut(r, it) } catch (_: Throwable) { null } }
        val toDev = HashMap<Int, Lut>(); val toPcs = HashMap<Int, Lut>()
        for (k in 0..2) { l("B2A$k")?.let { toDev[k] = it }; l("A2B$k")?.let { toPcs[k] = it } }
        if (toDev.isEmpty() || toPcs.isEmpty()) throw IllegalArgumentException("This profile has no printer tables (it may be a screen profile)")
        val wt = tags["wtpt"]?.takeIf { r.sig(it) == "XYZ " }?.let { floatArrayOf(r.s15(it + 8), r.s15(it + 12), r.s15(it + 16)) } ?: floatArrayOf(0.9642f, 1f, 0.8249f)
        val name = tags["desc"]?.let { o ->
            when (r.sig(o)) {
                "desc" -> { val len = r.u32(o + 8).toInt().coerceIn(0, 120); String(bytes, o + 12, len, Charsets.ISO_8859_1).trim('\u0000', ' ') }
                "mluc" -> { val cnt = r.u32(o + 8).toInt(); if (cnt < 1) null else { val len = r.u32(o + 20).toInt(); val off = r.u32(o + 24).toInt(); String(bytes, o + off, len.coerceAtMost(240), Charsets.UTF_16BE).trim('\u0000', ' ') } }
                else -> null
            }
        }?.takeIf { it.isNotBlank() } ?: "Printer profile"
        return Profile(name, pcs == "Lab ", ch, toDev, toPcs, wt)
    }

    // ---------- evaluating
    private fun table(t: FloatArray, x: Float): Float {
        val f = x.coerceIn(0f, 1f) * (t.size - 1); val i = f.toInt().coerceAtMost(t.size - 2); val a = f - i
        return t[i] * (1 - a) + t[i + 1] * a
    }

    /** Multilinear CLUT lookup over any number of inputs. */
    private fun clut(l: Lut, inp: FloatArray, out: FloatArray) {
        val n = l.grid.size; val oc = l.outCh
        val base = IntArray(n); val frac = FloatArray(n); val stride = IntArray(n)
        var s = oc
        for (d in n - 1 downTo 0) { stride[d] = s; s *= l.grid[d] }
        for (d in 0 until n) {
            val g = l.grid[d]; val f = inp[d].coerceIn(0f, 1f) * (g - 1)
            base[d] = f.toInt().coerceAtMost(g - 2).coerceAtLeast(0); frac[d] = f - base[d]
        }
        java.util.Arrays.fill(out, 0, oc, 0f)
        val c = l.clut!!
        for (corner in 0 until (1 shl n)) {
            var w = 1f; var idx = 0
            for (d in 0 until n) {
                val bit = (corner shr d) and 1
                w *= if (bit == 1) frac[d] else 1 - frac[d]
                idx += (base[d] + bit) * stride[d]
            }
            if (w == 0f) continue
            for (o in 0 until oc) out[o] += c[idx + o] * w
        }
    }

    private fun eval(l: Lut, inp: FloatArray): FloatArray {
        var v = inp.copyOf(l.inCh)
        val out = FloatArray(l.outCh)
        if (!l.modern) {
            l.pre?.let { pre -> for (i in 0 until l.inCh) v[i] = table(pre[i], v[i]) }
            clut(l, v, out)
            l.post?.let { post -> for (i in 0 until l.outCh) out[i] = table(post[i], out[i]) }
            return out
        }
        fun mtx(x: FloatArray): FloatArray {
            val m = l.matrix ?: return x
            return FloatArray(3) { r -> (m[r * 3] * x[0] + m[r * 3 + 1] * x[1] + m[r * 3 + 2] * x[2] + m[9 + r]).coerceIn(0f, 1f) }
        }
        if (l.aToB) {
            l.aCurves?.let { a -> for (i in 0 until l.inCh) v[i] = table(a[i], v[i]) }
            if (l.clut != null && l.grid.isNotEmpty()) { clut(l, v, out); v = out.copyOf() } else v = v.copyOf(l.outCh)
            l.mCurves?.let { m -> for (i in 0 until 3) v[i] = table(m[i], v[i]) }
            if (l.outCh == 3) v = mtx(v)
            l.bCurves?.let { b -> for (i in 0 until l.outCh) v[i] = table(b[i], v[i]) }
            return v
        } else {
            l.bCurves?.let { b -> for (i in 0 until l.inCh) v[i] = table(b[i], v[i]) }
            if (l.inCh == 3) v = mtx(v)
            l.mCurves?.let { m -> for (i in 0 until 3) v[i] = table(m[i], v[i]) }
            if (l.clut != null && l.grid.isNotEmpty()) { clut(l, v, out); v = out.copyOf() } else v = v.copyOf(l.outCh)
            l.aCurves?.let { a -> for (i in 0 until l.outCh) v[i] = table(a[i], v[i]) }
            return v
        }
    }

    // PCS encodings
    private fun labEnc(L: Float, a: Float, b: Float, legacy: Boolean): FloatArray =
        if (legacy) floatArrayOf(L / 100f * 65280f / 65535f, (a + 128f) / 255f * 65280f / 65535f, (b + 128f) / 255f * 65280f / 65535f)
        else floatArrayOf(L / 100f, (a + 128f) / 255f, (b + 128f) / 255f)
    private fun labDec(v: FloatArray, legacy: Boolean): FloatArray =
        if (legacy) floatArrayOf(v[0] * 65535f / 65280f * 100f, v[1] * 65535f / 65280f * 255f - 128f, v[2] * 65535f / 65280f * 255f - 128f)
        else floatArrayOf(v[0] * 100f, v[1] * 255f - 128f, v[2] * 255f - 128f)
    private val D50 = floatArrayOf(0.9642f, 1f, 0.8249f)
    private fun xyzToLab(x: FloatArray): FloatArray {
        fun f(t: Float) = if (t > 0.008856f) cbrt(t.toDouble()).toFloat() else 7.787f * t + 16f / 116f
        val fx = f(x[0] / D50[0]); val fy = f(x[1] / D50[1]); val fz = f(x[2] / D50[2])
        return floatArrayOf(116f * fy - 16f, 500f * (fx - fy), 200f * (fy - fz))
    }
    private fun labToXyz(l: FloatArray): FloatArray {
        val fy = (l[0] + 16f) / 116f; val fx = fy + l[1] / 500f; val fz = fy - l[2] / 200f
        fun g(t: Float) = if (t > 0.206893f) t * t * t else (t - 16f / 116f) / 7.787f
        return floatArrayOf(g(fx) * D50[0], g(fy) * D50[1], g(fz) * D50[2])
    }
    // sRGB (D65) ↔ XYZ D50 (Bradford-adapted).
    private val S2X = floatArrayOf(0.4360747f, 0.3850649f, 0.1430804f, 0.2225045f, 0.7168786f, 0.0606169f, 0.0139322f, 0.0971045f, 0.7141733f)
    private val X2S = floatArrayOf(3.1338561f, -1.6168667f, -0.4906146f, -0.9787684f, 1.9161415f, 0.0334540f, 0.0719453f, -0.2289914f, 1.4052427f)
    private fun lin(c: Float) = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    private fun enc(c: Float) = if (c <= 0.0031308f) c * 12.92f else 1.055f * c.coerceAtLeast(0f).pow(1 / 2.4f) - 0.055f

    private fun toPcsIn(l: Lut, xyz: FloatArray, lab: Boolean): FloatArray =
        if (lab) xyzToLab(xyz).let { labEnc(it[0], it[1], it[2], l.legacyLab) }
        else FloatArray(3) { (xyz[it] * 32768f / 65535f).coerceIn(0f, 1f) }
    private fun fromPcsOut(l: Lut, v: FloatArray, lab: Boolean): FloatArray =
        if (lab) labToXyz(labDec(v, l.legacyLab)) else FloatArray(3) { v[it] * 65535f / 32768f }

    /** sRGB colour → as printed → sRGB colour. */
    private fun proofOne(p: Profile, intent: Int, paper: Boolean, r: Float, g: Float, b: Float): Int {
        val lr = lin(r); val lg = lin(g); val lb = lin(b)
        val xyz = floatArrayOf(S2X[0] * lr + S2X[1] * lg + S2X[2] * lb, S2X[3] * lr + S2X[4] * lg + S2X[5] * lb, S2X[6] * lr + S2X[7] * lg + S2X[8] * lb)
        val toDev = p.toDevice[intent] ?: p.toDevice[0] ?: p.toDevice.values.first()
        val dev = eval(toDev, toPcsIn(toDev, xyz, p.pcsLab))
        // Back with relative colorimetric: what the inks really make.
        val back = p.toPcs[1] ?: p.toPcs[0] ?: p.toPcs.values.first()
        var out = fromPcsOut(back, eval(back, dev), p.pcsLab)
        if (paper) out = FloatArray(3) { out[it] * p.white[it] / D50[it] }
        val sr = X2S[0] * out[0] + X2S[1] * out[1] + X2S[2] * out[2]
        val sg = X2S[3] * out[0] + X2S[4] * out[1] + X2S[5] * out[2]
        val sb = X2S[6] * out[0] + X2S[7] * out[1] + X2S[8] * out[2]
        fun c8(v: Float) = (enc(v.coerceIn(0f, 1f)) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (c8(sr) shl 16) or (c8(sg) shl 8) or c8(sb)
    }

    /** 33³ sRGB → proof table for the current settings (cached). */
    private fun table(): IntArray? {
        val p = profile ?: return null
        val key = "${System.identityHashCode(p)}/$intent/$paper"
        if (key == lutKey) cached?.let { return it }
        val t = IntArray(N * N * N)
        for (bi in 0 until N) for (gi in 0 until N) for (ri in 0 until N)
            t[(bi * N + gi) * N + ri] = try { proofOne(p, intent, paper, ri / (N - 1f), gi / (N - 1f), bi / (N - 1f)) } catch (_: Throwable) { 0 }
        cached = t; lutKey = key
        return t
    }

    /** The photo as it would print with the profile (trilinear lookup in the table). */
    fun apply(src: Bitmap): Bitmap? {
        val t = table() ?: return null
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val s = (N - 1) / 255f
        for (i in px.indices) {
            val c = px[i]
            val fr = ((c shr 16) and 0xFF) * s; val fg = ((c shr 8) and 0xFF) * s; val fb = (c and 0xFF) * s
            val r0 = fr.toInt().coerceAtMost(N - 2); val g0 = fg.toInt().coerceAtMost(N - 2); val b0 = fb.toInt().coerceAtMost(N - 2)
            val ar = fr - r0; val ag = fg - g0; val ab = fb - b0
            var rr = 0f; var gg = 0f; var bb = 0f
            for (k in 0 until 8) {
                val dr = k and 1; val dg = (k shr 1) and 1; val db = (k shr 2) and 1
                val w8 = (if (dr == 1) ar else 1 - ar) * (if (dg == 1) ag else 1 - ag) * (if (db == 1) ab else 1 - ab)
                val v = t[((b0 + db) * N + g0 + dg) * N + r0 + dr]
                rr += ((v shr 16) and 0xFF) * w8; gg += ((v shr 8) and 0xFF) * w8; bb += (v and 0xFF) * w8
            }
            px[i] = (c and 0xFF000000.toInt()) or ((rr + 0.5f).toInt() shl 16) or ((gg + 0.5f).toInt() shl 8) or (bb + 0.5f).toInt()
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    // ---------- the imported profile, kept in the app
    private fun file(c: Context) = File(c.filesDir, "printer.icc")

    fun import(c: Context, uri: Uri): Profile {
        val bytes = c.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalArgumentException("Couldn't read the file")
        val p = read(bytes)
        file(c).writeBytes(bytes)
        profile = p; cached = null; lutKey = ""
        return p
    }

    fun load(c: Context) {
        if (profile != null) return
        val f = file(c)
        if (f.exists()) profile = try { read(f.readBytes()) } catch (_: Throwable) { null }
    }

    fun remove(c: Context) { file(c).delete(); profile = null; on = false; cached = null; lutKey = "" }
}
