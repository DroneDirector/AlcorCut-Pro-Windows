package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.DataOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Photo export options shared by every photo export (single, batch, filmstrip): colour space with
 * an embedded ICC profile, output sharpening for screen or print, the file-name template, and
 * named export presets.
 */
object ExportOpts {
    val SPACES = listOf("sRGB", "Display P3", "Adobe RGB")
    val SHARPEN_FOR = listOf("Off", "Screen", "Matte paper", "Glossy paper")
    val SHARPEN_AMOUNT = listOf("Low", "Standard", "High")
    const val DEFAULT_TEMPLATE = "{name}_edit"
    val TOKENS = listOf("{name}", "{date}", "{time}", "{seq}", "{preset}")

    var space by mutableStateOf(0)
    var sharpenFor by mutableStateOf(0)
    var sharpenAmount by mutableStateOf(1)
    var template by mutableStateOf(DEFAULT_TEMPLATE)
    var seq by mutableStateOf(1)
    /** Soft proof: shows on screen how a matte print will look (lower contrast, lifted blacks). */
    var proof by mutableStateOf(false)
    var presetName by mutableStateOf("")
    /** Gamut warning: 0 off, 1 glossy print, 2 matte print — colours the print can't show are marked on screen. */
    var gamut by mutableStateOf(0)
    val GAMUT = listOf("Off", "Glossy print", "Matte print")

    /** Ultra HDR JPEG (Android 14+): 0 off, else how much brighter the highlights get on HDR screens ([HDR_LEVELS]). */
    var hdr by mutableStateOf(0)
    val HDR_LEVELS = listOf("Off", "Subtle", "Standard", "Strong")
    private val HDR_RATIO = listOf(1f, 2f, 3.2f, 5f)
    val hdrSupported get() = Build.VERSION.SDK_INT >= 34
    private var loaded = false

    data class Preset(val name: String, val format: Int, val quality: Float, val sizeIdx: Int, val space: Int, val sharpenFor: Int, val sharpenAmount: Int, val template: String)

    /** Built-in export presets (sizeIdx: see the editor's size list — 0 full, 3 = 1920, 4 = 1080, 5 = 2048). */
    val builtIn = listOf(
        Preset("Web", 0, 0.85f, 5, 0, 1, 1, "{name}_web"),
        Preset("Social 1080", 0, 0.9f, 4, 0, 1, 1, "{name}_1080"),
        Preset("Full quality", 0, 1f, 0, 0, 0, 1, "{name}_full"),
        Preset("Print (TIFF 16-bit)", 4, 1f, 0, 2, 2, 1, "{name}_print"),
        Preset("Archive PNG", 1, 1f, 0, 0, 0, 1, "{name}"),
    )
    val user = mutableStateListOf<Preset>()

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = c.getSharedPreferences("procam", Context.MODE_PRIVATE)
        space = p.getInt("ex_space", 0); sharpenFor = p.getInt("ex_sharp_for", 0); sharpenAmount = p.getInt("ex_sharp_amt", 1)
        template = p.getString("ex_template", DEFAULT_TEMPLATE) ?: DEFAULT_TEMPLATE; seq = p.getInt("ex_seq", 1)
        hdr = p.getInt("ex_hdr", 0).coerceIn(0, HDR_LEVELS.lastIndex)
        try {
            val arr = ProjectStore.gson.fromJson(p.getString("ex_presets", "[]"), Array<Preset>::class.java)
            user.clear(); arr?.filterNotNull()?.let { user.addAll(it) }
        } catch (_: Throwable) {}
    }

    /** The options one export needs, handed to the export process. */
    class Snapshot(val space: Int, val sharpenFor: Int, val sharpenAmount: Int, val template: String, val seq: Int, val presetName: String, val hdr: Int)
    fun snapshot() = Snapshot(space, sharpenFor, sharpenAmount, template, seq, presetName, hdr)
    /** Uses [s] for this process's exports (presets etc. still come from the saved settings). */
    fun apply(c: Context, s: Snapshot) {
        load(c)
        space = s.space; sharpenFor = s.sharpenFor; sharpenAmount = s.sharpenAmount; template = s.template
        seq = s.seq; presetName = s.presetName; hdr = s.hdr
    }

    fun save(c: Context) {
        c.getSharedPreferences("procam", Context.MODE_PRIVATE).edit()
            .putInt("ex_space", space).putInt("ex_sharp_for", sharpenFor).putInt("ex_sharp_amt", sharpenAmount)
            .putString("ex_template", template).putInt("ex_seq", seq).putInt("ex_hdr", hdr)
            .putString("ex_presets", ProjectStore.gson.toJson(user.toList())).apply()
    }

    /**
     * Ultra HDR: adds a gain map to [bmp] (saved inside the JPEG). Normal screens show the photo
     * exactly as edited; HDR screens (most newer phones) show the bright parts — sky, lights,
     * reflections — brighter, the way they looked. Returns [bmp] unchanged when off or unsupported.
     */
    fun withGainmap(bmp: Bitmap): Bitmap {
        if (hdr == 0 || Build.VERSION.SDK_INT < 34) return bmp
        return try {
            val ratio = HDR_RATIO[hdr.coerceIn(0, HDR_RATIO.lastIndex)]
            // Quarter-size map, made from the picture's own brightness: only the highlights get a boost.
            val gw = max(1, bmp.width / 4); val gh = max(1, bmp.height / 4)
            val small = Bitmap.createScaledBitmap(bmp, gw, gh, true)
            val px = IntArray(gw * gh); small.getPixels(px, 0, gw, 0, 0, gw, gh)
            if (small !== bmp) small.recycle()
            val lnR = kotlin.math.ln(ratio)
            for (i in px.indices) {
                val c = px[i]
                val r = ((c shr 16) and 255) / 255f; val g = ((c shr 8) and 255) / 255f; val b = (c and 255) / 255f
                val l = max(0.2126f * r + 0.7152f * g + 0.0722f * b, max(r, max(g, b)) * 0.8f)
                val t = ((l - 0.62f) / 0.38f).coerceIn(0f, 1f)
                val s = t * t * (3 - 2 * t)
                val gain = 1f + (ratio - 1f) * s
                val v = (kotlin.math.ln(gain) / lnR * 255f).toInt().coerceIn(0, 255)
                px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            val content = Bitmap.createBitmap(px, gw, gh, Bitmap.Config.ARGB_8888)
            val gm = android.graphics.Gainmap(content)
            gm.setRatioMin(1f, 1f, 1f); gm.setRatioMax(ratio, ratio, ratio)
            gm.setGamma(1f, 1f, 1f)
            gm.setEpsilonSdr(1 / 64f, 1 / 64f, 1 / 64f); gm.setEpsilonHdr(1 / 64f, 1 / 64f, 1 / 64f)
            gm.minDisplayRatioForHdrTransition = 1f
            gm.displayRatioForFullHdr = ratio
            val out = if (bmp.isMutable) bmp else bmp.copy(Bitmap.Config.ARGB_8888, true)
            out.gainmap = gm
            out
        } catch (_: Throwable) { bmp }
    }

    // Approximate printable colour range of photo paper: per hue (Lab degrees) the strongest colour (chroma) and the lightness where it sits.
    private val GH = floatArrayOf(0f, 35f, 65f, 95f, 140f, 200f, 260f, 300f, 330f, 360f)
    private val GC = floatArrayOf(72f, 78f, 82f, 92f, 70f, 50f, 56f, 62f, 72f, 72f)
    private val GL = floatArrayOf(48f, 50f, 64f, 88f, 55f, 58f, 34f, 32f, 48f, 48f)

    /**
     * Gamut warning overlay for [bmp]: colours a print on glossy ([gamut] 1) or matte (2) paper
     * can't reproduce are shown in bright cyan; everything else is clear.
     */
    fun gamutOverlay(bmp: Bitmap): Bitmap? {
        if (gamut == 0) return null
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        val matte = gamut == 2
        val cScale = if (matte) 0.8f else 1f; val lMin = if (matte) 24f else 16f; val lMax = if (matte) 94f else 97f
        fun lin(v: Int): Float { val c = v / 255f; return if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat() }
        fun f(t: Float) = if (t > 0.008856f) Math.cbrt(t.toDouble()).toFloat() else 7.787f * t + 16f / 116f
        val out = IntArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            val r = lin((c shr 16) and 255); val g = lin((c shr 8) and 255); val b = lin(c and 255)
            val x = (0.4124f * r + 0.3576f * g + 0.1805f * b) / 0.95047f
            val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
            val z = (0.0193f * r + 0.1192f * g + 0.9505f * b) / 1.08883f
            val fx = f(x); val fy = f(y); val fz = f(z)
            val l = 116f * fy - 16f; val aa = 500f * (fx - fy); val bb = 200f * (fy - fz)
            val ch = kotlin.math.sqrt(aa * aa + bb * bb)
            if (ch < 18f) continue
            var hue = Math.toDegrees(kotlin.math.atan2(bb, aa).toDouble()).toFloat(); if (hue < 0) hue += 360f
            var k = 0; while (k < GH.size - 2 && hue > GH[k + 1]) k++
            val t = (hue - GH[k]) / (GH[k + 1] - GH[k])
            val cPeak = (GC[k] + (GC[k + 1] - GC[k]) * t) * cScale; val lPeak = GL[k] + (GL[k + 1] - GL[k]) * t
            val room = if (l >= lPeak) ((lMax - l) / (lMax - lPeak)) else ((l - lMin) / (lPeak - lMin))
            val cMax = cPeak * room.coerceIn(0f, 1f)
            if (ch > cMax + 2f) out[i] = 0xD000E5FF.toInt()
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** The file name (without extension) for an export of [source]. */
    fun fileName(context: Context, source: Uri?): String {
        load(context)
        val orig = source?.let { u ->
            try { context.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } } catch (_: Throwable) { null }
                ?: u.lastPathSegment
        }?.substringAfterLast('/')?.substringBeforeLast('.') ?: "EDIT"
        val now = Date()
        val n = (template.ifBlank { DEFAULT_TEMPLATE })
            .replace("{name}", orig)
            .replace("{date}", SimpleDateFormat("yyyyMMdd", Locale.US).format(now))
            .replace("{time}", SimpleDateFormat("HHmmss", Locale.US).format(now))
            .replace("{seq}", String.format(Locale.US, "%04d", seq))
            .replace("{preset}", presetName.ifBlank { "export" })
        if (template.contains("{seq}")) { seq++; save(context) }
        return n.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120).ifBlank { "EDIT" }
    }

    /** Output sharpening for the chosen medium, at the final size. */
    fun outputSharpen(bmp: Bitmap): Bitmap {
        if (sharpenFor == 0) return bmp
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        val amt = listOf(0.25f, 0.45f, 0.7f)[sharpenAmount.coerceIn(0, 2)] * (if (sharpenFor == 2) 1.4f else 1f)
        val radius = if (sharpenFor == 1) 1 else max(1, max(w, h) / 2400)
        ImageOps.detail(px, w, h, Detail(sharpen = amt.coerceAtMost(1f), sharpenRadius = radius - 1f, sharpenMasking = 0.15f))
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** The picture converted into the chosen colour space (the profile is embedded when saved). */
    fun toSpace(bmp: Bitmap): Bitmap {
        if (space == 0 || Build.VERSION.SDK_INT < 26) return bmp
        val cs = ColorSpace.get(if (space == 1) ColorSpace.Named.DISPLAY_P3 else ColorSpace.Named.ADOBE_RGB)
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888, bmp.hasAlpha(), cs)
        Canvas(out).drawBitmap(bmp, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** How a matte print will look, for the soft proof on screen. */
    fun proofView(bmp: Bitmap): Bitmap {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val p = px[i]
            fun ch(v: Int): Int = (14 + v * (238 - 14) / 255)
            val r = ch((p shr 16) and 255); val g = ch((p shr 8) and 255); val b = ch(p and 255)
            val l = (r * 54 + g * 183 + b * 19) shr 8
            fun sat(v: Int) = (l + (v - l) * 0.88f).toInt().coerceIn(0, 255)
            px[i] = (p and 0xFF000000.toInt()) or (sat(r) shl 16) or (sat(g) shl 8) or sat(b)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Writes an uncompressed TIFF (8- or 16-bit RGB / RGBA) with the colour profile's name noted. */
    fun writeTiff(bmp: Bitmap, file: File, bits16: Boolean) {
        val w = bmp.width; val h = bmp.height
        val alpha = bmp.hasAlpha()
        val spp = if (alpha) 4 else 3
        val bps = if (bits16) 16 else 8
        val bytesPerPx = spp * bps / 8
        val dataLen = w.toLong() * h * bytesPerPx
        val px = IntArray(w)
        DataOutputStream(file.outputStream().buffered(1 shl 16)).use { o ->
            // Header (big-endian), IFD right after it, then the values, then the pixels.
            o.writeBytes("MM"); o.writeShort(42); o.writeInt(8)
            val entries = if (alpha) 13 else 12
            val ifdSize = 2 + entries * 12 + 4
            var extra = 8 + ifdSize
            val bpsOff = extra; extra += spp * 2
            val resOff = extra; extra += 16
            val softOff = extra; val soft = "AlcorCut Pro\u0000".toByteArray(); extra += soft.size
            val dataOff = extra
            o.writeShort(entries)
            fun e(tag: Int, type: Int, count: Int, value: Int) { o.writeShort(tag); o.writeShort(type); o.writeInt(count); if (type == 3 && count == 1) { o.writeShort(value); o.writeShort(0) } else o.writeInt(value) }
            e(256, 4, 1, w); e(257, 4, 1, h)
            e(258, 3, spp, bpsOff)
            e(259, 3, 1, 1)          // no compression
            e(262, 3, 1, 2)          // RGB
            e(273, 4, 1, dataOff)    // strip offset
            e(277, 3, 1, spp)
            e(278, 4, 1, h)          // one strip
            e(279, 4, 1, dataLen.toInt())
            e(282, 5, 1, resOff); e(283, 5, 1, resOff + 8)
            e(305, 2, soft.size, softOff)   // software
            if (alpha) e(338, 3, 1, 2)       // unassociated alpha (tags stay in ascending order)
            o.writeInt(0)
            repeat(spp) { o.writeShort(bps) }
            o.writeInt(300); o.writeInt(1); o.writeInt(300); o.writeInt(1)
            o.write(soft)
            for (y in 0 until h) {
                bmp.getPixels(px, 0, w, 0, y, w, 1)
                for (x in 0 until w) {
                    val p = px[x]
                    val c = intArrayOf((p shr 16) and 255, (p shr 8) and 255, p and 255, (p ushr 24) and 255)
                    for (k in 0 until spp) if (bits16) o.writeShort(c[k] * 257) else o.writeByte(c[k])
                }
            }
        }
    }
}
