package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * RAW (DNG) develop for the photo editor — our own code, no third-party RAW library.
 *
 * Reads the sensor data straight from the DNG (uncompressed or lossless-JPEG, strips or tiles,
 * Bayer CFA or linear RGB), applies the black / white levels, linearisation table and the phone's
 * lens-shading gain maps, demosaics (half-size superpixels for previews, Malvar-He-Cutler at full
 * size for exports), converts with the camera's colour matrix, then white balance, exposure,
 * highlights, shadows, whites and blacks are applied in floating point with headroom above white
 * and a soft highlight roll-off — only then does the picture become 8-bit for the other tools.
 */
object RawDng {

    /** Developing choices kept with the edit (the Light / Color sliders do the rest). */
    data class Dev(
        /** Index into [PROFILES]. */
        val profile: Int = 0,
        /** Index into [WB_PRESETS]. */
        val wb: Int = 0,
        /** Turn off the lens corrections stored in the file (distortion, colour fringing, vignetting). */
        val lensOff: Boolean = false,
        /** Only the file's dark-corner (vignetting / shading) correction off. */
        val vigOff: Boolean = false,
        /** Only the file's lens-shape (distortion / fringe) correction off. */
        val shapeOff: Boolean = false,
        /** Index into [LOOKS]; 0 = the default look. */
        val look: Int = 0,
        /** An imported camera profile (.dcp) used instead of the file's own colour ([DcpStore] id); null = the file's. */
        val dcp: String? = null,
    ) {
        val vigOn: Boolean get() = !lensOff && !vigOff
        val shapeOn: Boolean get() = !lensOff && !shapeOff
    }

    class Profile(val label: String, val contrast: Float, val sat: Float, val knee: Float, val mono: Boolean = false, val camera: Boolean = false)
    val PROFILES = listOf(
        Profile("Standard", 0.22f, 1.10f, 0.74f),
        Profile("Flat", 0.0f, 0.96f, 0.60f),
        Profile("Vivid", 0.38f, 1.30f, 0.80f),
        Profile("Portrait", 0.12f, 1.02f, 0.70f),
        Profile("Landscape", 0.30f, 1.22f, 0.78f),
        Profile("Mono", 0.28f, 0f, 0.74f, mono = true),
        /** The camera maker's own profile stored in the DNG (tone curve and colour tables). */
        Profile("Camera", 0f, 1f, 0.92f, camera = true),
    )
    /**
     * RAW looks on top of the profile. 0 is the app's own look (unchanged). The others are measured:
     * the RAW sample test develops each camera's RAW files with the default look and fits the tone
     * curve ([curve]: output at inputs 0, 16 … 240, 255) and colour strength ([sat]) that turn it into
     * that camera's own picture, averaged per maker. [match] = fitted to this file's own preview.
     */
    class Look(val label: String, val group: String, val curve: IntArray? = null, val sat: Float = 1f, val match: Boolean = false,
               val ev: Float = 0f, val sh: Float = 0f, val hi: Float = 0f, val contrast: Float = 0f, val warm: Float = 0f)
    val LOOK_GROUPS = listOf("Phones", "Drones", "Cameras", "Action & 360°")
    val LOOKS: List<Look> = listOf(
        Look("Default", ""),
        Look("Match camera picture", "", match = true),
    ) + MeasuredLooks.ALL

    private fun expandCurve(c: IntArray): IntArray = IntArray(256) { v ->
        val f = v / 16f; val k = f.toInt().coerceAtMost(15); val t = f - k
        val a = c[k]; val b = c[(k + 1).coerceAtMost(16)]
        (a + (b - a) * t).roundToInt().coerceIn(0, 255)
    }

    /** White balance presets; 0 = as shot, 1 = auto, others a colour temperature in Kelvin. */
    val WB_PRESETS = listOf("As shot" to 0, "Auto" to 0, "Daylight" to 5500, "Cloudy" to 6500, "Shade" to 7500, "Tungsten" to 2850, "Fluorescent" to 3800)

    class UnsupportedRaw(msg: String) : Exception(msg)

    // ------------------------------------------------------------------ file access

    private class Src(context: Context, uri: Uri) : AutoCloseable {
        // Plain files (file://) are read directly — also what the automatic RAW sample tests use.
        private val raf = if (uri.scheme == "file") java.io.RandomAccessFile(uri.path ?: "", "r") else null
        private val pfd = if (raf != null) null else (context.contentResolver.openFileDescriptor(uri, "r") ?: throw java.io.IOException("can't open"))
        private val fis = pfd?.let { FileInputStream(it.fileDescriptor) }
        val ch: FileChannel = raf?.channel ?: fis!!.channel
        val size: Long = ch.size()
        fun bytes(pos: Long, n: Int): ByteArray {
            val b = ByteBuffer.allocate(n)
            var p = pos
            while (b.hasRemaining()) { val r = ch.read(b, p); if (r <= 0) break; p += r }
            return b.array()
        }
        override fun close() { try { raf?.close() } catch (_: Throwable) {}; try { fis?.close() } catch (_: Throwable) {}; try { pfd?.close() } catch (_: Throwable) {} }
    }

    private class Entry(val type: Int, val count: Int, val data: ByteArray)
    private class Ifd(val tags: Map<Int, Entry>, val order: ByteOrder) {
        fun has(t: Int) = tags.containsKey(t)
        fun nums(t: Int): DoubleArray? {
            val e = tags[t] ?: return null
            val bb = ByteBuffer.wrap(e.data).order(order)
            return DoubleArray(e.count) { i ->
                when (e.type) {
                    1, 7 -> (bb.get(i).toInt() and 0xFF).toDouble()
                    6 -> bb.get(i).toInt().toDouble()
                    3 -> (bb.getShort(i * 2).toInt() and 0xFFFF).toDouble()
                    8 -> bb.getShort(i * 2).toInt().toDouble()
                    4, 13 -> (bb.getInt(i * 4).toLong() and 0xFFFFFFFFL).toDouble()
                    9 -> bb.getInt(i * 4).toDouble()
                    5 -> { val a = bb.getInt(i * 8).toLong() and 0xFFFFFFFFL; val b = bb.getInt(i * 8 + 4).toLong() and 0xFFFFFFFFL; if (b == 0L) 0.0 else a.toDouble() / b }
                    10 -> { val a = bb.getInt(i * 8); val b = bb.getInt(i * 8 + 4); if (b == 0) 0.0 else a.toDouble() / b }
                    11 -> bb.getFloat(i * 4).toDouble()
                    12 -> bb.getDouble(i * 8)
                    else -> 0.0
                }
            }
        }
        fun num(t: Int, def: Double = 0.0): Double = nums(t)?.firstOrNull() ?: def
        fun raw(t: Int): ByteArray? = tags[t]?.data
    }

    private val TYPE_SIZE = intArrayOf(0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8, 4)

    private fun readIfd(src: Src, pos: Long, order: ByteOrder): Pair<Ifd, Long>? {
        if (pos <= 0 || pos + 2 > src.size) return null
        val n = ByteBuffer.wrap(src.bytes(pos, 2)).order(order).short.toInt() and 0xFFFF
        if (n == 0 || n > 4000) return null
        val buf = ByteBuffer.wrap(src.bytes(pos + 2, n * 12 + 4)).order(order)
        val tags = HashMap<Int, Entry>()
        for (i in 0 until n) {
            val tag = buf.getShort(i * 12).toInt() and 0xFFFF
            val type = buf.getShort(i * 12 + 2).toInt() and 0xFFFF
            val count = buf.getInt(i * 12 + 4)
            if (type !in 1..13 || count < 0) continue
            val len = TYPE_SIZE[type].toLong() * count
            if (len > 64L * 1024 * 1024) continue
            val data = if (len <= 4) ByteArray(len.toInt()).also { for (k in it.indices) it[k] = buf.get(i * 12 + 8 + k) }
            else {
                val off = buf.getInt(i * 12 + 8).toLong() and 0xFFFFFFFFL
                if (off + len > src.size) continue
                src.bytes(off, len.toInt())
            }
            tags[tag] = Entry(type, count, data)
        }
        val next = buf.getInt(n * 12).toLong() and 0xFFFFFFFFL
        return Ifd(tags, order) to next
    }

    // ------------------------------------------------------------------ metadata

    class Meta internal constructor(
        val uri: Uri,
        internal val order: ByteOrder,
        val width: Int, val height: Int,
        internal val spp: Int, internal val bps: Int, internal val compression: Int,
        internal val tileW: Int, internal val tileH: Int, internal val offsets: LongArray, internal val counts: LongArray, internal val rowsPerStrip: Int,
        /** Colour filter pattern (0 R, 1 G, 2 B), [cfaW]×[cfaH], relative to the active area; null = linear RGB. */
        internal val cfa: IntArray?, internal val cfaW: Int, internal val cfaH: Int,
        internal val blackRep: IntArray, internal val black: DoubleArray, internal val blackDH: DoubleArray?, internal val blackDV: DoubleArray?,
        internal val white: DoubleArray, internal val linTable: IntArray?,
        internal val activeTop: Int, internal val activeLeft: Int,
        internal val cropTop: Int, internal val cropLeft: Int, val cropW: Int, val cropH: Int,
        internal val gainMaps: List<GainMap>,
        /** Daylight-ish XYZ → camera matrix (kept for presets), or null. */
        internal val colorMatrix: DoubleArray?,
        internal val asShot: DoubleArray,
        val baselineExposure: Float,
        val orientation: Int,
        // ---- full DNG colour model
        internal val sampleFormat: Int = 1, internal val predictor: Int = 1,
        internal val cm1: DoubleArray? = null, internal val cm2: DoubleArray? = null,
        internal val cc1: DoubleArray? = null, internal val cc2: DoubleArray? = null,
        internal val fm1: DoubleArray? = null, internal val fm2: DoubleArray? = null,
        internal val illum1: Int = 6504, internal val illum2: Int = 0,
        internal val analog: DoubleArray? = null,
        internal val asShotXY: DoubleArray? = null,
        internal val hueSat: HsvTable? = null, internal val hueSat2: HsvTable? = null,
        internal val look: HsvTable? = null,
        internal val toneCurve: FloatArray? = null,
        internal val baselineOffset: Float = 0f,
        val profileName: String? = null,
        internal val ops1: List<Op> = emptyList(), internal val ops2: List<Op> = emptyList(),
        internal val warp: Warp? = null, internal val vignette: DoubleArray? = null,
        val info: List<Pair<String, String>> = emptyList(),
        /** DJI drones: lens calibration from the XMP (fx, fy, cx, cy offsets, k1, k2, p1, p2, k3), not yet applied. */
        internal val dewarp: DoubleArray? = null,
        /** DJI drones: flight / gimbal metadata from the XMP. */
        val drone: Map<String, String> = emptyMap(),
        /** GainMaps from OpcodeList3 (after demosaic, per colour) — DJI drones use this for lens shading. */
        internal val gains3: List<GainMap> = emptyList(),
        /** Camera maker (EXIF Make). */
        val make: String = "",
        /** Not a DNG: read through LibRaw (Sony, Canon, Nikon, Fujifilm… RAW files). */
        internal val libraw: Boolean = false,
        /** DNG 1.0 files: lossless-JPEG difference code 16 is followed by 16 extra bits (as old encoders wrote it). */
        internal val lj16Bits: Boolean = false,
    ) {
        /**
         * Phone RAWs saved without the camera's lens-shading data (e.g. by apps that don't ask the
         * camera for it): the dark corners are estimated from the picture and evened out the same way
         * as a file's own vignetting correction. Null when the file has its own data.
         */
        @Volatile internal var estVignette: DoubleArray? = null
        /**
         * Non-DNG RAWs carry no baseline exposure (DNGs do): the brightness lift that makes the
         * develop about as bright as the camera's own picture, measured once from its preview.
         */
        @Volatile internal var autoBaseline: Float = Float.NaN
        /** "Match camera picture": small develop to compare with the camera's preview, and the fitted tone curve / colour. */
        @Volatile internal var matchLin: Linear? = null
        @Volatile internal var camLut: IntArray? = null
        @Volatile internal var camSat: Float = 1f
        /** The dark-corner correction was estimated (the file had none). */
        val shadingEstimated: Boolean get() = estVignette != null
        /** The file has lens-shading data of its own. */
        val hasShading: Boolean get() = gainMaps.isNotEmpty() || vignette != null || gains3.isNotEmpty()
        /** Grey-world white balance from the preview, filled in by [preview]. */
        @Volatile internal var autoMul: FloatArray? = null
        /** Long side of the developed picture at full size. */
        val longSide: Int get() = max(cropW, cropH)
        /** The file has its own camera profile (tone curve / colour tables). */
        val hasCameraProfile: Boolean get() = hueSat != null || look != null || toneCurve != null
        /** The file carries lens corrections (distortion, colour fringing, vignetting). */
        val hasLensOps: Boolean get() = warp != null || vignette != null || dewarp != null || gains3.isNotEmpty() || estVignette != null
        val isDji: Boolean get() = drone.isNotEmpty()
    }

    internal class GainMap(
        val top: Int, val left: Int, val bottom: Int, val right: Int, val plane: Int, val planes: Int,
        val rowPitch: Int, val colPitch: Int, val pv: Int, val ph: Int,
        val spV: Double, val spH: Double, val orV: Double, val orH: Double, val mapPlanes: Int, val gains: FloatArray,
    )

    /** One DNG opcode (OpcodeList1 / 2) other than GainMap. */
    internal class Op(
        val id: Int, val top: Int, val left: Int, val bottom: Int, val right: Int, val plane: Int, val planes: Int,
        val rowPitch: Int, val colPitch: Int,
        val table: IntArray? = null, val poly: DoubleArray? = null, val values: FloatArray? = null,
        val constant: Int = 0, val phase: Int = 0, val points: IntArray? = null, val rects: IntArray? = null,
    )

    /** WarpRectilinear (OpcodeList3): radial kr0..kr3 and tangential kt0, kt1 per plane, centre (0..1). */
    internal class Warp(val coef: Array<DoubleArray>, val cx: Double, val cy: Double)

    /** ProfileHueSatMap / ProfileLookTable: hue shift (degrees), saturation and value scales. */
    internal class HsvTable(val hues: Int, val sats: Int, val vals: Int, val data: FloatArray, val srgbGamma: Boolean = false)

    /** True for files that look like DNGs (by name / type, confirmed by the DNGVersion tag). */
    fun isDng(context: Context, uri: Uri): Boolean = try {
        val type = context.contentResolver.getType(uri) ?: ""
        val name = (uri.lastPathSegment ?: "").lowercase()
        val hinted = type.contains("dng", true) || type.contains("x-adobe", true) || name.endsWith(".dng") || type == "image/x-raw" || type.isEmpty() || type == "application/octet-stream"
        hinted && Src(context, uri).use { s ->
            val h = s.bytes(0, 8)
            val order = when { h[0] == 'I'.code.toByte() && h[1] == 'I'.code.toByte() -> ByteOrder.LITTLE_ENDIAN; h[0] == 'M'.code.toByte() && h[1] == 'M'.code.toByte() -> ByteOrder.BIG_ENDIAN; else -> return@use false }
            val first = ByteBuffer.wrap(h).order(order).getInt(4).toLong() and 0xFFFFFFFFL
            readIfd(s, first, order)?.first?.has(50706) == true
        }
    } catch (_: Throwable) { false }

    private fun Ifd.text(t: Int): String? = raw(t)?.let { b -> String(b, Charsets.ISO_8859_1).trim('\u0000', ' ').takeIf { it.isNotEmpty() } }

    /** File name endings of camera RAW formats other than DNG (read through LibRaw). */
    val OTHER_RAW_EXTENSIONS: Set<String> get() = LibRawBridge.EXTENSIONS

    /** A RAW photo the app can develop: DNG, or another camera RAW format when LibRaw is available. */
    fun isRaw(context: Context, uri: Uri): Boolean = isDng(context, uri) || isOtherRaw(context, uri)

    /** Sony / Canon / Nikon / Fujifilm… RAW (by file name or type), read through LibRaw. */
    fun isOtherRaw(context: Context, uri: Uri): Boolean = try {
        LibRawBridge.available && run {
            val type = (context.contentResolver.getType(uri) ?: "").lowercase()
            val names = listOfNotNull(uri.lastPathSegment, if (uri.scheme == "file") null else Rename.displayName(context, uri))
            names.any { it.substringAfterLast('.', "").lowercase() in LibRawBridge.EXTENSIONS } ||
                LibRawBridge.EXTENSIONS.any { type.endsWith("-$it") }
        }
    } catch (_: Throwable) { false }

    private fun <T> withLibRaw(context: Context, uri: Uri, block: (Long) -> T): T {
        if (!LibRawBridge.available) throw UnsupportedRaw("This RAW format needs the RAW library, which couldn't be loaded")
        val pfd = if (uri.scheme == "file") null else (context.contentResolver.openFileDescriptor(uri, "r") ?: throw java.io.IOException("can't open"))
        try {
            val h = LibRawBridge.nOpen(if (pfd == null) uri.path ?: "" else "/proc/self/fd/${pfd.fd}")
            if (h == -2L) throw notSupported(uri)
            if (h <= 0L) throw UnsupportedRaw("This RAW file couldn't be read")
            try { return block(h) } finally { LibRawBridge.nClose(h) }
        } finally { try { pfd?.close() } catch (_: Throwable) {} }
    }

    private fun notSupported(uri: Uri) = UnsupportedRaw(
        if (uri.lastPathSegment.orEmpty().endsWith(".nef", true)) "This Nikon RAW type isn't supported yet (High Efficiency RAW) — Lossless compressed RAW works"
        else "This camera's RAW format isn't supported yet",
    )

    private fun libRawCamera(s: Array<String>): String {
        val make = s[0].trim(); val model = s[1].trim()
        return if (model.startsWith(make, true)) model else "$make $model".trim()
    }

    /** LibRaw's orientation → TIFF orientation. */
    private fun libRawOrientation(flip: Int): Int = when (flip) { 3 -> 3; 5 -> 8; 6 -> 6; else -> 1 }

    private fun openOther(context: Context, uri: Uri): Meta = withLibRaw(context, uri) { h ->
        val v = LibRawBridge.nInfo(h); val s = LibRawBridge.nStrings(h)
        val w = v[0].toInt(); val ht = v[1].toInt(); val filters = v[2].toLong(); val colors = v[3].toInt()
        if (w <= 0 || ht <= 0) throw UnsupportedRaw("This RAW file couldn't be read")
        if (v[29] != 0.0) throw UnsupportedRaw("Sigma Foveon RAW files aren't supported yet")
        var cfa: IntArray? = null; var cw = 2; var ch = 2
        if (filters != 0L) {
            if (colors != 3 && s[3] != "RGBG") throw UnsupportedRaw("This camera's colour filter (${s[3]}) isn't supported")
            val p = LibRawBridge.nPattern(h, 16).map { if (it == 3) 1 else it }
            if (p.any { it !in 0..2 }) throw UnsupportedRaw("This camera's colour filter isn't supported")
            if (filters == 9L) { cw = 6; ch = 6; cfa = IntArray(36) { p[(it / 6) * 16 + it % 6] } }
            else if ((0 until 16).all { y -> (0 until 16).all { x -> p[y * 16 + x] == p[(y % 2) * 16 + x % 2] } }) cfa = IntArray(4) { p[(it / 2) * 16 + it % 2] }
            else { ch = 16; cfa = IntArray(32) { p[(it / 2) * 16 + it % 2] } }
        }
        val cm = DoubleArray(9) { v[18 + it] }.takeIf { m -> m.any { it != 0.0 } }
        fun neutral(o: Int): DoubleArray? { val r = v[o]; val g = v[o + 1]; val b = v[o + 2]; return if (r > 0 && g > 0 && b > 0) doubleArrayOf(g / r, 1.0, g / b) else null }
        val asShot = neutral(10) ?: neutral(14) ?: doubleArrayOf(1.0, 1.0, 1.0)
        val info = ArrayList<Pair<String, String>>()
        info += "Camera" to libRawCamera(s)
        s[2].trim().takeIf { it.isNotEmpty() }?.let { info += "Lens" to it }
        v[7].takeIf { it > 0 }?.let { info += "Shutter" to (if (it < 1) "1/${(1 / it).roundToInt()} s" else "%.1f s".format(it)) }
        v[8].takeIf { it > 0 }?.let { info += "Aperture" to "f/%.1f".format(it) }
        v[6].takeIf { it > 0 }?.let { info += "ISO" to it.roundToInt().toString() }
        v[9].takeIf { it > 0 }?.let { info += "Focal length" to "%.1f mm".format(it) }
        info += "Sensor" to "${w}×${ht}, ${if (cfa == null) "linear RGB" else if (cw == 6) "X-Trans 6×6" else "Bayer"}"
        info += "Read by" to "LibRaw ${try { LibRawBridge.nVersion() } catch (_: Throwable) { "" }}".trim()
        Meta(
            uri, ByteOrder.LITTLE_ENDIAN, w, ht, if (cfa == null) 3 else 1, 16, 1,
            0, 0, LongArray(0), LongArray(0), ht,
            cfa, cw, ch,
            intArrayOf(1, 1), doubleArrayOf(0.0), null, null,
            doubleArrayOf(ONE.toDouble()), null,
            0, 0, 0, 0, w, ht,
            emptyList(), cm, asShot, 0f, libRawOrientation(v[4].toInt()),
            cm1 = cm, info = info, make = s[0].trim(), libraw = true,
        )
    }

    private fun decodeOther(context: Context, m: Meta): Sensor = withLibRaw(context, m.uri) { h ->
        val r = LibRawBridge.nUnpack(h)
        if (r == -2) throw notSupported(m.uri)
        if (r != 0) throw UnsupportedRaw("This RAW file couldn't be read")
        var d = LibRawBridge.nRaw(h, ONE.toInt()) ?: throw UnsupportedRaw("This RAW file's sensor data couldn't be read")
        val n = m.width * m.height
        if (m.cfa == null && d.size == n) { val g = d; d = ShortArray(n * 3) { g[it / 3] } } // monochrome sensor
        if (d.size != n * m.spp) throw UnsupportedRaw("This RAW file's sensor data couldn't be read")
        Sensor(m.width, m.height, m.spp, d, m.cfa, m.cfaW, m.cfaH)
    }

    private fun embeddedOther(context: Context, uri: Uri, minSide: Int): Embedded? = try {
        withLibRaw(context, uri) { h ->
            val t = LibRawBridge.nThumb(h) ?: return@withLibRaw null
            val bmp = when (t[0].toInt()) {
                1 -> jpeg(t.copyOfRange(1, t.size), minSide)
                2 -> {
                    val bb = ByteBuffer.wrap(t, 1, 8); val w = bb.int; val ht = bb.int
                    if (w <= 0 || ht <= 0 || t.size < 9 + w * ht * 3) null
                    else Bitmap.createBitmap(IntArray(w * ht) { i -> (0xFF shl 24) or ((t[9 + i * 3].toInt() and 0xFF) shl 16) or ((t[10 + i * 3].toInt() and 0xFF) shl 8) or (t[11 + i * 3].toInt() and 0xFF) }, w, ht, Bitmap.Config.ARGB_8888)
                }
                else -> null
            } ?: return@withLibRaw null
            val v = LibRawBridge.nInfo(h)
            // Previews stored the sensor's way up are turned like the RAW; ones already upright are kept.
            val o = libRawOrientation(v[4].toInt())
            val b = if (o != 1 && (bmp.width > bmp.height) == (v[0] > v[1])) orientBitmap(bmp, o) else bmp
            Embedded(b, libRawCamera(LibRawBridge.nStrings(h)))
        }
    } catch (_: Throwable) { null }

    fun open(context: Context, uri: Uri): Meta = run { appContext = context.applicationContext ?: context }.let { openAny(context, uri) }

    private fun openAny(context: Context, uri: Uri): Meta = if (!isDng(context, uri) && isOtherRaw(context, uri)) openOther(context, uri) else openDng(context, uri)

    private fun openDng(context: Context, uri: Uri): Meta = Src(context, uri).use { s ->
        val h = s.bytes(0, 8)
        val order = if (h[0] == 'M'.code.toByte()) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
        val first = ByteBuffer.wrap(h).order(order).getInt(4).toLong() and 0xFFFFFFFFL
        val all = ArrayList<Ifd>()
        var exif: Ifd? = null
        fun walk(pos: Long, depth: Int) {
            var p = pos; var guard = 0
            while (p > 0 && guard++ < 16) {
                val (ifd, next) = readIfd(s, p, order) ?: return
                all += ifd
                if (depth < 3) ifd.nums(330)?.forEach { walk(it.toLong(), depth + 1) }
                if (exif == null) ifd.nums(34665)?.firstOrNull()?.let { e -> exif = readIfd(s, e.toLong(), order)?.first }
                p = next
            }
        }
        walk(first, 0)
        val ifd0 = all.firstOrNull() ?: throw UnsupportedRaw("Not a DNG")
        val rawIfd = all.filter {
            val pi = it.num(262).toInt()
            (it.num(254, 0.0).toInt() and 1) == 0 && (pi == 32803 || pi == 34892) && it.has(256)
        }.maxByOrNull { it.num(256) * it.num(257) } ?: throw UnsupportedRaw("No raw image in this DNG")
        fun tag(t: Int) = rawIfd.nums(t) ?: ifd0.nums(t)

        val photometric = rawIfd.num(262).toInt()
        val spp = rawIfd.num(277, 1.0).toInt()
        val w = rawIfd.num(256).toInt(); val ht = rawIfd.num(257).toInt()
        val compression = rawIfd.num(259, 1.0).toInt()
        when (compression) {
            1, 7, 8, 34892 -> {}
            52546 -> if (!LibRawBridge.jxlAvailable) throw UnsupportedRaw("JPEG XL DNG (newer Galaxy / iPhone RAW) can't be decoded on this phone yet")
            else -> throw UnsupportedRaw("This DNG's compression ($compression) isn't supported yet")
        }
        val bps = rawIfd.num(258, 16.0).toInt()
        val sampleFormat = rawIfd.num(339, 1.0).toInt()
        val predictor = rawIfd.num(317, 1.0).toInt()
        var cfa: IntArray? = null
        var cfaW = 2; var cfaH = 2
        if (photometric == 32803) {
            if (spp != 1) throw UnsupportedRaw("Unusual sensor layout")
            val dim = rawIfd.nums(33421) ?: doubleArrayOf(2.0, 2.0)
            cfaH = dim[0].toInt(); cfaW = dim[1].toInt()
            if (cfaW !in 1..8 || cfaH !in 1..8) throw UnsupportedRaw("Unusual colour filter")
            val pat = rawIfd.nums(33422) ?: throw UnsupportedRaw("Missing colour filter pattern")
            if (pat.size < cfaW * cfaH) throw UnsupportedRaw("Missing colour filter pattern")
            // CFAPlaneColor maps plane numbers to R / G / B (default 0, 1, 2).
            val planeColor = rawIfd.nums(50710)?.map { it.toInt() } ?: listOf(0, 1, 2)
            cfa = IntArray(cfaW * cfaH) { planeColor.getOrElse(pat[it].toInt()) { pat[it].toInt() } }
            if (cfa.any { it !in 0..2 } || (0..2).any { c -> c !in cfa }) throw UnsupportedRaw("Only red / green / blue colour filters are supported")
        } else if (spp != 3 && spp != 1) throw UnsupportedRaw("Unusual sensor layout") // 1 = black-and-white sensor

        val tiled = rawIfd.has(322)
        val offsets = (if (tiled) rawIfd.nums(324) else rawIfd.nums(273))?.map { it.toLong() }?.toLongArray() ?: throw UnsupportedRaw("No image data")
        val counts = (if (tiled) rawIfd.nums(325) else rawIfd.nums(279))?.map { it.toLong() }?.toLongArray() ?: throw UnsupportedRaw("No image data")

        val rep = rawIfd.nums(50713)?.map { it.toInt() }?.toIntArray() ?: intArrayOf(1, 1)
        val black = rawIfd.nums(50714) ?: ifd0.nums(50714) ?: doubleArrayOf(0.0)
        val white = rawIfd.nums(50717) ?: ifd0.nums(50717) ?: doubleArrayOf(if (sampleFormat == 3) 1.0 else ((1L shl bps.coerceAtMost(31)) - 1).toDouble())
        val lin = rawIfd.nums(50712)?.map { it.toInt() }?.toIntArray()

        // Active area, then the default crop inside it.
        val aa = rawIfd.nums(50829)?.map { it.toInt() }
        val aTop = aa?.getOrNull(0) ?: 0; val aLeft = aa?.getOrNull(1) ?: 0
        val aH = (aa?.getOrNull(2) ?: ht) - aTop; val aW = (aa?.getOrNull(3) ?: w) - aLeft
        val co = rawIfd.nums(50719); val cs = rawIfd.nums(50720)
        var cTop = aTop + (co?.getOrNull(1)?.roundToInt() ?: 0)
        var cLeft = aLeft + (co?.getOrNull(0)?.roundToInt() ?: 0)
        var cW = (cs?.getOrNull(0)?.roundToInt() ?: aW).coerceAtMost(w - cLeft).coerceAtLeast(8)
        var cH = (cs?.getOrNull(1)?.roundToInt() ?: aH).coerceAtMost(ht - cTop).coerceAtLeast(8)
        // DefaultUserCrop (DNG 1.5): a further crop the camera / user set, as fractions.
        rawIfd.nums(51125)?.takeIf { it.size >= 4 }?.let { uc ->
            val t = (uc[0] * cH).roundToInt(); val l = (uc[1] * cW).roundToInt(); val b = (uc[2] * cH).roundToInt(); val r = (uc[3] * cW).roundToInt()
            if (b - t >= 8 && r - l >= 8 && (t > 0 || l > 0 || b < cH || r < cW)) { cTop += t; cLeft += l; cW = r - l; cH = b - t }
        }

        val ops1 = parseOps(rawIfd.raw(51008) ?: ifd0.raw(51008))
        val ops2 = parseOps(rawIfd.raw(51009) ?: ifd0.raw(51009))
        val gains = parseGainMaps(rawIfd.raw(51009) ?: ifd0.raw(51009))
        val (warp, vig) = parseOps3(rawIfd.raw(51022) ?: ifd0.raw(51022))
        val gains3 = parseGainMaps(rawIfd.raw(51022) ?: ifd0.raw(51022))

        // Colour: both calibration illuminants, interpolated later by the picture's white.
        fun m9(t: Int) = tag(t)?.takeIf { it.size >= 9 }?.copyOf(9)
        val cm1 = m9(50721); val cm2 = m9(50722)
        val il1 = illumK(ifd0.num(50778, 21.0).toInt()); val il2 = if (cm2 != null) illumK(ifd0.num(50779, 21.0).toInt()) else 0
        val cm = when {
            cm1 != null && cm2 != null -> if (kotlin.math.abs(il2 - 6504) < kotlin.math.abs(il1 - 6504)) cm2 else cm1
            else -> cm1 ?: cm2
        }
        val asShot = tag(50728)?.takeIf { it.size >= 3 && it.all { v -> v > 0 } }
        val asShotXY = tag(50729)?.takeIf { it.size >= 2 && it[0] > 0 && it[1] > 0 }
        val be = tag(50730)?.firstOrNull()?.toFloat() ?: 0f
        val orient = ifd0.num(274, 1.0).toInt()

        fun hsv(dimTag: Int, dataTag: Int, encTag: Int): HsvTable? {
            val d = tag(dimTag) ?: return null
            val data = tag(dataTag) ?: return null
            val hn = d.getOrNull(0)?.toInt() ?: 0; val sn = d.getOrNull(1)?.toInt() ?: 0; val vn = (d.getOrNull(2)?.toInt() ?: 1).coerceAtLeast(1)
            if (hn < 1 || sn < 2 || data.size < hn * sn * vn * 3) return null
            return HsvTable(hn, sn, vn, FloatArray(hn * sn * vn * 3) { data[it].toFloat() }, srgbGamma = (tag(encTag)?.firstOrNull() ?: 0.0) == 1.0)
        }
        val curve = tag(50940)?.takeIf { it.size >= 4 }?.map { it.toFloat() }?.toFloatArray()

        // Shooting details for the RAW INFO panel.
        val info = ArrayList<Pair<String, String>>()
        (ifd0.text(50708) ?: listOfNotNull(ifd0.text(271), ifd0.text(272)).joinToString(" ").ifBlank { null })?.let { info += "Camera" to it }
        exif?.text(42036)?.let { info += "Lens" to it }
        val ex = exif
        if (ex != null) {
            ex.nums(33434)?.firstOrNull()?.takeIf { it > 0 }?.let { info += "Shutter" to (if (it < 1) "1/${(1 / it).roundToInt()} s" else "%.1f s".format(it)) }
            ex.nums(33437)?.firstOrNull()?.takeIf { it > 0 }?.let { info += "Aperture" to "f/%.1f".format(it) }
            (ex.nums(34855) ?: ex.nums(34867))?.firstOrNull()?.takeIf { it > 0 }?.let { info += "ISO" to it.roundToInt().toString() }
            ex.nums(37386)?.firstOrNull()?.takeIf { it > 0 }?.let { info += "Focal length" to "%.1f mm".format(it) }
        }
        info += "Sensor" to "${w}×${ht}, ${if (cfa == null) "linear RGB" else if (cfaW == 2 && cfaH == 2) "Bayer" else if (cfaW == 6 && cfaH == 6) "X-Trans 6×6" else if (cfaW == 4 && cfaH == 4) "Quad Bayer 4×4" else "${cfaW}×${cfaH} CFA"}, ${if (sampleFormat == 3) "$bps-bit float" else "$bps-bit"}"
        info += "Compression" to when (compression) { 1 -> "Uncompressed"; 7 -> "Lossless JPEG"; 8 -> "Deflate" + if (sampleFormat == 3) " (floating point / HDR)" else ""; 34892 -> "Lossy JPEG"; else -> "$compression" }
        ifd0.nums(50706)?.takeIf { it.size >= 2 }?.let { v -> info += "DNG version" to v.take(4).joinToString(".") { it.toInt().toString() } }
        ifd0.text(50936)?.let { info += "Camera profile" to it }
        val extras = listOfNotNull(
            if (gains.isNotEmpty()) "lens shading" else null,
            if (warp != null) (if (warp.coef.size >= 3) "distortion + colour fringing" else "distortion") else null,
            if (vig != null || gains3.isNotEmpty()) "vignetting" else null,
            if (ops1.any { it.id == 4 || it.id == 5 }) "bad-pixel fix" else null,
        )
        // DJI drones: their own metadata block (drone-dji) in the XMP.
        val xmp = ifd0.raw(700)?.let { String(it, Charsets.UTF_8) } ?: ""
        val drone = LinkedHashMap<String, String>()
        if (xmp.contains("drone-dji")) {
            Regex("drone-dji:(\\w+)=\"([^\"]*)\"").findAll(xmp).forEach { drone[it.groupValues[1]] = it.groupValues[2] }
            Regex("<drone-dji:(\\w+)>([^<]*)</drone-dji:\\w+>").findAll(xmp).forEach { drone[it.groupValues[1]] = it.groupValues[2] }
        }
        var dewarp: DoubleArray? = null
        if (drone.isNotEmpty()) {
            val model = ifd0.text(272) ?: ""
            val name = productName(drone["ProductName"]) ?: djiName(model)
            info.add(0, "Drone" to (name?.let { if (model.isNotBlank()) "$it ($model)" else it } ?: "DJI $model"))
            fun d(k: String) = drone[k]?.trim()?.removePrefix("+")?.toDoubleOrNull()
            d("RelativeAltitude")?.let { info += "Height (take-off)" to "%.1f m".format(it) }
            d("AbsoluteAltitude")?.let { info += "Altitude (sea level)" to "%.1f m".format(it) }
            val lat = d("GpsLatitude"); val lon = d("GpsLongitude") ?: d("GpsLongtitude")
            if (lat != null && lon != null && (lat != 0.0 || lon != 0.0)) info += "GPS" to "%.6f, %.6f".format(java.util.Locale.US, lat, lon)
            d("GimbalPitchDegree")?.let { info += "Gimbal pitch" to "%.1f°".format(it) }
            d("GimbalYawDegree")?.let { info += "Gimbal yaw" to "%.1f°".format(it) }
            d("FlightYawDegree")?.let { info += "Drone heading" to "%.1f°".format(it) }
            val sp = listOfNotNull(d("FlightXSpeed"), d("FlightYSpeed"), d("FlightZSpeed"))
            if (sp.size == 3) info += "Flight speed" to "%.1f m/s".format(kotlin.math.sqrt(sp.sumOf { it * it }))
            // Lens calibration ("date;fx,fy,cx,cy,k1,k2,p1,p2,k3"); DewarpFlag 1 = already corrected.
            val flag = d("DewarpFlag")?.toInt() ?: 0
            drone["DewarpData"]?.substringAfter(';')?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() }?.takeIf { it.size >= 9 && it[0] > 100 }?.let { v ->
                if (flag == 0 && warp == null) { dewarp = v.take(9).toDoubleArray(); info += "Lens calibration" to "DJI calibration in the file (applied)" }
                else if (flag == 1) info += "Lens calibration" to "already corrected by the drone"
            }
        }
        if (extras.isNotEmpty()) info += "Corrections in the file" to extras.joinToString(", ")

        Meta(
            uri, order, w, ht, spp, bps, compression,
            if (tiled) rawIfd.num(322).toInt() else 0, if (tiled) rawIfd.num(323).toInt() else 0, offsets, counts,
            rawIfd.num(278, ht.toDouble()).toInt().coerceAtLeast(1),
            cfa, cfaW, cfaH, rep, black, rawIfd.nums(50715), rawIfd.nums(50716), white, lin,
            aTop, aLeft, cTop, cLeft, cW, cH, gains, cm, asShot ?: doubleArrayOf(1.0, 1.0, 1.0), be, orient,
            sampleFormat = sampleFormat, predictor = predictor,
            cm1 = cm1, cm2 = cm2, cc1 = m9(50723), cc2 = m9(50724), fm1 = m9(50964), fm2 = m9(50965),
            illum1 = il1, illum2 = il2, analog = tag(50727)?.takeIf { it.size >= 3 },
            asShotXY = if (asShot == null) asShotXY else null,
            hueSat = hsv(50937, 50938, 51107), hueSat2 = hsv(50937, 50939, 51107)?.takeIf { cm2 != null },
            look = hsv(50981, 50982, 51108), toneCurve = curve,
            baselineOffset = tag(51109)?.firstOrNull()?.toFloat() ?: 0f,
            profileName = ifd0.text(50936),
            ops1 = ops1, ops2 = ops2.filter { it.id != 9 }, warp = warp, vignette = vig, info = info, make = ifd0.text(271)?.trim() ?: "",
            dewarp = dewarp, drone = drone, gains3 = gains3,
            lj16Bits = (ifd0.nums(50706)?.let { v -> v.getOrElse(0) { 1.0 } * 256 + v.getOrElse(1) { 0.0 } } ?: 257.0) < 257.0,
        )
    }

    /** The drone's own product name from the XMP ("DJIMavic3Pro" → "Mavic 3 Pro"). */
    private fun productName(p: String?): String? {
        val t = p?.trim()?.removePrefix("DJI")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (t.contains(' ')) return t
        return t.replace(Regex("(?<=[a-z])(?=[A-Z0-9])|(?<=[0-9])(?=[A-Z])"), " ").replace("Air 2 S", "Air 2S").replace("Air 3 S", "Air 3S")
    }

    /** DJI camera model codes → drone names (the DNG only carries the camera code). */
    private fun djiName(model: String): String? = mapOf(
        "FC220" to "Mavic Pro", "FC2103" to "Mavic Air", "FC3170" to "Mavic Air 2", "FC3411" to "Air 2S",
        "FC8282" to "Air 3", "FC8284" to "Air 3 (tele)", "FC9113" to "Air 3S", "FC9184" to "Air 3S (tele)",
        "FC7203" to "Mavic Mini", "FC7303" to "Mini 2", "FC3582" to "Mini 3 Pro", "FC3682" to "Mini 3", "FC8482" to "Mini 4 Pro",
        "FC1102" to "Spark", "FC6310" to "Phantom 4 Pro", "FC6310S" to "Phantom 4 Pro V2", "FC330" to "Phantom 4",
        "L1D-20c" to "Mavic 2 Pro", "FC2204" to "Mavic 2 Zoom", "L2D-20c" to "Mavic 3", "FC4170" to "Mavic 3 Classic",
        "FC4370" to "Mavic 3 Pro", "FC4382" to "Mavic 3 Pro (tele)", "FC4280" to "Mavic 3 Pro (medium tele)",
        "FC4170A" to "Mavic 3 Classic", "M3E" to "Mavic 3 Enterprise", "M3T" to "Mavic 3 Thermal", "M3M" to "Mavic 3 Multispectral",
        "FC7703" to "Avata", "FC8671" to "Avata 2", "FC3277" to "DJI FPV", "FC9813" to "Neo",
        "ZH20T" to "Zenmuse H20T", "ZH20" to "Zenmuse H20", "ZenmuseP1" to "Zenmuse P1", "FC6520" to "Zenmuse X5S", "FC6540" to "Zenmuse X7",
    )[model.trim()]

    // ---- DNG camera profiles (.dcp): the colour part of a DNG in a file of its own.

    /** A camera profile read from a .dcp file. */
    class Dcp internal constructor(
        val name: String, val camera: String,
        internal val cm1: DoubleArray?, internal val cm2: DoubleArray?, internal val fm1: DoubleArray?, internal val fm2: DoubleArray?,
        internal val illum1: Int, internal val illum2: Int,
        internal val hueSat: HsvTable?, internal val hueSat2: HsvTable?, internal val look: HsvTable?, internal val toneCurve: FloatArray?,
        internal val baselineOffset: Float?,
    )

    /** Reads a .dcp (DNG Camera Profile) file; throws [UnsupportedRaw] when it isn't one. */
    fun readDcp(context: Context, uri: Uri): Dcp = Src(context, uri).use { s ->
        val h = s.bytes(0, 8)
        val order = if (h[0] == 'M'.code.toByte()) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
        val bb = ByteBuffer.wrap(h).order(order)
        val magic = bb.getShort(2).toInt() and 0xFFFF
        if ((h[0] != 'I'.code.toByte() && h[0] != 'M'.code.toByte()) || (magic != 0x4352 && magic != 42)) throw UnsupportedRaw("Not a camera profile (.dcp)")
        val (ifd, _) = readIfd(s, bb.getInt(4).toLong() and 0xFFFFFFFFL, order) ?: throw UnsupportedRaw("Not a camera profile (.dcp)")
        fun m9(t: Int) = ifd.nums(t)?.takeIf { it.size >= 9 }?.copyOf(9)
        val cm1 = m9(50721); val cm2 = m9(50722)
        if (cm1 == null && cm2 == null) throw UnsupportedRaw("This profile has no colour matrix")
        fun hsv(dimTag: Int, dataTag: Int, encTag: Int): HsvTable? {
            val d = ifd.nums(dimTag) ?: return null
            val data = ifd.nums(dataTag) ?: return null
            val hn = d.getOrNull(0)?.toInt() ?: 0; val sn = d.getOrNull(1)?.toInt() ?: 0; val vn = (d.getOrNull(2)?.toInt() ?: 1).coerceAtLeast(1)
            if (hn < 1 || sn < 2 || data.size < hn * sn * vn * 3) return null
            return HsvTable(hn, sn, vn, FloatArray(hn * sn * vn * 3) { data[it].toFloat() }, srgbGamma = (ifd.nums(encTag)?.firstOrNull() ?: 0.0) == 1.0)
        }
        Dcp(
            name = ifd.text(50936)?.trim()?.takeIf { it.isNotEmpty() } ?: "Camera profile",
            camera = ifd.text(50708)?.trim() ?: "",
            cm1 = cm1, cm2 = cm2, fm1 = m9(50964), fm2 = m9(50965),
            illum1 = illumK(ifd.num(50778, 21.0).toInt()), illum2 = if (cm2 != null) illumK(ifd.num(50779, 21.0).toInt()) else 0,
            hueSat = hsv(50937, 50938, 51107), hueSat2 = hsv(50937, 50939, 51107)?.takeIf { cm2 != null },
            look = hsv(50981, 50982, 51108), toneCurve = ifd.nums(50940)?.takeIf { it.size >= 4 }?.map { it.toFloat() }?.toFloatArray(),
            baselineOffset = ifd.nums(51109)?.firstOrNull()?.toFloat(),
        )
    }

    private val dcpMetas = android.util.LruCache<String, Meta>(4)

    /** [m] with the colour of the imported profile chosen in [dev] (or [m] itself). */
    private fun withDcp(m: Meta, dev: Dev): Meta {
        val id = dev.dcp ?: return m
        val key = "${System.identityHashCode(m)}/$id"
        dcpMetas.get(key)?.let { return it }
        val d = DcpStore.get(appContext ?: return m, id) ?: return m
        val one = d.cm2 == null
        val out = Meta(
            m.uri, m.order, m.width, m.height, m.spp, m.bps, m.compression, m.tileW, m.tileH, m.offsets, m.counts, m.rowsPerStrip,
            m.cfa, m.cfaW, m.cfaH, m.blackRep, m.black, m.blackDH, m.blackDV, m.white, m.linTable,
            m.activeTop, m.activeLeft, m.cropTop, m.cropLeft, m.cropW, m.cropH, m.gainMaps,
            d.cm2?.takeIf { kotlin.math.abs(d.illum2 - 6504) < kotlin.math.abs(d.illum1 - 6504) } ?: d.cm1 ?: d.cm2, m.asShot, m.baselineExposure, m.orientation,
            sampleFormat = m.sampleFormat, predictor = m.predictor,
            cm1 = d.cm1 ?: d.cm2, cm2 = if (one) null else d.cm2, cc1 = if (one) m.cc1 else m.cc1, cc2 = if (one) null else m.cc2,
            fm1 = d.fm1, fm2 = if (one) null else d.fm2, illum1 = if (d.cm1 != null) d.illum1 else d.illum2, illum2 = if (one) 0 else d.illum2,
            analog = m.analog, asShotXY = m.asShotXY,
            hueSat = d.hueSat, hueSat2 = d.hueSat2, look = d.look, toneCurve = d.toneCurve,
            baselineOffset = d.baselineOffset ?: m.baselineOffset, profileName = d.name,
            ops1 = m.ops1, ops2 = m.ops2, warp = m.warp, vignette = m.vignette, info = m.info,
            dewarp = m.dewarp, drone = m.drone, gains3 = m.gains3, make = m.make, libraw = m.libraw, lj16Bits = m.lj16Bits,
        )
        out.estVignette = m.estVignette; out.autoBaseline = m.autoBaseline; out.autoMul = m.autoMul
        out.matchLin = m.matchLin; out.camLut = m.camLut; out.camSat = m.camSat
        dcpMetas.put(key, out)
        return out
    }

    private fun illumK(code: Int): Int = when (code) {
        17, 3, 24 -> 2856; 18 -> 4874; 19 -> 6774; 20 -> 5503; 21, 10 -> 6504; 22, 11 -> 7504; 23 -> 5003
        2, 14 -> 4200; 12 -> 6400; 13 -> 5000; 15 -> 3450; else -> 5500
    }

    /** Opcodes in a list (big-endian): count, then id, version, flags, size, parameters. */
    private fun opcodes(b: ByteArray?): List<Pair<Int, ByteBuffer>> {
        if (b == null || b.size < 4) return emptyList()
        val bb = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN)
        val out = ArrayList<Pair<Int, ByteBuffer>>()
        try {
            val n = bb.int
            repeat(n.coerceAtMost(256)) {
                val id = bb.int; bb.int; bb.int; val size = bb.int
                if (size < 0 || size > bb.remaining()) return out
                val p = ByteBuffer.wrap(b, bb.position(), size).slice().order(ByteOrder.BIG_ENDIAN)
                out += id to p
                bb.position(bb.position() + size)
            }
        } catch (_: Throwable) {}
        return out
    }

    private fun parseOps(b: ByteArray?): List<Op> = opcodes(b).mapNotNull { (id, p) ->
        try {
            when (id) {
                4 -> Op(id, 0, 0, Int.MAX_VALUE, Int.MAX_VALUE, 0, 1, 1, 1, constant = p.int, phase = p.int)
                5 -> {
                    val phase = p.int; val np = p.int; val nr = p.int
                    if (np > 2_000_000 || nr > 200_000) null
                    else Op(id, 0, 0, Int.MAX_VALUE, Int.MAX_VALUE, 0, 1, 1, 1, phase = phase,
                        points = IntArray(np * 2) { p.int }, rects = IntArray(nr * 4) { p.int })
                }
                6 -> Op(id, p.int, p.int, p.int, p.int, 0, 0, 1, 1)
                7, 8, 10, 11, 12, 13 -> {
                    val top = p.int; val left = p.int; val bottom = p.int; val right = p.int
                    val plane = p.int; val planes = p.int; val rp = p.int.coerceAtLeast(1); val cp = p.int.coerceAtLeast(1)
                    when (id) {
                        7 -> { val n = p.int; if (n !in 1..65536) null else Op(id, top, left, bottom, right, plane, planes, rp, cp, table = IntArray(n) { p.short.toInt() and 0xFFFF }) }
                        8 -> { val d = p.int; if (d !in 0..8) null else Op(id, top, left, bottom, right, plane, planes, rp, cp, poly = DoubleArray(d + 1) { p.double }) }
                        else -> { val n = p.int; if (n !in 1..200_000) null else Op(id, top, left, bottom, right, plane, planes, rp, cp, values = FloatArray(n) { p.float }) }
                    }
                }
                else -> null   // 9 (GainMap) is read separately; unknown optional ops are skipped.
            }
        } catch (_: Throwable) { null }
    }

    private fun parseOps3(b: ByteArray?): Pair<Warp?, DoubleArray?> {
        var warp: Warp? = null; var vig: DoubleArray? = null
        for ((id, p) in opcodes(b)) try {
            when (id) {
                1 -> { val n = p.int; if (n in 1..4) { val c = Array(n) { DoubleArray(6) { p.double } }; warp = Warp(c, p.double, p.double) } }
                3 -> vig = DoubleArray(7) { p.double }   // k0..k4, cx, cy
            }
        } catch (_: Throwable) {}
        return warp to vig
    }

    private fun parseGainMaps(b: ByteArray?): List<GainMap> {
        val out = ArrayList<GainMap>()
        for ((id, bb) in opcodes(b)) {
            if (id != 9) continue
            try {
                val top = bb.int; val left = bb.int; val bottom = bb.int; val right = bb.int
                val plane = bb.int; val planes = bb.int; val rp = bb.int; val cp = bb.int
                val pv = bb.int; val ph = bb.int
                val sv = bb.double; val sh = bb.double; val ov = bb.double; val oh = bb.double
                val mp = bb.int
                val cnt = pv * ph * mp
                if (cnt in 1..1_000_000) {
                    val g = FloatArray(cnt) { bb.float }
                    out += GainMap(top, left, bottom, right, plane, planes, rp.coerceAtLeast(1), cp.coerceAtLeast(1), pv, ph, sv, sh, ov, oh, mp, g)
                }
            } catch (_: Throwable) {}
        }
        return out
    }

    // ------------------------------------------------------------------ sensor decode

    /** Normalised sensor values, cropped: 0..1 ⇒ 0..[ONE] (headroom for lens-shading gains). */
    internal class Sensor(val w: Int, val h: Int, val spp: Int, val data: ShortArray, val cfa: IntArray?, val cw: Int = 2, val ch: Int = 2) {
        fun v(i: Int): Float = (data[i].toInt() and 0xFFFF) / ONE
        /** Colour (0 R, 1 G, 2 B) at x, y. */
        fun color(x: Int, y: Int): Int = cfa!![(y % ch) * cw + (x % cw)]
        val bayer: Boolean get() = cfa != null && cw == 2 && ch == 2
    }
    private const val ONE = 16383f

    private fun half(h: Int): Float {
        val s = (h shr 15) and 1; val e = (h shr 10) and 0x1F; val m = h and 0x3FF
        val v = when (e) { 0 -> m / 1024f * 2f.pow(-14); 31 -> if (m == 0) Float.POSITIVE_INFINITY else Float.NaN; else -> (1f + m / 1024f) * 2f.pow(e - 15) }
        return if (s == 1) -v else v
    }
    /** DNG 24-bit float: sign, 7-bit exponent (bias 63), 16-bit mantissa. */
    private fun f24(v: Int): Float {
        val s = (v shr 23) and 1; val e = (v shr 16) and 0x7F; val m = v and 0xFFFF
        if (e == 0 && m == 0) return 0f
        val f = java.lang.Float.intBitsToFloat((s shl 31) or ((e - 63 + 127) shl 23) or (m shl 7))
        return if (e == 0x7F) Float.POSITIVE_INFINITY else f
    }

    /** Undoes the TIFF / DNG predictors on one decoded tile or strip (rows of [cols] × [spp] samples). */
    private fun unpredict(b: ByteArray, cols: Int, rows: Int, spp: Int, bytesPer: Int, predictor: Int, order: ByteOrder) {
        val rowSamples = cols * spp
        when (predictor) {
            2, 34892, 34893 -> {
                val step = when (predictor) { 34892 -> 2; 34893 -> 4; else -> 1 } * spp
                val bb = ByteBuffer.wrap(b).order(order)
                for (r in 0 until rows) {
                    val base = r * rowSamples
                    for (c in step until rowSamples) {
                        val i = base + c; val j = i - step
                        when (bytesPer) {
                            1 -> if (i < b.size) b[i] = (b[i] + b[j]).toByte()
                            2 -> if (i * 2 + 1 < b.size) bb.putShort(i * 2, (bb.getShort(i * 2) + bb.getShort(j * 2)).toShort())
                            4 -> if (i * 4 + 3 < b.size) bb.putInt(i * 4, bb.getInt(i * 4) + bb.getInt(j * 4))
                        }
                    }
                }
            }
            3, 34894, 34895 -> {
                // Floating point predictor: byte-wise differences, then the bytes are de-interleaved.
                val step = when (predictor) { 34894 -> 2; 34895 -> 4; else -> 1 } * spp
                val rowBytes = rowSamples * bytesPer
                val tmp = ByteArray(rowBytes)
                for (r in 0 until rows) {
                    val o = r * rowBytes
                    if (o + rowBytes > b.size) break
                    for (k in step until rowBytes) b[o + k] = (b[o + k] + b[o + k - step]).toByte()
                    System.arraycopy(b, o, tmp, 0, rowBytes)
                    for (c in 0 until rowSamples) for (k in 0 until bytesPer) {
                        // Stored most significant byte first; rebuild in big-endian order.
                        b[o + c * bytesPer + k] = tmp[k * rowSamples + c]
                    }
                }
            }
        }
    }

    internal fun decode(context: Context, m: Meta): Sensor = if (m.libraw) decodeOther(context, m) else Src(context, m.uri).use { s ->
        val w = m.width; val h = m.height; val spp = m.spp
        val raw = ShortArray(w * h * spp)
        val isFloat = m.sampleFormat == 3
        // Floats are stored scaled to 16 bits against the white level (0 … white ⇒ 0 … 65535).
        val fWhite = (m.white.firstOrNull() ?: 1.0).coerceAtLeast(1e-6)
        fun put(x: Int, y: Int, c: Int, v: Int) { if (x in 0 until w && y in 0 until h) raw[(y * w + x) * spp + c] = v.toShort() }
        fun putF(x: Int, y: Int, c: Int, f: Float) { put(x, y, c, (f / fWhite * 65535.0).roundToInt().coerceIn(0, 65535)) }
        val tiled = m.tileW > 0
        val tw = if (tiled) m.tileW else w
        val th = if (tiled) m.tileH else m.rowsPerStrip
        val across = if (tiled) (w + tw - 1) / tw else 1
        for (k in m.offsets.indices) {
            var bytes = s.bytes(m.offsets[k], m.counts.getOrElse(k) { 0L }.toInt().coerceAtLeast(0))
            val x0 = if (tiled) (k % across) * tw else 0
            val y0 = if (tiled) (k / across) * th else k * th
            val rowsHere = if (tiled) th else min(th, h - y0)
            when (m.compression) {
                7 -> {
                    val lj = LJpeg(bytes, m.lj16Bits).decode()
                    val samplesPerRow = tw * spp
                    for (i in 0 until min(lj.size, samplesPerRow * th)) {
                        val r = i / samplesPerRow; val c = i % samplesPerRow
                        put(x0 + c / spp, y0 + r, c % spp, lj[i])
                    }
                    continue
                }
                52546 -> {
                    // JPEG XL (DNG 1.7): each tile / strip is a JPEG XL image.
                    val wh = IntArray(2)
                    val px = LibRawBridge.nJxl(bytes, spp, wh) ?: throw UnsupportedRaw("This JPEG XL DNG couldn't be decoded")
                    val pw = wh[0]; val ph = wh[1]
                    for (r in 0 until min(ph, th)) for (c in 0 until min(pw, tw)) for (k in 0 until spp) put(x0 + c, y0 + r, k, px[(r * pw + c) * spp + k].toInt() and 0xFFFF)
                    continue
                }
                34892 -> {
                    // Lossy DNG: each tile is an ordinary 8-bit JPEG (the linearisation table expands it).
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                    val px = IntArray(bmp.width * bmp.height)
                    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                    for (yy in 0 until bmp.height) for (xx in 0 until bmp.width) {
                        val c = px[yy * bmp.width + xx]
                        if (spp >= 3) {
                            put(x0 + xx, y0 + yy, 0, (c shr 16) and 0xFF); put(x0 + xx, y0 + yy, 1, (c shr 8) and 0xFF); put(x0 + xx, y0 + yy, 2, c and 0xFF)
                        } else put(x0 + xx, y0 + yy, 0, (c shr 8) and 0xFF)
                    }
                    bmp.recycle()
                    continue
                }
                8 -> {
                    val inf = java.util.zip.Inflater()
                    inf.setInput(bytes)
                    val bytesPer = (m.bps + 7) / 8
                    val out = ByteArray(tw * rowsHere * spp * bytesPer)
                    var n = 0
                    while (n < out.size && !inf.finished()) { val r = inf.inflate(out, n, out.size - n); if (r == 0 && (inf.needsInput() || inf.needsDictionary())) break; n += r }
                    inf.end()
                    unpredict(out, tw, rowsHere, spp, bytesPer, m.predictor, if (m.predictor == 3 || m.predictor >= 34894) ByteOrder.BIG_ENDIAN else m.order)
                    bytes = out
                }
            }
            // Uncompressed or inflated samples.
            val fpOrder = if (m.compression == 8 && (m.predictor == 3 || m.predictor >= 34894)) ByteOrder.BIG_ENDIAN else m.order
            val bb = ByteBuffer.wrap(bytes).order(fpOrder)
            val rowLen = tw * spp
            if (isFloat) {
                val bp = m.bps / 8
                val n = bytes.size / bp.coerceAtLeast(1)
                for (i in 0 until n) {
                    val r = i / rowLen; val c = i % rowLen
                    if (r >= rowsHere) break
                    val f = when (m.bps) {
                        16 -> half(bb.getShort(i * 2).toInt() and 0xFFFF)
                        24 -> f24(if (fpOrder == ByteOrder.BIG_ENDIAN) ((bytes[i * 3].toInt() and 0xFF) shl 16) or ((bytes[i * 3 + 1].toInt() and 0xFF) shl 8) or (bytes[i * 3 + 2].toInt() and 0xFF)
                            else ((bytes[i * 3 + 2].toInt() and 0xFF) shl 16) or ((bytes[i * 3 + 1].toInt() and 0xFF) shl 8) or (bytes[i * 3].toInt() and 0xFF))
                        else -> bb.getFloat(i * 4)
                    }
                    putF(x0 + c / spp, y0 + r, c % spp, if (f.isNaN()) 0f else f)
                }
                continue
            }
            when (m.bps) {
                16 -> { var i = 0; val n = bytes.size / 2; while (i < n) { val r = i / rowLen; val c = i % rowLen; put(x0 + c / spp, y0 + r, c % spp, bb.getShort(i * 2).toInt() and 0xFFFF); i++ } }
                32 -> { var i = 0; val n = bytes.size / 4; while (i < n) { val r = i / rowLen; val c = i % rowLen; put(x0 + c / spp, y0 + r, c % spp, ((bb.getInt(i * 4).toLong() and 0xFFFFFFFFL) shr 16).toInt()); i++ } }
                8 -> for (i in bytes.indices) { val r = i / rowLen; val c = i % rowLen; put(x0 + c / spp, y0 + r, c % spp, bytes[i].toInt() and 0xFF) }
                else -> {
                    // Packed 10 / 12 / 14 bits, most significant bit first, rows byte-aligned.
                    val rowBytes = (rowLen * m.bps + 7) / 8
                    var r = 0
                    while ((r + 1) * rowBytes <= bytes.size && r < th) {
                        var bit = r.toLong() * rowBytes * 8
                        for (c in 0 until rowLen) {
                            var v = 0
                            for (b in 0 until m.bps) { val byte = bytes[(bit ushr 3).toInt()].toInt(); v = (v shl 1) or ((byte shr (7 - (bit and 7).toInt())) and 1); bit++ }
                            put(x0 + c / spp, y0 + r, c % spp, v)
                        }
                        r++
                    }
                }
            }
        }
        // Colour at a stored position (pattern starts at the active area's corner).
        fun colAt(x: Int, y: Int): Int = m.cfa?.let { p -> p[(((y - m.activeTop) % m.cfaH + m.cfaH) % m.cfaH) * m.cfaW + ((((x - m.activeLeft) % m.cfaW) + m.cfaW) % m.cfaW)] } ?: 0
        // OpcodeList1: on the raw values (bad pixels, maps, per-row / column fixes).
        applyOps(m.ops1, raw, w, h, spp, if (m.bps >= 16 || isFloat) 65535f else ((1 shl m.bps) - 1).toFloat(), ::colAt)
        // Linearise, black / white levels, normalise — in place (value × ONE, headroom for gains).
        val repR = m.blackRep.getOrElse(0) { 1 }.coerceAtLeast(1); val repC = m.blackRep.getOrElse(1) { 1 }.coerceAtLeast(1)
        val lt = m.linTable
        val wScale = if (isFloat) 65535.0 / fWhite else 1.0
        for (y in 0 until h) for (x in 0 until w) for (c in 0 until spp) {
            val i = (y * w + x) * spp + c
            var v = raw[i].toInt() and 0xFFFF
            if (lt != null && lt.isNotEmpty()) v = lt[v.coerceAtMost(lt.size - 1)]
            val bi = (((y % repR) * repC + (x % repC)) * spp + c).coerceAtMost(m.black.size - 1)
            var bl = m.black[bi]
            m.blackDH?.let { if (x < it.size) bl += it[x] }
            m.blackDV?.let { if (y < it.size) bl += it[y] }
            bl *= wScale
            val wl = m.white[c.coerceAtMost(m.white.size - 1)] * wScale
            val f = ((v - bl) / (wl - bl).coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
            raw[i] = (f * ONE).roundToInt().toShort()
        }
        // OpcodeList2: maps / deltas / scales on the 0..1 data, then the lens-shading gain maps.
        applyOps(m.ops2, raw, w, h, spp, ONE, ::colAt)
        for (g in m.gainMaps) {
            val hh = (g.bottom - g.top).coerceAtLeast(1).toDouble(); val ww = (g.right - g.left).coerceAtLeast(1).toDouble()
            var y = g.top
            while (y < min(g.bottom, h)) {
                val gv = (((y - g.top) / hh) - g.orV) / g.spV.coerceAtLeast(1e-9)
                val v0 = gv.toInt().coerceIn(0, max(0, g.pv - 1)); val v1 = (v0 + 1).coerceAtMost(g.pv - 1); val fv = (gv - v0).toFloat().coerceIn(0f, 1f)
                var x = g.left
                while (x < min(g.right, w)) {
                    val gh = (((x - g.left) / ww) - g.orH) / g.spH.coerceAtLeast(1e-9)
                    val h0 = gh.toInt().coerceIn(0, max(0, g.ph - 1)); val h1 = (h0 + 1).coerceAtMost(g.ph - 1); val fh = (gh - h0).toFloat().coerceIn(0f, 1f)
                    for (p in g.plane until min(g.plane + g.planes, spp)) {
                        val mp = if (g.mapPlanes > 1) (p - g.plane).coerceAtMost(g.mapPlanes - 1) else 0
                        fun at(a: Int, b: Int) = g.gains[(a * g.ph + b) * g.mapPlanes + mp]
                        val gain = (at(v0, h0) * (1 - fh) + at(v0, h1) * fh) * (1 - fv) + (at(v1, h0) * (1 - fh) + at(v1, h1) * fh) * fv
                        val i = (y * w + x) * spp + p
                        raw[i] = ((raw[i].toInt() and 0xFFFF) * gain).roundToInt().coerceIn(0, 65535).toShort()
                    }
                    x += g.colPitch
                }
                y += g.rowPitch
            }
        }
        // Crop to the picture area (keeping the colour pattern's phase).
        val cw = m.cropW; val ch = m.cropH
        val out = if (cw == w && ch == h && m.cropTop == 0 && m.cropLeft == 0) raw else {
            val o = ShortArray(cw * ch * spp)
            for (y in 0 until ch) System.arraycopy(raw, ((y + m.cropTop) * w + m.cropLeft) * spp, o, y * cw * spp, cw * spp)
            o
        }
        val pat = m.cfa?.let { IntArray(m.cfaW * m.cfaH) { k -> colAt((k % m.cfaW) + m.cropLeft, (k / m.cfaW) + m.cropTop) } }
        // Black-and-white sensors: the same value in all three channels.
        if (pat == null && spp == 1) return@use Sensor(cw, ch, 3, ShortArray(out.size * 3) { out[it / 3] }, null, m.cfaW, m.cfaH)
        Sensor(cw, ch, spp, out, pat, m.cfaW, m.cfaH)
    }

    /** Applies OpcodeList1 / 2 operations in place; [one] is the value of white in [raw]. */
    private fun applyOps(ops: List<Op>, raw: ShortArray, w: Int, h: Int, spp: Int, one: Float, colAt: (Int, Int) -> Int) {
        for (op in ops) {
            val top = op.top.coerceAtLeast(0); val left = op.left.coerceAtLeast(0)
            val bottom = op.bottom.coerceAtMost(h); val right = op.right.coerceAtMost(w)
            val p0 = op.plane.coerceIn(0, spp - 1); val p1 = min(spp, op.plane + op.planes.coerceAtLeast(1))
            fun each(f: (Int, Int, Int) -> Unit) {
                var y = top
                while (y < bottom) { var x = left; while (x < right) { for (p in p0 until p1) f(x, y, p); x += op.colPitch }; y += op.rowPitch }
            }
            fun get(i: Int) = raw[i].toInt() and 0xFFFF
            fun set(i: Int, v: Float) { raw[i] = v.roundToInt().coerceIn(0, 65535).toShort() }
            when (op.id) {
                7 -> { val t = op.table!!; each { x, y, p -> val i = (y * w + x) * spp + p; raw[i] = t[get(i).coerceAtMost(t.size - 1)].toShort() } }
                8 -> { val c = op.poly!!; each { x, y, p ->
                    val i = (y * w + x) * spp + p; val v = get(i) / one; var r = 0.0; var xp = 1.0
                    for (k in c.indices) { r += c[k] * xp; xp *= v }
                    set(i, (r * one).toFloat())
                } }
                10 -> { val d = op.values!!; each { x, y, p -> val k = (y - top) / op.rowPitch; if (k < d.size) { val i = (y * w + x) * spp + p; set(i, get(i) + d[k] * one) } } }
                11 -> { val d = op.values!!; each { x, y, p -> val k = (x - left) / op.colPitch; if (k < d.size) { val i = (y * w + x) * spp + p; set(i, get(i) + d[k] * one) } } }
                12 -> { val d = op.values!!; each { x, y, p -> val k = (y - top) / op.rowPitch; if (k < d.size) { val i = (y * w + x) * spp + p; set(i, get(i) * d[k]) } } }
                13 -> { val d = op.values!!; each { x, y, p -> val k = (x - left) / op.colPitch; if (k < d.size) { val i = (y * w + x) * spp + p; set(i, get(i) * d[k]) } } }
                4, 5 -> {
                    if (spp != 1) continue
                    // Bad pixels: listed ones, or ones stuck at the constant — filled from same-colour neighbours.
                    val bad = HashSet<Int>()
                    if (op.id == 4) { for (i in 0 until w * h) if (get(i) == op.constant) bad += i }
                    else {
                        op.points?.let { pts -> for (k in 0 until pts.size / 2) { val y = pts[k * 2]; val x = pts[k * 2 + 1]; if (x in 0 until w && y in 0 until h) bad += y * w + x } }
                        op.rects?.let { rs -> for (k in 0 until rs.size / 4) for (y in max(0, rs[k * 4]) until min(h, rs[k * 4 + 2])) for (x in max(0, rs[k * 4 + 1]) until min(w, rs[k * 4 + 3])) bad += y * w + x }
                    }
                    if (bad.size > w * h / 10) continue
                    for (i in bad) {
                        val x = i % w; val y = i / w; val c = colAt(x, y)
                        var sum = 0; var n = 0
                        for (dy in -2..2) for (dx in -2..2) {
                            if (dx == 0 && dy == 0) continue
                            val xx = x + dx; val yy = y + dy
                            if (xx !in 0 until w || yy !in 0 until h) continue
                            val j = yy * w + xx
                            if (j in bad || colAt(xx, yy) != c) continue
                            sum += get(j); n++
                        }
                        if (n > 0) raw[i] = (sum / n).toShort()
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ lossless JPEG (ITU T.81, process 14)

    private class LJpeg(val d: ByteArray, val legacy16: Boolean = false) {
        private var p = 0
        private var bitBuf = 0L; private var bitCnt = 0
        private class Huff(val lookup: IntArray, val maxCode: IntArray, val valPtr: IntArray, val minCode: IntArray, val vals: IntArray)
        private val tables = arrayOfNulls<Huff>(4)

        private fun u8() = d[p++].toInt() and 0xFF
        private fun u16(): Int { val a = u8(); return (a shl 8) or u8() }

        fun decode(): IntArray {
            if (u16() != 0xFFD8) throw UnsupportedRaw("Bad compressed RAW data")
            var w = 0; var h = 0; var comps = 0; var prec = 16
            var compIds = IntArray(0)
            var restart = 0
            while (p < d.size - 1) {
                var mk = u8(); if (mk != 0xFF) continue
                mk = u8(); while (mk == 0xFF) mk = u8()
                when (mk) {
                    0xC3 -> { val len = u16(); val end = p + len - 2; prec = u8(); h = u16(); w = u16(); comps = u8(); compIds = IntArray(comps) { val id = u8(); u8(); u8(); id }; p = end }
                    0xC4 -> {
                        val len = u16(); val end = p + len - 2
                        while (p < end) {
                            val tc = u8(); val counts = IntArray(17); var tot = 0
                            for (i in 1..16) { counts[i] = u8(); tot += counts[i] }
                            val vals = IntArray(tot) { u8() }
                            tables[tc and 3] = build(counts, vals)
                        }
                        p = end
                    }
                    0xDD -> { u16(); restart = u16() }
                    0xDA -> {
                        val len = u16(); val end = p + len - 2
                        val ns = u8(); val tsel = IntArray(ns)
                        for (i in 0 until ns) { val cid = u8(); val t = u8(); val idx = compIds.indexOf(cid).coerceAtLeast(i); if (idx < ns) tsel[idx] = t shr 4 }
                        val pred = u8(); u8(); val pt = u8() and 0xF
                        p = end
                        return scan(w, h, ns, tsel, pred, pt, prec, restart)
                    }
                    0xD9 -> break
                    else -> { val len = u16(); p += len - 2 }
                }
            }
            throw UnsupportedRaw("Bad compressed RAW data")
        }

        private fun build(counts: IntArray, vals: IntArray): Huff {
            val maxCode = IntArray(18) { -1 }; val minCode = IntArray(17); val valPtr = IntArray(17)
            var code = 0; var k = 0
            for (l in 1..16) {
                valPtr[l] = k; minCode[l] = code
                code += counts[l]; k += counts[l]
                maxCode[l] = if (counts[l] > 0) code - 1 else -1
                code = code shl 1
            }
            maxCode[17] = Int.MAX_VALUE
            return Huff(IntArray(0), maxCode, valPtr, minCode, vals)
        }

        private fun fill() {
            while (bitCnt <= 48) {
                var b = 0
                if (p < d.size) {
                    b = d[p].toInt() and 0xFF
                    if (b == 0xFF) {
                        val nx = if (p + 1 < d.size) d[p + 1].toInt() and 0xFF else 0
                        if (nx == 0x00) { p += 2 } else { b = 0 /* marker: feed zeros */ }
                    } else p++
                }
                bitBuf = (bitBuf shl 8) or b.toLong(); bitCnt += 8
            }
        }
        private fun bits(n: Int): Int {
            if (n == 0) return 0
            if (bitCnt < n) fill()
            val v = ((bitBuf ushr (bitCnt - n)) and ((1L shl n) - 1)).toInt()
            bitCnt -= n
            return v
        }
        private fun huff(t: Huff): Int {
            var code = bits(1); var l = 1
            while (l < 16 && code > t.maxCode[l]) { code = (code shl 1) or bits(1); l++ }
            if (code > t.maxCode[l]) return 0
            return t.vals[(t.valPtr[l] + code - t.minCode[l]).coerceIn(0, t.vals.size - 1)]
        }
        private fun diff(t: Huff): Int {
            val s = huff(t)
            if (s == 0) return 0
            if (s == 16 && !legacy16) return 32768
            val v = bits(s)
            return if (v < (1 shl (s - 1))) v - (1 shl s) + 1 else v
        }
        private fun resync() {
            bitBuf = 0; bitCnt = 0
            while (p < d.size - 1) { if ((d[p].toInt() and 0xFF) == 0xFF && (d[p + 1].toInt() and 0xFF) in 0xD0..0xD7) { p += 2; return }; p++ }
        }

        private fun scan(w: Int, h: Int, nc: Int, tsel: IntArray, pred: Int, pt: Int, prec: Int, restart: Int): IntArray {
            val out = IntArray(w * h * nc)
            val rowW = w * nc
            val init = 1 shl (prec - pt - 1)
            val mask = (1 shl prec) - 1
            val ts = Array(nc) { tables[tsel[it]] ?: tables[0] ?: throw UnsupportedRaw("Bad compressed RAW data") }
            var mcu = 0
            var startRow = 0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (restart > 0 && mcu > 0 && mcu % restart == 0) { resync(); startRow = y }
                    for (c in 0 until nc) {
                        val i = y * rowW + x * nc + c
                        val pr = when {
                            y == startRow && x == 0 -> init
                            y == startRow -> out[i - nc]
                            x == 0 -> out[i - rowW]
                            else -> {
                                val ra = out[i - nc]; val rb = out[i - rowW]; val rc = out[i - rowW - nc]
                                when (pred) { 1 -> ra; 2 -> rb; 3 -> rc; 4 -> ra + rb - rc; 5 -> ra + ((rb - rc) shr 1); 6 -> rb + ((ra - rc) shr 1); 7 -> (ra + rb) shr 1; else -> ra }
                            }
                        }
                        out[i] = (pr + diff(ts[c])) and mask
                    }
                    mcu++
                }
            }
            if (pt > 0) for (i in out.indices) out[i] = out[i] shl pt
            return out
        }
    }

    // ------------------------------------------------------------------ colour

    private val XYZ_RGB = doubleArrayOf( // linear sRGB (D65) → XYZ
        0.4124564, 0.3575761, 0.1804375,
        0.2126729, 0.7151522, 0.0721750,
        0.0193339, 0.1191920, 0.9503041,
    )
    private val RGB_XYZ = inv3(XYZ_RGB)!!
    /** Bradford chromatic adaptation D50 → D65 (and back). */
    private val BRAD_50_65 = doubleArrayOf(0.9555766, -0.0230393, 0.0631636, -0.0282895, 1.0099416, 0.0210077, 0.0122982, -0.0204830, 1.3299098)
    private val BRAD_65_50 = inv3(BRAD_50_65)!!
    /** XYZ (D50) → linear ProPhoto RGB. */
    private val XYZ50_PP = doubleArrayOf(1.3459433, -0.2556075, -0.0511118, -0.5445989, 1.5081673, 0.0205351, 0.0, 0.0, 1.2118128)
    private val SRGB_TO_PP = mul3(XYZ50_PP, mul3(BRAD_65_50, XYZ_RGB))
    private val PP_TO_SRGB = inv3(SRGB_TO_PP)!!

    private fun mul3(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { k -> val r = k / 3; val c = k % 3; a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c] }
    private fun mulV(a: DoubleArray, v: DoubleArray) = DoubleArray(3) { r -> a[r * 3] * v[0] + a[r * 3 + 1] * v[1] + a[r * 3 + 2] * v[2] }
    private fun diag(v: DoubleArray) = doubleArrayOf(v[0], 0.0, 0.0, 0.0, v[1], 0.0, 0.0, 0.0, v[2])
    private val I3 = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
    private fun inv3(m: DoubleArray): DoubleArray? {
        val a = m[0]; val b = m[1]; val c = m[2]; val d = m[3]; val e = m[4]; val f = m[5]; val g = m[6]; val h = m[7]; val i = m[8]
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        if (kotlin.math.abs(det) < 1e-12) return null
        return doubleArrayOf((e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det, (f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det, (d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det)
    }

    /** CIE xy of a colour temperature (Planckian below 4000 K, daylight above). */
    private fun cctXy(t: Double): Pair<Double, Double> {
        val x = if (t < 4000) {
            -0.2661239e9 / (t * t * t) - 0.2343589e6 / (t * t) + 0.8776956e3 / t + 0.179910
        } else if (t <= 7000) {
            -4.6070e9 / (t * t * t) + 2.9678e6 / (t * t) + 0.09911e3 / t + 0.244063
        } else {
            -2.0064e9 / (t * t * t) + 1.9018e6 / (t * t) + 0.24748e3 / t + 0.237040
        }
        val y = if (t < 4000) -1.1063814 * x * x * x - 1.34811020 * x * x + 2.18555832 * x - 0.20219683
        else -3.0 * x * x + 2.87 * x - 0.275
        return x to y
    }
    /** Colour temperature of an xy white (McCamy). */
    private fun xyCct(x: Double, y: Double): Double { val n = (x - 0.3320) / (0.1858 - y); return (449 * n * n * n + 3525 * n * n + 6823.3 * n + 5520.33).coerceIn(1500.0, 50000.0) }
    private fun xyz(x: Double, y: Double) = doubleArrayOf(x / y, 1.0, (1 - x - y) / y)

    /** Weight of calibration illuminant 1 for a white of [cct] Kelvin (DNG spec: by inverse temperature). */
    private fun weight1(m: Meta, cct: Double): Double {
        if (m.cm2 == null || m.illum2 <= 0 || m.illum1 == m.illum2) return 1.0
        val t1 = m.illum1.toDouble(); val t2 = m.illum2.toDouble()
        return ((1 / cct - 1 / t2) / (1 / t1 - 1 / t2)).coerceIn(0.0, 1.0)
    }
    private fun lerp(a: DoubleArray?, b: DoubleArray?, g: Double): DoubleArray? = when {
        a != null && b != null -> DoubleArray(9) { a[it] * g + b[it] * (1 - g) }
        else -> a ?: b
    }
    /** XYZ → camera at a white of [cct]: AnalogBalance × CameraCalibration × ColorMatrix (interpolated). */
    private fun xyzToCam(m: Meta, cct: Double): DoubleArray? {
        val g = weight1(m, cct)
        val cm = lerp(m.cm1, m.cm2, g) ?: m.colorMatrix ?: return null
        val cc = lerp(m.cc1, m.cc2, g) ?: I3
        val ab = m.analog?.let { diag(it) } ?: I3
        return mul3(ab, mul3(cc, cm))
    }
    /** The xy white a camera-space neutral corresponds to (iterated, as in the DNG spec). */
    private fun neutralXy(m: Meta, n: DoubleArray): Pair<Double, Double> {
        var x = 0.3457; var y = 0.3585
        repeat(12) {
            val c = xyzToCam(m, xyCct(x, y)) ?: return x to y
            val ic = inv3(c) ?: return x to y
            val v = mulV(ic, n); val s = v[0] + v[1] + v[2]
            if (s <= 1e-9) return x to y
            val nx = v[0] / s; val ny = v[1] / s
            if (kotlin.math.abs(nx - x) < 1e-7 && kotlin.math.abs(ny - y) < 1e-7) return nx to ny
            x = nx; y = ny
        }
        return x to y
    }

    /** Everything the develop needs about colour, for one white balance choice. */
    private class Colour(val mul: FloatArray, val mtx: FloatArray, val weight: Double)

    private fun colour(m: Meta, dev: Dev, temp: Float, tint: Float): Colour {
        // 1. The white, as a camera neutral.
        val neutral: DoubleArray = when {
            dev.wb == 1 -> m.autoMul?.let { a -> doubleArrayOf(1.0 / a[0], 1.0, 1.0 / a[2]) } ?: m.asShot
            dev.wb >= 2 -> {
                val (x, y) = cctXy(WB_PRESETS[dev.wb].second.toDouble())
                xyzToCam(m, xyCct(x, y))?.let { c -> mulV(c, xyz(x, y)) }?.takeIf { n -> n.all { it > 1e-6 } } ?: m.asShot
            }
            m.asShotXY != null -> { val x = m.asShotXY[0]; val y = m.asShotXY[1]; xyzToCam(m, xyCct(x, y))?.let { c -> mulV(c, xyz(x, y)) } ?: m.asShot }
            else -> m.asShot
        }
        val (wx, wy) = if (m.colorMatrix != null || m.cm1 != null) neutralXy(m, neutral) else (0.3127 to 0.3290)
        val cct = xyCct(wx, wy)
        // 2. White balance multipliers (green = 1), with the Temperature / Tint sliders.
        val base = doubleArrayOf(neutral[1] / neutral[0], 1.0, neutral[1] / neutral[2])
        var r = base[0].toFloat(); var g = 1f; var b = base[2].toFloat()
        r *= 2f.pow(temp * 0.6f); b *= 2f.pow(-temp * 0.6f); g *= 2f.pow(-tint * 0.4f)
        // 3. White-balanced camera RGB → linear sRGB.
        val fm = lerp(m.fm1, m.fm2, weight1(m, cct))
        val c = xyzToCam(m, cct)
        val mtx: DoubleArray = if (fm != null && c != null) {
            // Forward matrix route (DNG 1.2+): camera → XYZ D50 → sRGB.
            val abcc = mul3(m.analog?.let { diag(it) } ?: I3, lerp(m.cc1, m.cc2, weight1(m, cct)) ?: I3)
            val iabcc = inv3(abcc) ?: I3
            val ref = mulV(iabcc, neutral)
            val d = diag(DoubleArray(3) { 1.0 / ref[it].coerceAtLeast(1e-9) })
            val camToSrgb = mul3(RGB_XYZ, mul3(BRAD_50_65, mul3(fm, mul3(d, iabcc))))
            val onWb = mul3(camToSrgb, diag(DoubleArray(3) { 1.0 / base[it] }))
            val wv = mulV(onWb, doubleArrayOf(1.0, 1.0, 1.0)); val k = (wv[0] + wv[1] + wv[2]) / 3
            DoubleArray(9) { onWb[it] / k.coerceAtLeast(1e-9) }
        } else if (c != null) {
            val camRgb = mul3(c, XYZ_RGB)
            for (row in 0 until 3) { val s = camRgb[row * 3] + camRgb[row * 3 + 1] + camRgb[row * 3 + 2]; if (s > 1e-9) for (k in 0 until 3) camRgb[row * 3 + k] /= s }
            inv3(camRgb) ?: I3
        } else I3
        return Colour(floatArrayOf(r, g, b), FloatArray(9) { mtx[it].toFloat() }, weight1(m, cct))
    }

    // ---- camera profile look tables (ProfileHueSatMap / ProfileLookTable), in linear ProPhoto RGB

    private fun hsvApply(t: HsvTable, t2: HsvTable?, w1: Float, rgb: FloatArray) {
        var r = rgb[0]; var g = rgb[1]; var b = rgb[2]
        if (t.srgbGamma) { r = enc(r); g = enc(g); b = enc(b) }
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        if (mx <= 0f) return
        val d = mx - mn
        var hh = if (d <= 0f) 0f else when (mx) { r -> ((g - b) / d).let { if (it < 0) it + 6f else it }; g -> (b - r) / d + 2f; else -> (r - g) / d + 4f }
        var ss = if (mx > 0f) d / mx else 0f
        var vv = mx
        // Trilinear lookup: hue wraps, saturation / value clamp.
        val hf = hh / 6f * t.hues; val h0 = hf.toInt() % t.hues; val h1 = (h0 + 1) % t.hues; val fh = hf - hf.toInt()
        val sf = (ss * (t.sats - 1)).coerceIn(0f, (t.sats - 1).toFloat()); val s0 = sf.toInt().coerceAtMost(t.sats - 2); val fs = sf - s0
        val vf = if (t.vals > 1) (vv.coerceIn(0f, 1f) * (t.vals - 1)) else 0f; val v0 = vf.toInt().coerceAtMost(max(0, t.vals - 2)); val fv = vf - v0
        val v1 = if (t.vals > 1) v0 + 1 else v0
        val out = FloatArray(3)
        for (k in 0 until 3) {
            fun at(tb: HsvTable, vi: Int, hi: Int, si: Int) = tb.data[((vi * tb.hues + hi) * tb.sats + si) * 3 + k]
            fun tri(tb: HsvTable): Float {
                val a = (at(tb, v0, h0, s0) * (1 - fs) + at(tb, v0, h0, s0 + 1) * fs) * (1 - fh) + (at(tb, v0, h1, s0) * (1 - fs) + at(tb, v0, h1, s0 + 1) * fs) * fh
                if (v1 == v0) return a
                val c = (at(tb, v1, h0, s0) * (1 - fs) + at(tb, v1, h0, s0 + 1) * fs) * (1 - fh) + (at(tb, v1, h1, s0) * (1 - fs) + at(tb, v1, h1, s0 + 1) * fs) * fh
                return a * (1 - fv) + c * fv
            }
            out[k] = if (t2 != null && t2.data.size == t.data.size) tri(t) * w1 + tri(t2) * (1 - w1) else tri(t)
        }
        hh += out[0] / 60f; ss = (ss * out[1]).coerceIn(0f, 1f); vv *= out[2]
        hh = ((hh % 6f) + 6f) % 6f
        val i = hh.toInt(); val f = hh - i
        val p = vv * (1 - ss); val q = vv * (1 - ss * f); val u = vv * (1 - ss * (1 - f))
        when (i) { 0 -> { r = vv; g = u; b = p }; 1 -> { r = q; g = vv; b = p }; 2 -> { r = p; g = vv; b = u }; 3 -> { r = p; g = q; b = vv }; 4 -> { r = u; g = p; b = vv }; else -> { r = vv; g = p; b = q } }
        if (t.srgbGamma) { r = dec(r); g = dec(g); b = dec(b) }
        rgb[0] = r; rgb[1] = g; rgb[2] = b
    }
    private fun enc(v: Float) = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.coerceAtLeast(0f).pow(1f / 2.4f) - 0.055f
    private fun dec(v: Float) = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)

    // ------------------------------------------------------------------ develop

    /**
     * Linear camera RGB (no white balance), upright, at most [maxSide] on the long side — for the
     * live preview. [vig] is the file's vignetting correction per pixel (or null).
     */
    class Linear(val w: Int, val h: Int, val rgb: FloatArray, internal val vig: FloatArray? = null, internal val g3: FloatArray? = null)

    /** Vignetting gain from FixVignetteRadial at (x, y) of a w×h stored image. */
    private val PHONE_MAKERS = listOf("samsung", "google", "xiaomi", "redmi", "poco", "oneplus", "oppo", "vivo", "realme", "motorola", "huawei", "honor", "nothing", "asus", "sony", "nokia", "hmd", "fairphone", "zte", "nubia", "tecno", "infinix", "lenovo", "meizu", "sharp", "apple")
    private fun isPhone(make: String): Boolean = make.lowercase().let { mk -> PHONE_MAKERS.any { mk.startsWith(it) } }

    /**
     * Dark corners of a phone RAW without lens-shading data: brightness by distance from the centre
     * (median of each ring, so the scene itself matters little), turned into a gain like a DNG
     * FixVignetteRadial (k0, k1 for r², r⁴; centre in the middle). Kept within what phone lenses do.
     */
    private fun estimateShading(d: Linear): DoubleArray {
        val rings = 12
        val buckets = Array(rings) { ArrayList<Float>() }
        val cx = d.w / 2f; val cy = d.h / 2f; val m = kotlin.math.sqrt(cx * cx + cy * cy)
        val step = max(1, (d.w * d.h) / 120_000)
        var i = 0
        while (i < d.w * d.h) {
            val x = i % d.w; val y = i / d.w
            val r = kotlin.math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / m
            val l = 0.25f * d.rgb[i * 3] + 0.5f * d.rgb[i * 3 + 1] + 0.25f * d.rgb[i * 3 + 2]
            if (l in 0.004f..0.9f) buckets[(r * rings).toInt().coerceIn(0, rings - 1)] += l
            i += step
        }
        val med = FloatArray(rings) { k -> buckets[k].let { b -> if (b.size < 20) Float.NaN else b.sorted()[b.size / 2] } }
        val c0 = (med[0].takeIf { !it.isNaN() } ?: med.firstOrNull { !it.isNaN() }) ?: return doubleArrayOf(0.48, 0.32, 0.0, 0.0, 0.0, 0.5, 0.5)
        val outer = listOf(med[rings - 1], med[rings - 2], med[rings - 3]).filter { !it.isNaN() }
        // Corner gain needed: measured when it looks like lens fall-off, else a typical phone value.
        // Blended with a typical phone value, so photos of the same set get about the same correction.
        val measured = if (outer.isEmpty()) 2.0f else (c0 / outer.average().toFloat()).coerceIn(1.15f, 2.8f)
        var g = (0.4f * measured + 0.6f * 2.0f).coerceIn(1.3f, 2.5f)
        if (g.isNaN()) g = 2.0f
        val k = (g - 1).toDouble()
        return doubleArrayOf(k * 0.6, k * 0.4, 0.0, 0.0, 0.0, 0.5, 0.5)
    }

    private fun vigGain(v: DoubleArray, x: Float, y: Float, w: Int, h: Int): Float {
        val cx = v[5] * w; val cy = v[6] * h
        val mx = max(max(cx, w - cx), 1.0); val my = max(max(cy, h - cy), 1.0)
        val m = kotlin.math.sqrt(mx * mx + my * my)
        val dx = (x - cx) / m; val dy = (y - cy) / m
        val r2 = dx * dx + dy * dy
        return (1 + r2 * (v[0] + r2 * (v[1] + r2 * (v[2] + r2 * (v[3] + r2 * v[4]))))).toFloat().coerceIn(0.5f, 8f)
    }

    /**
     * OpcodeList3 GainMaps at (x, y) of the cropped picture: multiplies [rgb] in place, per colour.
     * Map areas are in active-area coordinates; [GainMap.plane] 0..2 = R, G, B after demosaic.
     */
    private fun gain3(m: Meta, x: Float, y: Float, rgb: FloatArray) {
        val ax = x + m.cropLeft - m.activeLeft; val ay = y + m.cropTop - m.activeTop
        for (g in m.gains3) {
            if (ay < g.top || ay >= g.bottom || ax < g.left || ax >= g.right) continue
            val vr = (ay - g.top) / (g.bottom - g.top).coerceAtLeast(1)
            val hr = (ax - g.left) / (g.right - g.left).coerceAtLeast(1)
            val fy = (((vr - g.orV) / (if (g.spV > 0) g.spV else 1.0))).coerceIn(0.0, (g.pv - 1).toDouble())
            val fx = (((hr - g.orH) / (if (g.spH > 0) g.spH else 1.0))).coerceIn(0.0, (g.ph - 1).toDouble())
            val y0 = fy.toInt().coerceAtMost(max(g.pv - 2, 0)); val x0 = fx.toInt().coerceAtMost(max(g.ph - 2, 0))
            val y1 = min(y0 + 1, g.pv - 1); val x1 = min(x0 + 1, g.ph - 1)
            val wy = (fy - y0).toFloat(); val wx = (fx - x0).toFloat()
            for (c in g.plane until min(g.plane + g.planes, 3)) {
                val mp = min(c - g.plane, g.mapPlanes - 1)
                val gg = g.gains; val st = g.mapPlanes
                val v = (gg[(y0 * g.ph + x0) * st + mp] * (1 - wx) + gg[(y0 * g.ph + x1) * st + mp] * wx) * (1 - wy) +
                    (gg[(y1 * g.ph + x0) * st + mp] * (1 - wx) + gg[(y1 * g.ph + x1) * st + mp] * wx) * wy
                rgb[c] *= v
            }
        }
    }

    /** Decodes and makes the preview-size linear picture (half-size superpixels, then averaged down). */
    fun preview(context: Context, m: Meta, maxSide: Int): Linear {
        val s = decode(context, m)
        // Big sensors: bin straight from the sensor data to the preview size (no full-size copy in between, so
        // even very large RAWs fit in the phone's memory).
        var down = binned(s, maxSide) ?: boxDown(superpixel(s), maxSide)
        if (!m.hasShading && s.cfa != null && m.estVignette == null && isPhone(m.make)) m.estVignette = estimateShading(down)
        (m.vignette ?: m.estVignette)?.let { v -> down = Linear(down.w, down.h, down.rgb, FloatArray(down.w * down.h) { i -> vigGain(v, (i % down.w + 0.5f) / down.w * s.w, (i / down.w + 0.5f) / down.h * s.h, s.w, s.h) }) }
        if (m.gains3.isNotEmpty()) {
            val g3 = FloatArray(down.w * down.h * 3); val t = FloatArray(3)
            for (i in 0 until down.w * down.h) {
                t[0] = 1f; t[1] = 1f; t[2] = 1f
                gain3(m, (i % down.w + 0.5f) / down.w * s.w, (i / down.w + 0.5f) / down.h * s.h, t)
                g3[i * 3] = t[0]; g3[i * 3 + 1] = t[1]; g3[i * 3 + 2] = t[2]
            }
            down = Linear(down.w, down.h, down.rgb, down.vig, g3)
        }
        // Grey-world auto white balance (ignoring near-black and clipped pixels).
        var sr = 0.0; var sg = 0.0; var sb = 0.0
        val px = down.rgb
        var i = 0
        while (i < px.size) {
            val r = px[i]; val g = px[i + 1]; val b = px[i + 2]
            val mx = max(r, max(g, b))
            if (mx in 0.02f..0.95f) { sr += r; sg += g; sb += b }
            i += 3
        }
        if (sr > 0 && sb > 0) m.autoMul = floatArrayOf((sg / sr).toFloat(), 1f, (sg / sb).toFloat())
        val out = orient(down, m.orientation)
        if (m.matchLin == null) m.matchLin = try { shrink(out, 400) } catch (_: Throwable) { null }
        if (m.libraw && m.autoBaseline.isNaN()) m.autoBaseline = try { matchCameraBrightness(context, m, out) } catch (_: Throwable) { 0f }
        return out
    }

    /** The small develop source the RAW LOOK previews are made from (filled in by [preview]). */
    fun lookSource(m: Meta): Linear? = m.matchLin

    /** Set by [open], so "Match camera picture" can read the camera's preview while developing. */
    @Volatile private var appContext: Context? = null

    /**
     * Fits a tone curve (luminance histogram matching) and a colour strength that bring the default
     * develop to the look of the camera's own preview stored in the file. Identity when there's none.
     */
    private fun matchCamera(m: Meta): IntArray {
        val ident = IntArray(256) { it }
        val ctx = appContext ?: return ident.also { m.camLut = it }
        val lin = m.matchLin ?: try { preview(ctx, m, 512); m.matchLin } catch (_: Throwable) { null } ?: return ident
        val cam = embedded(ctx, m.uri, 200)?.bitmap ?: return ident.also { m.camLut = it }
        val ours = develop(lin, m, Dev(), 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val (lut, sat) = fitTone(ours, cam)
        m.camSat = sat
        m.camLut = lut
        return lut
    }

    /**
     * The tone curve (8-bit in → out, from luminance histogram matching) and colour strength that
     * turn [ours] into the look of [cam] — the camera's own picture of the same scene.
     */
    internal fun fitTone(ours: Bitmap, cam: Bitmap): Pair<IntArray, Float> {
        fun stats(b: Bitmap): Pair<DoubleArray, Double> {
            val px = IntArray(b.width * b.height); b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
            val hist = DoubleArray(256); var chroma = 0.0; var n = 0
            for (c in px) {
                val r = (c shr 16) and 255; val g = (c shr 8) and 255; val bl = c and 255
                val y = ((r * 0.2126 + g * 0.7152 + bl * 0.0722) + 0.5).toInt().coerceIn(0, 255)
                hist[y]++
                if (y in 30..225) { chroma += (max(r, max(g, bl)) - min(r, min(g, bl))).toDouble() / y; n++ }
            }
            for (i in 1 until 256) hist[i] += hist[i - 1]
            val t = hist[255].coerceAtLeast(1.0); for (i in 0 until 256) hist[i] /= t
            return hist to (if (n > 0) chroma / n else 0.0)
        }
        val (ho, co) = stats(ours); val (hc, cc) = stats(cam)
        val raw = DoubleArray(256) { v -> var u = 0; while (u < 255 && hc[u] < ho[v]) u++; u.toDouble() }
        // Smooth, keep it rising and not too steep, so it never posterises.
        val lut = IntArray(256)
        var prev = 0.0
        for (v in 0 until 256) {
            var s = 0.0; var w = 0.0
            for (d in -6..6) { val i = (v + d).coerceIn(0, 255); s += raw[i]; w++ }
            var x = s / w
            x = x.coerceIn(prev, prev + 3.0)
            if (v == 255) x = max(x, 250.0)
            lut[v] = x.roundToInt().coerceIn(0, 255); prev = x
        }
        val sat = if (co > 1e-3 && cc > 1e-3) (cc / co).toFloat().coerceIn(0.7f, 1.6f) else 1f
        return lut to sat
    }

    /** Median brightness (linear light) of a picture. */
    private fun medianLum(b: Bitmap): Float {
        val px = IntArray(b.width * b.height); b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        val l = FloatArray(px.size) { i -> val c = px[i]; 0.2126f * dec(((c shr 16) and 255) / 255f) + 0.7152f * dec(((c shr 8) and 255) / 255f) + 0.0722f * dec((c and 255) / 255f) }
        l.sort(); return l[l.size / 2]
    }

    /** Exposure lift (stops, 0…1.5) that brings the neutral develop to the brightness of the camera's own preview. */
    private fun matchCameraBrightness(context: Context, m: Meta, lin: Linear): Float {
        val cam = embedded(context, m.uri, 200)?.bitmap ?: return 0f
        m.autoBaseline = 0f
        val ours = develop(shrink(lin, 256), m, Dev(), 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val a = medianLum(ours); val b = medianLum(cam)
        if (a < 1e-4f || b < 1e-4f) return 0f
        return (ln(b / a) / ln(2f)).coerceIn(0f, 1.5f)
    }

    /** Bayer or RGB sensor data averaged straight down to about [maxSide] px, or null for other patterns / small sensors. */
    private fun binned(s: Sensor, maxSide: Int): Linear? {
        val bayer = s.bayer
        if (!bayer && s.cfa != null) return null
        val sw = if (bayer) s.w / 2 else s.w; val sh = if (bayer) s.h / 2 else s.h
        val k = max(sw, sh).toFloat() / maxSide
        if (k <= 1.5f) return null
        val w = (sw / k).toInt().coerceAtLeast(1); val h = (sh / k).toInt().coerceAtLeast(1)
        val out = FloatArray(w * h * 3)
        for (y in 0 until h) {
            val y0 = (y * k).toInt(); val y1 = min(sh, ((y + 1) * k).toInt().coerceAtLeast(y0 + 1))
            for (x in 0 until w) {
                val x0 = (x * k).toInt(); val x1 = min(sw, ((x + 1) * k).toInt().coerceAtLeast(x0 + 1))
                var r = 0f; var g = 0f; var b = 0f; var n = 0
                if (bayer) {
                    for (cy in y0 until y1) for (cx in x0 until x1) {
                        var gg = 0f
                        for (dy in 0..1) for (dx in 0..1) {
                            val xx = cx * 2 + dx; val yy = cy * 2 + dy
                            val v = s.v(yy * s.w + xx)
                            when (s.color(xx, yy)) { 0 -> r += v; 1 -> gg += v * 0.5f; else -> b += v }
                        }
                        g += gg; n++
                    }
                } else {
                    val spp = s.spp
                    for (yy in y0 until y1) for (xx in x0 until x1) {
                        val i = (yy * s.w + xx) * spp
                        r += s.v(i); g += s.v(i + min(1, spp - 1)); b += s.v(i + min(2, spp - 1)); n++
                    }
                }
                val o = (y * w + x) * 3
                out[o] = r / n; out[o + 1] = g / n; out[o + 2] = b / n
            }
        }
        return Linear(w, h, out)
    }

    private fun superpixel(s: Sensor): Linear {
        if (s.cfa == null) { // already RGB
            val f = FloatArray(s.w * s.h * 3) { s.v(it) }
            return Linear(s.w, s.h, f)
        }
        if (s.bayer) {
            val w = s.w / 2; val h = s.h / 2
            val out = FloatArray(w * h * 3)
            for (y in 0 until h) for (x in 0 until w) {
                val o = (y * w + x) * 3
                var g = 0f
                for (dy in 0..1) for (dx in 0..1) {
                    val xx = x * 2 + dx; val yy = y * 2 + dy
                    val v = s.v(yy * s.w + xx)
                    when (s.color(xx, yy)) { 0 -> out[o] = v; 1 -> g += v * 0.5f; else -> out[o + 2] = v }
                }
                out[o + 1] = g
            }
            return Linear(w, h, out)
        }
        // Any other pattern (X-Trans 6×6, Quad Bayer 4×4…): average each colour over one pattern
        // repeat, stepping half a repeat so the preview keeps detail.
        val st = max(1, min(s.cw, s.ch) / 2)
        val w = ((s.w - s.cw) / st + 1).coerceAtLeast(1); val h = ((s.h - s.ch) / st + 1).coerceAtLeast(1)
        val out = FloatArray(w * h * 3)
        val sum = FloatArray(3); val n = IntArray(3)
        for (y in 0 until h) for (x in 0 until w) {
            sum.fill(0f); n.fill(0)
            for (dy in 0 until s.ch) for (dx in 0 until s.cw) {
                val xx = x * st + dx; val yy = y * st + dy
                if (xx >= s.w || yy >= s.h) continue
                val c = s.color(xx, yy); sum[c] += s.v(yy * s.w + xx); n[c]++
            }
            val o = (y * w + x) * 3
            for (c in 0 until 3) out[o + c] = if (n[c] > 0) sum[c] / n[c] else 0f
        }
        return Linear(w, h, out)
    }

    private fun boxDown(src: Linear, maxSide: Int): Linear {
        val k = max(src.w, src.h).toFloat() / maxSide
        if (k <= 1f) return src
        val w = (src.w / k).toInt().coerceAtLeast(1); val h = (src.h / k).toInt().coerceAtLeast(1)
        val out = FloatArray(w * h * 3)
        val vg = src.vig?.let { FloatArray(w * h) }
        // The per-colour lens-shading gains (DJI and others) must come along too, or the small live
        // preview shows the lens's dark corners while a slider moves.
        val g3 = src.g3?.let { FloatArray(w * h * 3) }
        for (y in 0 until h) {
            val y0 = (y * k).toInt(); val y1 = min(src.h, ((y + 1) * k).toInt().coerceAtLeast(y0 + 1))
            for (x in 0 until w) {
                val x0 = (x * k).toInt(); val x1 = min(src.w, ((x + 1) * k).toInt().coerceAtLeast(x0 + 1))
                var r = 0f; var g = 0f; var b = 0f; var n = 0
                for (yy in y0 until y1) for (xx in x0 until x1) { val i = (yy * src.w + xx) * 3; r += src.rgb[i]; g += src.rgb[i + 1]; b += src.rgb[i + 2]; n++ }
                val o = (y * w + x) * 3
                out[o] = r / n; out[o + 1] = g / n; out[o + 2] = b / n
                val mid = ((y0 + y1) / 2).coerceAtMost(src.h - 1) * src.w + ((x0 + x1) / 2).coerceAtMost(src.w - 1)
                if (vg != null) vg[y * w + x] = src.vig!![mid]
                if (g3 != null) { g3[o] = src.g3!![mid * 3]; g3[o + 1] = src.g3[mid * 3 + 1]; g3[o + 2] = src.g3[mid * 3 + 2] }
            }
        }
        return Linear(w, h, out, vg, g3)
    }

    /** Smaller copy of a preview (for the fast live preview while dragging). */
    fun shrink(src: Linear, maxSide: Int): Linear = boxDown(src, maxSide)

    private fun orientXy(o: Int, x: Int, y: Int, sw: Int, sh: Int): Pair<Int, Int> = when (o) {
        2 -> (sw - 1 - x) to y
        3 -> (sw - 1 - x) to (sh - 1 - y)
        4 -> x to (sh - 1 - y)
        5 -> y to x
        6 -> y to (sh - 1 - x)
        7 -> (sw - 1 - y) to (sh - 1 - x)
        8 -> (sw - 1 - y) to x
        else -> x to y
    }

    private fun orient(src: Linear, o: Int): Linear {
        if (o <= 1 || o > 8) return src
        val sw = src.w; val sh = src.h
        val swap = o >= 5
        val w = if (swap) sh else sw; val h = if (swap) sw else sh
        val out = FloatArray(w * h * 3)
        val vg = src.vig?.let { FloatArray(w * h) }
        val g3 = src.g3?.let { FloatArray(w * h * 3) }
        for (y in 0 until h) for (x in 0 until w) {
            // Map output (x, y) back to the stored picture.
            val (sx, sy) = orientXy(o, x, y, sw, sh)
            val si = (sy * sw + sx) * 3; val di = (y * w + x) * 3
            out[di] = src.rgb[si]; out[di + 1] = src.rgb[si + 1]; out[di + 2] = src.rgb[si + 2]
            if (vg != null) vg[y * w + x] = src.vig!![sy * sw + sx]
            if (g3 != null) { g3[di] = src.g3!![si]; g3[di + 1] = src.g3[si + 1]; g3[di + 2] = src.g3[si + 2] }
        }
        return Linear(w, h, out, vg, g3)
    }

    /** Where the stored image's relative point (cx, cy) lands after orientation [o]. */
    private fun orientCentre(o: Int, cx: Double, cy: Double): Pair<Double, Double> = when (o) {
        2 -> (1 - cx) to cy; 3 -> (1 - cx) to (1 - cy); 4 -> cx to (1 - cy)
        5 -> cy to cx; 6 -> (1 - cy) to cx; 7 -> (1 - cy) to (1 - cx); 8 -> cy to (1 - cx)
        else -> cx to cy
    }

    /**
     * WarpRectilinear from the file: lens distortion and, with one set per colour, lateral
     * chromatic aberration (colour fringing). Returns a corrected copy of [px] (w×h ARGB).
     */
    private fun warp(px: IntArray, w: Int, h: Int, wp: Warp, cx0: Double, cy0: Double): IntArray {
        val out = IntArray(w * h)
        val cx = cx0 * w; val cy = cy0 * h
        val mx = max(max(cx, w - cx), 1.0); val my = max(max(cy, h - cy), 1.0)
        val m = kotlin.math.sqrt(mx * mx + my * my)
        val planes = wp.coef.size
        fun sample(xs: Double, ys: Double, shift: Int): Int {
            val x0 = kotlin.math.floor(xs).toInt(); val y0 = kotlin.math.floor(ys).toInt()
            val fx = (xs - x0).toFloat(); val fy = (ys - y0).toFloat()
            fun at(x: Int, y: Int) = (px[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)] shr shift) and 0xFF
            val a = at(x0, y0) * (1 - fx) + at(x0 + 1, y0) * fx
            val b = at(x0, y0 + 1) * (1 - fx) + at(x0 + 1, y0 + 1) * fx
            return (a * (1 - fy) + b * fy).roundToInt().coerceIn(0, 255)
        }
        for (y in 0 until h) {
            val dy = (y - cy) / m
            for (x in 0 until w) {
                val dx = (x - cx) / m
                val r2 = dx * dx + dy * dy
                var res = 0xFF shl 24
                for (ch in 0 until 3) {
                    val k = wp.coef[if (planes >= 3) ch else 0]
                    val f = k[0] + r2 * (k[1] + r2 * (k[2] + r2 * k[3]))
                    val xs = cx + m * (dx * f + 2 * k[4] * dx * dy + k[5] * (r2 + 2 * dx * dx))
                    val ys = cy + m * (dy * f + 2 * k[5] * dx * dy + k[4] * (r2 + 2 * dy * dy))
                    val shift = 16 - ch * 8
                    res = res or (sample(xs, ys, shift) shl shift)
                }
                out[y * w + x] = res
            }
        }
        return out
    }

    /**
     * DJI lens calibration (Brown–Conrady: fx, fy, cx, cy from the centre, k1, k2, p1, p2, k3,
     * measured at [refW]×[refH]): every output pixel is taken from where the lens put it.
     */
    private fun dewarp(px: IntArray, w: Int, h: Int, d: DoubleArray, refW: Int, refH: Int): IntArray {
        val sx = w.toDouble() / refW; val sy = h.toDouble() / refH
        val fx = d[0] * sx; val fy = d[1] * sy
        val cx = w / 2.0 + d[2] * sx; val cy = h / 2.0 + d[3] * sy
        val k1 = d[4]; val k2 = d[5]; val p1 = d[6]; val p2 = d[7]; val k3 = d[8]
        val out = IntArray(w * h)
        for (v in 0 until h) {
            val y = (v - cy) / fy
            for (u in 0 until w) {
                val x = (u - cx) / fx
                val r2 = x * x + y * y
                val rad = 1 + r2 * (k1 + r2 * (k2 + r2 * k3))
                val xd = x * rad + 2 * p1 * x * y + p2 * (r2 + 2 * x * x)
                val yd = y * rad + p1 * (r2 + 2 * y * y) + 2 * p2 * x * y
                val xs = xd * fx + cx; val ys = yd * fy + cy
                val x0 = kotlin.math.floor(xs).toInt(); val y0 = kotlin.math.floor(ys).toInt()
                val ax = (xs - x0).toFloat(); val ay = (ys - y0).toFloat()
                fun at(xx: Int, yy: Int) = px[yy.coerceIn(0, h - 1) * w + xx.coerceIn(0, w - 1)]
                val c00 = at(x0, y0); val c10 = at(x0 + 1, y0); val c01 = at(x0, y0 + 1); val c11 = at(x0 + 1, y0 + 1)
                var res = 0xFF shl 24
                for (sh in intArrayOf(16, 8, 0)) {
                    val a = ((c00 shr sh) and 0xFF) * (1 - ax) + ((c10 shr sh) and 0xFF) * ax
                    val b = ((c01 shr sh) and 0xFF) * (1 - ax) + ((c11 shr sh) and 0xFF) * ax
                    res = res or ((a * (1 - ay) + b * ay).roundToInt().coerceIn(0, 255) shl sh)
                }
                out[v * w + u] = res
            }
        }
        return out
    }

    /** Pre-computed develop: white balance, colour matrix, camera profile, exposure and tone. */
    private class Pipe(val m: Meta, dev: Dev, exposure: Float, highlights: Float, shadows: Float, whites: Float, blacks: Float, temp: Float, tint: Float) {
        val lk = LOOKS[dev.look.coerceIn(0, LOOKS.size - 1)]
        val lut = if (lk.match) (m.camLut ?: matchCamera(m)) else lk.curve?.let { expandCurve(it) }
        val col = colour(m, dev, temp + lk.warm, tint)
        val mul = col.mul
        val clip = min(mul[0], min(mul[1], mul[2]))
        val mtx = col.mtx
        val prof = PROFILES[dev.profile.coerceIn(0, PROFILES.size - 1)]
        val camera = prof.camera && m.hasCameraProfile
        val k = 2f.pow(exposure + lk.ev + m.baselineExposure + (m.autoBaseline.takeIf { !it.isNaN() } ?: 0f) + if (camera) m.baselineOffset else 0f)
        val hs = if (camera) m.hueSat else null
        val hs2 = if (camera) m.hueSat2 else null
        val look = if (camera) m.look else null
        val w1 = col.weight.toFloat()
        val toPP = SRGB_TO_PP.map { it.toFloat() }.toFloatArray()
        val fromPP = PP_TO_SRGB.map { it.toFloat() }.toFloatArray()
        val sh = shadows + lk.sh; val hi = highlights + lk.hi; val wh = whites; val bl = blacks
        val sat = prof.sat * lk.sat * (if (lk.match) m.camSat else 1f)
        // Display LUT: linear 0..1 → (camera tone curve, or the profile's contrast) → sRGB → 8-bit.
        val curve = if (camera) m.toneCurve else null
        val disp = IntArray(DISP + 1) { i ->
            var lv = i / DISP.toFloat()
            if (curve != null) lv = curveAt(curve, lv)
            var v = if (lv <= 0.0031308f) lv * 12.92f else 1.055f * lv.pow(1f / 2.4f) - 0.055f
            if (curve == null) { val s = v * v * (3f - 2f * v); v += (s - v) * (prof.contrast + lk.contrast) }
            val d = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            lut?.get(d) ?: d
        }
        private val tmp = FloatArray(3)

        fun tone(y: Float): Float {
            if (y <= 0f) return 0f
            var v = y
            var l = ln(v) / LN2
            if (sh != 0f) { v *= 2f.pow(sh * 1.6f * (1f - smooth(-5f, -1f, l))); l = ln(v) / LN2 }
            if (hi != 0f) { v *= 2f.pow(hi * 1.6f * smooth(-2.5f, 1f, l)); l = ln(v) / LN2 }
            if (wh != 0f) v *= 2f.pow(wh * 0.9f * smooth(-1.5f, 1.5f, l))
            if (bl != 0f) { val d = bl * 0.015f; v = if (d < 0f) ((v + d) / (1f + d)).coerceAtLeast(0f) else d + v * (1f - d) }
            // Soft highlight roll-off: everything above the knee is compressed, never hard-clipped.
            val kn = prof.knee
            return if (v <= kn) v else kn + (1f - kn) * (1f - exp(-(v - kn) / (1f - kn)))
        }

        /** Camera RGB (no WB) → ARGB; [gain] is the lens vignetting correction for this pixel. */
        fun px(r0: Float, g0: Float, b0: Float, gain: Float = 1f): Int {
            val cr = min(r0 * gain * mul[0], clip); val cg = min(g0 * gain * mul[1], clip); val cb = min(b0 * gain * mul[2], clip)
            var r = (mtx[0] * cr + mtx[1] * cg + mtx[2] * cb) * k
            var g = (mtx[3] * cr + mtx[4] * cg + mtx[5] * cb) * k
            var b = (mtx[6] * cr + mtx[7] * cg + mtx[8] * cb) * k
            r = max(r, 0f); g = max(g, 0f); b = max(b, 0f)
            if (hs != null || look != null) {
                // The camera maker's colour tables, in linear ProPhoto RGB.
                tmp[0] = toPP[0] * r + toPP[1] * g + toPP[2] * b
                tmp[1] = toPP[3] * r + toPP[4] * g + toPP[5] * b
                tmp[2] = toPP[6] * r + toPP[7] * g + toPP[8] * b
                hs?.let { hsvApply(it, hs2, w1, tmp) }
                look?.let { hsvApply(it, null, 1f, tmp) }
                r = max(0f, fromPP[0] * tmp[0] + fromPP[1] * tmp[1] + fromPP[2] * tmp[2])
                g = max(0f, fromPP[3] * tmp[0] + fromPP[4] * tmp[1] + fromPP[5] * tmp[2])
                b = max(0f, fromPP[6] * tmp[0] + fromPP[7] * tmp[1] + fromPP[8] * tmp[2])
            }
            val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
            if (y <= 1e-7f) return 0xFF000000.toInt()
            val y2 = tone(y)
            val gn = y2 / y
            if (prof.mono) { val d = disp[(y2.coerceIn(0f, 1f) * DISP).toInt()]; return (0xFF shl 24) or (d shl 16) or (d shl 8) or d }
            r = y2 + (r * gn - y2) * sat; g = y2 + (g * gn - y2) * sat; b = y2 + (b * gn - y2) * sat
            val mx = max(r, max(g, b))
            if (mx > 1f && y2 < 1f) { val t = (1f - y2) / (mx - y2); r = y2 + (r - y2) * t; g = y2 + (g - y2) * t; b = y2 + (b - y2) * t }
            val ri = disp[(r.coerceIn(0f, 1f) * DISP).toInt()]
            val gi = disp[(g.coerceIn(0f, 1f) * DISP).toInt()]
            val bi = disp[(b.coerceIn(0f, 1f) * DISP).toInt()]
            return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
    }
    private const val DISP = 16383
    private const val LN2 = 0.6931472f
    private fun smooth(e0: Float, e1: Float, x: Float): Float { val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
    /** ProfileToneCurve: (x, y) pairs, linear interpolation. */
    private fun curveAt(c: FloatArray, x: Float): Float {
        val n = c.size / 2
        if (x <= c[0]) return c[1]
        for (i in 1 until n) if (x <= c[i * 2]) { val x0 = c[i * 2 - 2]; val y0 = c[i * 2 - 1]; val x1 = c[i * 2]; val y1 = c[i * 2 + 1]; return y0 + (y1 - y0) * ((x - x0) / (x1 - x0).coerceAtLeast(1e-6f)) }
        return c[n * 2 - 1]
    }

    /** Develops a preview with the current settings (fast enough for live sliders). */
    fun develop(src: Linear, m: Meta, dev: Dev, exposure: Float, highlights: Float, shadows: Float, whites: Float, blacks: Float, temp: Float, tint: Float): Bitmap {
        val p = Pipe(withDcp(m, dev), dev, exposure, highlights, shadows, whites, blacks, temp, tint)
        val n = src.w * src.h
        var out = IntArray(n)
        val f = src.rgb
        val vg = if (!dev.vigOn) null else src.vig
        val g3 = if (!dev.vigOn) null else src.g3
        for (i in 0 until n) out[i] = if (g3 == null) p.px(f[i * 3], f[i * 3 + 1], f[i * 3 + 2], vg?.get(i) ?: 1f)
            else p.px(f[i * 3] * g3[i * 3], f[i * 3 + 1] * g3[i * 3 + 1], f[i * 3 + 2] * g3[i * 3 + 2], vg?.get(i) ?: 1f)
        val wp = m.warp
        if (wp != null && dev.shapeOn) try {
            val (cx, cy) = orientCentre(m.orientation, wp.cx, wp.cy)
            out = warp(out, src.w, src.h, wp, cx, cy)
        } catch (_: OutOfMemoryError) {}
        val dw = m.dewarp
        if (dw != null && dev.shapeOn && m.orientation <= 1) try {
            out = dewarp(out, src.w, src.h, dw, m.cropW, m.cropH)
        } catch (_: OutOfMemoryError) {}
        return Bitmap.createBitmap(out, src.w, src.h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Full-quality develop for export: decodes the DNG again, demosaics (full size when the export
     * needs it, else half-size superpixels), develops, applies the file's lens corrections, turns
     * upright and fits [maxSide].
     */
    fun export(context: Context, m: Meta, dev: Dev, exposure: Float, highlights: Float, shadows: Float, whites: Float, blacks: Float, temp: Float, tint: Float, maxSide: Int): Bitmap {
        val s = decode(context, m)
        if (dev.wb == 1 && m.autoMul == null) { preview(context, m, 512) } // fills autoMul
        val p = Pipe(withDcp(m, dev), dev, exposure, highlights, shadows, whites, blacks, temp, tint)
        val vig = if (!dev.vigOn) null else (m.vignette ?: m.estVignette)
        val lens3 = dev.vigOn && m.gains3.isNotEmpty()
        val full = max(s.w, s.h)
        var bmp: Bitmap = if (s.cfa == null || maxSide * 1.0f <= full / 2f) {
            val half = superpixel(s)
            val out = IntArray(half.w * half.h)
            val t3 = FloatArray(3)
            val sx = s.w.toFloat() / half.w; val sy = s.h.toFloat() / half.h
            for (i in out.indices) {
                val g = vig?.let { vigGain(it, (i % half.w + 0.5f) * sx, (i / half.w + 0.5f) * sy, s.w, s.h) } ?: 1f
                if (lens3) {
                    t3[0] = half.rgb[i * 3]; t3[1] = half.rgb[i * 3 + 1]; t3[2] = half.rgb[i * 3 + 2]
                    gain3(m, (i % half.w + 0.5f) * sx, (i / half.w + 0.5f) * sy, t3)
                    out[i] = p.px(t3[0], t3[1], t3[2], g)
                } else out[i] = p.px(half.rgb[i * 3], half.rgb[i * 3 + 1], half.rgb[i * 3 + 2], g)
            }
            Bitmap.createBitmap(out, half.w, half.h, Bitmap.Config.ARGB_8888)
        } else {
            val w = s.w; val h = s.h
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val row = IntArray(w)
            val rgb = FloatArray(3)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (s.bayer) mhc(s, x, y, rgb) else generic(s, x, y, rgb)
                    if (lens3) gain3(m, x + 0.5f, y + 0.5f, rgb)
                    row[x] = p.px(rgb[0], rgb[1], rgb[2], vig?.let { vigGain(it, x.toFloat(), y.toFloat(), w, h) } ?: 1f)
                }
                b.setPixels(row, 0, w, 0, y, w, 1)
            }
            b
        }
        // Lens distortion / colour fringing from the file (in the stored orientation).
        val wp = m.warp
        if (wp != null && dev.shapeOn) try {
            val w = bmp.width; val h = bmp.height
            val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
            bmp.recycle()
            val o = warp(px, w, h, wp, wp.cx, wp.cy)
            bmp = Bitmap.createBitmap(o, w, h, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {}
        // DJI drones: the lens calibration from the file.
        val dw = m.dewarp
        if (dw != null && dev.shapeOn) try {
            val w = bmp.width; val h = bmp.height
            val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
            bmp.recycle()
            bmp = Bitmap.createBitmap(dewarp(px, w, h, dw, m.cropW, m.cropH), w, h, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {}
        // Upright, then fit the export size.
        val mx = Matrix()
        when (m.orientation) {
            2 -> mx.postScale(-1f, 1f); 3 -> mx.postRotate(180f); 4 -> mx.postScale(1f, -1f)
            5 -> { mx.postRotate(90f); mx.postScale(-1f, 1f) }; 6 -> mx.postRotate(90f)
            7 -> { mx.postRotate(270f); mx.postScale(-1f, 1f) }; 8 -> mx.postRotate(270f)
        }
        val long = max(bmp.width, bmp.height)
        if (long > maxSide) { val k = maxSide.toFloat() / long; mx.postScale(k, k) }
        if (mx.isIdentity) return bmp
        val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mx, true)
        if (out !== bmp) bmp.recycle()
        return out
    }

    /** Demosaic for any colour filter pattern: each colour averaged over one pattern repeat around the pixel. */
    private fun generic(s: Sensor, x: Int, y: Int, o: FloatArray) {
        val sum = floatArrayOf(0f, 0f, 0f); val n = intArrayOf(0, 0, 0)
        val rx = s.cw / 2; val ry = s.ch / 2
        for (dy in -ry..ry) for (dx in -rx..rx) {
            val xx = x + dx; val yy = y + dy
            if (xx !in 0 until s.w || yy !in 0 until s.h) continue
            val c = s.color(xx, yy)
            // Nearer samples count more.
            val wgt = 1f / (1 + dx * dx + dy * dy)
            sum[c] += s.v(yy * s.w + xx) * wgt; n[c]++
        }
        val own = s.color(x, y)
        for (c in 0 until 3) o[c] = if (c == own) s.v(y * s.w + x) else 0f
        // Weighted means (weights recomputed to normalise).
        val ws = floatArrayOf(0f, 0f, 0f)
        for (dy in -ry..ry) for (dx in -rx..rx) {
            val xx = x + dx; val yy = y + dy
            if (xx !in 0 until s.w || yy !in 0 until s.h) continue
            ws[s.color(xx, yy)] += 1f / (1 + dx * dx + dy * dy)
        }
        for (c in 0 until 3) if (c != own && ws[c] > 0f) o[c] = sum[c] / ws[c]
    }

    /** Malvar-He-Cutler demosaic of one pixel (gradient-corrected bilinear), camera RGB into [o]. */
    private fun mhc(s: Sensor, x: Int, y: Int, o: FloatArray) {
        val w = s.w; val h = s.h
        fun ix(v: Int, n: Int) = if (v < 0) -v else if (v >= n) 2 * n - 2 - v else v
        fun at(dx: Int, dy: Int) = s.v(ix(y + dy, h) * w + ix(x + dx, w))
        val c = s.color(x, y)
        val ctr = at(0, 0)
        if (c == 1) {
            val g = ctr
            val diag = at(1, 1) + at(-1, 1) + at(1, -1) + at(-1, -1)
            val rowColor = s.color(ix(x + 1, w), y)
            val horiz = (5f * g + 4f * (at(1, 0) + at(-1, 0)) - diag - (at(2, 0) + at(-2, 0)) + 0.5f * (at(0, 2) + at(0, -2))) / 8f
            val vert = (5f * g + 4f * (at(0, 1) + at(0, -1)) - diag - (at(0, 2) + at(0, -2)) + 0.5f * (at(2, 0) + at(-2, 0))) / 8f
            o[1] = g
            if (rowColor == 0) { o[0] = max(horiz, 0f); o[2] = max(vert, 0f) } else { o[2] = max(horiz, 0f); o[0] = max(vert, 0f) }
        } else {
            val cross2 = at(2, 0) + at(-2, 0) + at(0, 2) + at(0, -2)
            val g = (2f * (at(1, 0) + at(-1, 0) + at(0, 1) + at(0, -1)) + 4f * ctr - cross2) / 8f
            val other = (6f * ctr + 2f * (at(1, 1) + at(-1, 1) + at(1, -1) + at(-1, -1)) - 1.5f * cross2) / 8f
            o[1] = max(g, 0f)
            if (c == 0) { o[0] = ctr; o[2] = max(other, 0f) } else { o[2] = ctr; o[0] = max(other, 0f) }
        }
    }

    // ------------------------------------------------------------------ embedded preview

    /** The camera's own JPEG preview inside the DNG, already cropped and turned like the developed picture. */
    class Embedded(val bitmap: Bitmap, /** Camera model, for learning how its previews differ from our develop. */ val camera: String)

    /**
     * Pulls the largest preview the camera stored in the DNG (a few ms — no sensor data is read),
     * so the editor can show the photo at once while the RAW itself is still being developed.
     */
    fun embedded(context: Context, uri: Uri, minSide: Int = 1400): Embedded? =
        if (!isDng(context, uri) && isOtherRaw(context, uri)) embeddedOther(context, uri, minSide) else embeddedDng(context, uri, minSide)

    private fun embeddedDng(context: Context, uri: Uri, minSide: Int): Embedded? = try {
        Src(context, uri).use { s ->
            val h = s.bytes(0, 8)
            val order = when { h[0] == 'I'.code.toByte() && h[1] == 'I'.code.toByte() -> ByteOrder.LITTLE_ENDIAN; h[0] == 'M'.code.toByte() && h[1] == 'M'.code.toByte() -> ByteOrder.BIG_ENDIAN; else -> return@use null }
            val first = ByteBuffer.wrap(h).order(order).getInt(4).toLong() and 0xFFFFFFFFL
            val all = ArrayList<Ifd>()
            fun walk(pos: Long, depth: Int) {
                var p = pos; var guard = 0
                while (p > 0 && guard++ < 16) {
                    val (ifd, next) = readIfd(s, p, order) ?: return
                    all += ifd
                    if (depth < 3) ifd.nums(330)?.forEach { walk(it.toLong(), depth + 1) }
                    p = next
                }
            }
            walk(first, 0)
            val ifd0 = all.firstOrNull() ?: return@use null
            // Candidates: JPEG strips (not the raw), old-style JPEG thumbnails, then 8-bit RGB thumbnails.
            class Cand(val px: Double, val load: () -> Bitmap?)
            val cands = ArrayList<Cand>()
            for (ifd in all) {
                val pi = ifd.num(262, 6.0).toInt()
                if (pi == 32803 || pi == 34892) continue
                val comp = ifd.num(259, 1.0).toInt()
                val px = ifd.num(256) * ifd.num(257)
                if (comp == 6 || comp == 7) {
                    val off = ifd.nums(273); val cnt = ifd.nums(279)
                    if (off != null && cnt != null && off.size == 1 && cnt.size == 1 && cnt[0] > 0 && cnt[0] < 64e6)
                        cands += Cand(px.takeIf { it > 0 } ?: cnt[0], { jpeg(s.bytes(off[0].toLong(), cnt[0].toInt()), minSide) })
                }
                val jo = ifd.num(513); val jl = ifd.num(514)
                if (jo > 0 && jl > 0 && jl < 64e6) cands += Cand(px.takeIf { it > 0 } ?: jl, { jpeg(s.bytes(jo.toLong(), jl.toInt()), minSide) })
                if (comp == 1 && pi == 2 && ifd.num(277, 1.0).toInt() == 3 && ifd.num(258, 8.0).toInt() == 8 && px in 1.0..4e6) {
                    val w = ifd.num(256).toInt(); val ht = ifd.num(257).toInt()
                    val off = ifd.nums(273); val cnt = ifd.nums(279)
                    if (off != null && cnt != null) cands += Cand(px * 0.5, {
                        val buf = ByteArray(w * ht * 3); var at = 0
                        for (k in off.indices) { val b = s.bytes(off[k].toLong(), min(cnt.getOrElse(k) { 0.0 }.toInt(), buf.size - at)); System.arraycopy(b, 0, buf, at, b.size); at += b.size; if (at >= buf.size) break }
                        if (at < buf.size) null else Bitmap.createBitmap(IntArray(w * ht) { i -> (0xFF shl 24) or ((buf[i * 3].toInt() and 0xFF) shl 16) or ((buf[i * 3 + 1].toInt() and 0xFF) shl 8) or (buf[i * 3 + 2].toInt() and 0xFF) }, w, ht, Bitmap.Config.ARGB_8888)
                    })
                }
            }
            val bmp = cands.sortedByDescending { it.px }.firstNotNullOfOrNull { c -> try { c.load() } catch (_: Throwable) { null } } ?: return@use null
            val camera = ifd0.text(50708) ?: listOfNotNull(ifd0.text(271), ifd0.text(272)).joinToString(" ").ifEmpty { "camera" }
            val o = ifd0.num(274, 1.0).toInt()
            Embedded(bmp, camera) to o
        }?.let { (e, o) ->
            // Same framing as the developed picture: its crop's shape, then the file's orientation.
            val m = try { open(context, uri) } catch (_: Throwable) { null }
            var b = e.bitmap
            if (m != null && m.cropW > 0 && m.cropH > 0) {
                val want = m.cropW.toFloat() / m.cropH; val have = b.width.toFloat() / b.height
                if (kotlin.math.abs(want / have - 1f) > 0.015f) b = if (have > want) {
                    val nw = (b.height * want).roundToInt().coerceIn(1, b.width); Bitmap.createBitmap(b, (b.width - nw) / 2, 0, nw, b.height)
                } else {
                    val nh = (b.width / want).roundToInt().coerceIn(1, b.height); Bitmap.createBitmap(b, 0, (b.height - nh) / 2, b.width, nh)
                }
            }
            Embedded(orientBitmap(b, o), e.camera)
        }
    } catch (_: Throwable) { null }

    private fun jpeg(b: ByteArray, minSide: Int): Bitmap? {
        if (b.size < 4 || (b[0].toInt() and 0xFF) != 0xFF || (b[1].toInt() and 0xFF) != 0xD8) return null
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size, o)
        if (o.outWidth <= 0) return null
        var sample = 1
        while (max(o.outWidth, o.outHeight) / (sample * 2) >= minSide) sample *= 2
        return android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Turns a stored picture by the TIFF orientation, exactly as the RAW develop does. */
    private fun orientBitmap(src: Bitmap, o: Int): Bitmap {
        if (o <= 1 || o > 8) return src
        val sw = src.width; val sh = src.height
        val swap = o >= 5
        val w = if (swap) sh else sw; val h = if (swap) sw else sh
        val inPx = IntArray(sw * sh).also { src.getPixels(it, 0, sw, 0, 0, sw, sh) }
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) { val (sx, sy) = orientXy(o, x, y, sw, sh); out[y * w + x] = inPx[sy * sw + sx] }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

}
