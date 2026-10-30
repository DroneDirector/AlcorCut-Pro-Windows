package androidx.exifinterface.media

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileDescriptor
import java.io.InputStream

/**
 * Photo metadata (EXIF): reads JPEG / TIFF / DNG tags and writes them back into JPEGs. Values come
 * back as text the same way as on the phone (rationals as decimals, GPS as "d/1,m/1,s/100"). Own code.
 */
class ExifInterface private constructor(private val file: File?, bytes: ByteArray?) {
    constructor(path: String) : this(File(path), null)
    constructor(input: InputStream) : this(null, input.readBytes())
    constructor(fd: FileDescriptor) : this(FileLinks.fileOf(fd), null)
    constructor(f: File) : this(f, null)

    private val tags = LinkedHashMap<String, String>()

    init {
        try { parse(bytes ?: file?.takeIf { it.exists() }?.readBytes() ?: ByteArray(0)) } catch (_: Throwable) {}
    }

    fun getAttribute(tag: String): String? = tags[tag]
    fun getAttributeInt(tag: String, default: Int): Int = tags[tag]?.let { v -> v.toIntOrNull() ?: v.toDoubleOrNull()?.toInt() } ?: default
    fun getAttributeDouble(tag: String, default: Double): Double = tags[tag]?.let { v ->
        v.toDoubleOrNull() ?: v.split('/').takeIf { it.size == 2 }?.let { (a, b) -> a.toDouble() / b.toDouble() }
    } ?: default
    fun hasAttribute(tag: String) = tag in tags
    fun setAttribute(tag: String, value: String?) { if (value == null) tags.remove(tag) else tags[tag] = value }

    val rotationDegrees: Int get() = when (getAttributeInt(TAG_ORIENTATION, 1)) { 3, 4 -> 180; 5, 6 -> 90; 7, 8 -> 270; else -> 0 }
    val isFlipped: Boolean get() = getAttributeInt(TAG_ORIENTATION, 1) in listOf(2, 4, 5, 7)

    val latLong: DoubleArray? get() {
        fun dms(s: String?): Double? = s?.split(',')?.takeIf { it.size == 3 }?.map { p -> p.split('/').let { it[0].toDouble() / it.getOrElse(1) { "1" }.toDouble() } }?.let { it[0] + it[1] / 60 + it[2] / 3600 }
        val la = dms(tags[TAG_GPS_LATITUDE]) ?: return null
        val lo = dms(tags[TAG_GPS_LONGITUDE]) ?: return null
        return doubleArrayOf(if (tags[TAG_GPS_LATITUDE_REF] == "S") -la else la, if (tags[TAG_GPS_LONGITUDE_REF] == "W") -lo else lo)
    }

    /** Writes the GPS position (degrees, as on the phone). */
    fun setLatLong(lat: Double, lon: Double) {
        fun dms(v: Double): String {
            val a = kotlin.math.abs(v); val d = a.toInt(); val mf = (a - d) * 60; val m = mf.toInt(); val sec = (mf - m) * 60
            return "$d/1,$m/1,${Math.round(sec * 10000)}/10000"
        }
        tags[TAG_GPS_LATITUDE] = dms(lat); tags[TAG_GPS_LATITUDE_REF] = if (lat >= 0) "N" else "S"
        tags[TAG_GPS_LONGITUDE] = dms(lon); tags[TAG_GPS_LONGITUDE_REF] = if (lon >= 0) "E" else "W"
    }
    fun getLatLong(out: FloatArray): Boolean { val l = latLong ?: return false; out[0] = l[0].toFloat(); out[1] = l[1].toFloat(); return true }

    /** Writes the tags back into the JPEG file (other formats are left as they are). */
    fun saveAttributes() {
        val f = file ?: throw java.io.IOException("No file")
        val src = f.readBytes()
        if (src.size < 4 || (src[0].toInt() and 0xFF) != 0xFF || (src[1].toInt() and 0xFF) != 0xD8) return
        val out = ByteArrayOutputStream(src.size + 4096)
        out.write(0xFF); out.write(0xD8)
        var i = 2
        // Keep a leading JFIF segment, drop the old EXIF / XMP segments, then write ours.
        if (i + 4 < src.size && (src[i + 1].toInt() and 0xFF) == 0xE0) {
            val len = ((src[i + 2].toInt() and 0xFF) shl 8) or (src[i + 3].toInt() and 0xFF)
            out.write(src, i, len + 2); i += len + 2
        }
        val app1 = build()
        out.write(0xFF); out.write(0xE1); out.write((app1.size + 2) shr 8); out.write((app1.size + 2) and 0xFF); out.write(app1)
        tags[TAG_XMP]?.let { xmp ->
            val body = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.ISO_8859_1) + xmp.toByteArray(Charsets.UTF_8)
            if (body.size < 65533) { out.write(0xFF); out.write(0xE1); out.write((body.size + 2) shr 8); out.write((body.size + 2) and 0xFF); out.write(body) }
        }
        while (i + 4 <= src.size) {
            val m = src[i + 1].toInt() and 0xFF
            if ((src[i].toInt() and 0xFF) != 0xFF || m == 0xDA || m == 0xD9) break
            val len = ((src[i + 2].toInt() and 0xFF) shl 8) or (src[i + 3].toInt() and 0xFF)
            val isExifOrXmp = m == 0xE1 && len > 8 && (String(src, i + 4, 4, Charsets.ISO_8859_1) == "Exif" || String(src, i + 4, minOf(28, len - 2), Charsets.ISO_8859_1).startsWith("http://ns.adobe.com/xap"))
            if (!isExifOrXmp) out.write(src, i, len + 2)
            i += len + 2
        }
        out.write(src, i, src.size - i)
        val tmp = File(f.path + ".exif")
        tmp.writeBytes(out.toByteArray())
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    // ------------------------------------------------------------------ reading

    private fun parse(b: ByteArray) {
        if (b.size < 8) return
        if ((b[0].toInt() and 0xFF) == 0xFF && (b[1].toInt() and 0xFF) == 0xD8) {
            var i = 2
            while (i + 4 <= b.size) {
                if ((b[i].toInt() and 0xFF) != 0xFF) break
                val m = b[i + 1].toInt() and 0xFF
                if (m == 0xDA || m == 0xD9) break
                val len = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
                if (m == 0xE1 && len > 8) {
                    val head = String(b, i + 4, minOf(29, len - 2), Charsets.ISO_8859_1)
                    if (head.startsWith("Exif")) tiff(b, i + 10)
                    else if (head.startsWith("http://ns.adobe.com/xap/1.0/")) tags[TAG_XMP] = String(b, i + 4 + 29, len - 2 - 29, Charsets.UTF_8)
                }
                i += len + 2
            }
        } else if ((b[0] == 'I'.code.toByte() && b[1] == 'I'.code.toByte()) || (b[0] == 'M'.code.toByte() && b[1] == 'M'.code.toByte())) {
            tiff(b, 0)
        }
    }

    private fun tiff(b: ByteArray, o: Int) {
        val le = b[o] == 'I'.code.toByte()
        fun u16(p: Int) = if (le) (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8) else ((b[p].toInt() and 0xFF) shl 8) or (b[p + 1].toInt() and 0xFF)
        fun u32(p: Int): Long = if (le) (u16(p).toLong() or (u16(p + 2).toLong() shl 16)) else ((u16(p).toLong() shl 16) or u16(p + 2).toLong())
        val visited = HashSet<Int>()
        fun ifd(at: Int, names: Map<Int, String>) {
            if (at <= 0 || o + at + 2 > b.size || !visited.add(at)) return
            val p = o + at
            val n = u16(p)
            for (k in 0 until n) {
                val e = p + 2 + k * 12
                if (e + 12 > b.size) return
                val tag = u16(e); val type = u16(e + 2); val count = u32(e + 4).toInt()
                val size = count * (when (type) { 3, 8 -> 2; 4, 9, 11 -> 4; 5, 10, 12 -> 8; else -> 1 })
                val vp = if (size <= 4) e + 8 else o + u32(e + 8).toInt()
                if (count < 0 || vp < 0 || vp + size > b.size) continue
                when (tag) {
                    0x8769 -> { ifd(u32(e + 8).toInt(), EXIF_TAGS); continue }
                    0x8825 -> { ifd(u32(e + 8).toInt(), GPS_TAGS); continue }
                }
                val name = names[tag] ?: continue
                val v = when (type) {
                    2 -> String(b, vp, count, Charsets.UTF_8).trimEnd('\u0000', ' ')
                    7 -> if (tag == 0x9286) String(b, vp + minOf(8, count), maxOf(0, count - 8), Charsets.UTF_8).trimEnd('\u0000', ' ') else (0 until minOf(count, 16)).joinToString(",") { (b[vp + it].toInt() and 0xFF).toString() }
                    1 -> (0 until count).joinToString(",") { (b[vp + it].toInt() and 0xFF).toString() }
                    3 -> (0 until count).joinToString(",") { u16(vp + it * 2).toString() }
                    4 -> (0 until count).joinToString(",") { u32(vp + it * 4).toString() }
                    9 -> (0 until count).joinToString(",") { u32(vp + it * 4).toInt().toString() }
                    5, 10 -> {
                        val parts = (0 until count).map { j ->
                            val num = u32(vp + j * 8); val den = u32(vp + j * 8 + 4)
                            if (type == 10) num.toInt().toLong() to den.toInt().toLong() else num to den
                        }
                        if (tag in GPS_RATIONAL_LISTS && names === GPS_TAGS) parts.joinToString(",") { "${it.first}/${it.second}" }
                        else parts.joinToString(",") { (n0, d0) -> if (d0 == 0L) "0" else fmt(n0.toDouble() / d0) }
                    }
                    else -> null
                } ?: continue
                tags[name] = v
            }
        }
        ifd(u32(o + 4).toInt(), MAIN_TAGS)
    }

    private fun fmt(d: Double): String = if (d == Math.floor(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString()

    // ------------------------------------------------------------------ writing

    private fun build(): ByteArray {
        val main = ArrayList<Pair<Int, String>>(); val exif = ArrayList<Pair<Int, String>>(); val gps = ArrayList<Pair<Int, String>>()
        for ((k, v) in tags) {
            if (k == TAG_XMP) continue
            MAIN_TAGS.entries.firstOrNull { it.value == k }?.let { main += it.key to v; return@let }
                ?: EXIF_TAGS.entries.firstOrNull { it.value == k }?.let { exif += it.key to v }
                ?: GPS_TAGS.entries.firstOrNull { it.value == k }?.let { gps += it.key to v }
        }
        val w = TiffWriter()
        return w.write(main.sortedBy { it.first }, exif.sortedBy { it.first }, gps.sortedBy { it.first })
    }

    private class TiffWriter {
        private fun typeOf(tag: Int, gps: Boolean): Int = when {
            gps && tag in GPS_RATIONAL_LISTS -> 5
            gps && tag == 0x0005 -> 1
            gps && tag == 0x0000 -> 1
            tag in RATIONAL -> 5
            tag in SRATIONAL -> 10
            tag in SHORT -> 3
            tag in LONG -> 4
            tag == 0x9286 -> 7
            tag in UNDEFINED -> 7
            else -> 2
        }

        private fun encode(tag: Int, v: String, gps: Boolean): Pair<Int, ByteArray>? {
            val t = typeOf(tag, gps)
            val buf = java.nio.ByteBuffer.allocate(4096).order(java.nio.ByteOrder.BIG_ENDIAN)
            var count = 0
            try {
                when (t) {
                    2 -> { val s = v.toByteArray(Charsets.UTF_8) + 0; buf.put(s); count = s.size }
                    7 -> {
                        if (tag == 0x9286) { val s = "ASCII\u0000\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + v.toByteArray(Charsets.US_ASCII); buf.put(s); count = s.size }
                        else { val s = v.split(',').map { it.trim().toInt().toByte() }.toByteArray(); buf.put(s); count = s.size }
                    }
                    1 -> { val s = v.split(',').map { it.trim().toInt().toByte() }.toByteArray(); buf.put(s); count = s.size }
                    3 -> v.split(',').forEach { buf.putShort(it.trim().toDouble().toInt().toShort()); count++ }
                    4 -> v.split(',').forEach { buf.putInt(it.trim().toDouble().toLong().toInt()); count++ }
                    5, 10 -> v.split(',').forEach { part ->
                        val p = part.trim()
                        if ('/' in p) { val (a, b) = p.split('/'); buf.putInt(a.trim().toLong().toInt()); buf.putInt(b.trim().toLong().toInt()) }
                        else {
                            val d = p.toDouble()
                            var den = 1L
                            while (den < 1_000_000 && kotlin.math.abs(d * den - Math.round(d * den)) > 1e-9) den *= 10
                            buf.putInt(Math.round(d * den).toInt()); buf.putInt(den.toInt())
                        }
                        count++
                    }
                }
            } catch (_: Throwable) { return null }
            buf.flip()
            val bytes = ByteArray(buf.remaining()); buf.get(bytes)
            return (t shl 16 or (count and 0xFFFF)) to bytes
        }

        fun write(main: List<Pair<Int, String>>, exif: List<Pair<Int, String>>, gps: List<Pair<Int, String>>): ByteArray {
            data class Entry(val tag: Int, val type: Int, val count: Int, val data: ByteArray)
            fun entries(list: List<Pair<Int, String>>, isGps: Boolean) = list.mapNotNull { (t, v) ->
                encode(t, v, isGps)?.let { (tc, d) -> Entry(t, tc ushr 16, if ((tc ushr 16) in listOf(1, 2, 7)) d.size else tc and 0xFFFF, d) }
            }
            val m = entries(main, false).toMutableList(); val e = entries(exif, false); val g = entries(gps, true)
            // Pointers to the EXIF and GPS directories (filled in below).
            if (e.isNotEmpty()) m += Entry(0x8769, 4, 1, ByteArray(4))
            if (g.isNotEmpty()) m += Entry(0x8825, 4, 1, ByteArray(4))
            m.sortBy { it.tag }
            fun dirSize(l: List<Entry>) = 2 + l.size * 12 + 4
            fun dataSize(l: List<Entry>) = l.sumOf { if (it.data.size > 4) it.data.size + (it.data.size and 1) else 0 }
            val mainAt = 8
            val exifAt = mainAt + dirSize(m) + dataSize(m)
            val gpsAt = exifAt + (if (e.isEmpty()) 0 else dirSize(e) + dataSize(e))
            val out = java.nio.ByteBuffer.allocate(gpsAt + (if (g.isEmpty()) 0 else dirSize(g) + dataSize(g)) + 16).order(java.nio.ByteOrder.BIG_ENDIAN)
            out.put("Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1))
            val base = out.position()
            out.put('M'.code.toByte()); out.put('M'.code.toByte()); out.putShort(42); out.putInt(mainAt)
            fun dir(l: List<Entry>, at: Int) {
                out.position(base + at)
                out.putShort(l.size.toShort())
                var dataAt = at + dirSize(l)
                val blobs = ArrayList<Pair<Int, ByteArray>>()
                for (x in l) {
                    out.putShort(x.tag.toShort()); out.putShort(x.type.toShort()); out.putInt(x.count)
                    when {
                        x.tag == 0x8769 -> out.putInt(exifAt)
                        x.tag == 0x8825 -> out.putInt(gpsAt)
                        x.data.size <= 4 -> { out.put(x.data); repeat(4 - x.data.size) { out.put(0) } }
                        else -> { out.putInt(dataAt); blobs += dataAt to x.data; dataAt += x.data.size + (x.data.size and 1) }
                    }
                }
                out.putInt(0)
                for ((p, d) in blobs) { out.position(base + p); out.put(d) }
            }
            dir(m, mainAt)
            if (e.isNotEmpty()) dir(e, exifAt)
            if (g.isNotEmpty()) dir(g, gpsAt)
            val end = base + (if (g.isNotEmpty()) gpsAt + dirSize(g) + dataSize(g) else if (e.isNotEmpty()) exifAt + dirSize(e) + dataSize(e) else mainAt + dirSize(m) + dataSize(m))
            return out.array().copyOf(end)
        }
    }

    companion object {
        const val TAG_IMAGE_DESCRIPTION = "ImageDescription"
        const val TAG_MAKE = "Make"
        const val TAG_MODEL = "Model"
        const val TAG_ORIENTATION = "Orientation"
        const val TAG_SOFTWARE = "Software"
        const val TAG_DATETIME = "DateTime"
        const val TAG_ARTIST = "Artist"
        const val TAG_COPYRIGHT = "Copyright"
        const val TAG_IMAGE_WIDTH = "ImageWidth"
        const val TAG_IMAGE_LENGTH = "ImageLength"
        const val TAG_X_RESOLUTION = "XResolution"
        const val TAG_Y_RESOLUTION = "YResolution"
        const val TAG_RESOLUTION_UNIT = "ResolutionUnit"
        const val TAG_EXPOSURE_TIME = "ExposureTime"
        const val TAG_F_NUMBER = "FNumber"
        const val TAG_EXPOSURE_PROGRAM = "ExposureProgram"
        const val TAG_PHOTOGRAPHIC_SENSITIVITY = "PhotographicSensitivity"
        const val TAG_ISO_SPEED_RATINGS = "PhotographicSensitivity"
        const val TAG_DATETIME_ORIGINAL = "DateTimeOriginal"
        const val TAG_DATETIME_DIGITIZED = "DateTimeDigitized"
        const val TAG_OFFSET_TIME = "OffsetTime"
        const val TAG_OFFSET_TIME_ORIGINAL = "OffsetTimeOriginal"
        const val TAG_OFFSET_TIME_DIGITIZED = "OffsetTimeDigitized"
        const val TAG_SHUTTER_SPEED_VALUE = "ShutterSpeedValue"
        const val TAG_APERTURE_VALUE = "ApertureValue"
        const val TAG_EXPOSURE_BIAS_VALUE = "ExposureBiasValue"
        const val TAG_MAX_APERTURE_VALUE = "MaxApertureValue"
        const val TAG_METERING_MODE = "MeteringMode"
        const val TAG_FLASH = "Flash"
        const val TAG_FOCAL_LENGTH = "FocalLength"
        const val TAG_USER_COMMENT = "UserComment"
        const val TAG_COLOR_SPACE = "ColorSpace"
        const val TAG_PIXEL_X_DIMENSION = "PixelXDimension"
        const val TAG_PIXEL_Y_DIMENSION = "PixelYDimension"
        const val TAG_EXPOSURE_MODE = "ExposureMode"
        const val TAG_WHITE_BALANCE = "WhiteBalance"
        const val TAG_DIGITAL_ZOOM_RATIO = "DigitalZoomRatio"
        const val TAG_FOCAL_LENGTH_IN_35MM_FILM = "FocalLengthIn35mmFilm"
        const val TAG_SCENE_CAPTURE_TYPE = "SceneCaptureType"
        const val TAG_BODY_SERIAL_NUMBER = "BodySerialNumber"
        const val TAG_LENS_SPECIFICATION = "LensSpecification"
        const val TAG_LENS_MAKE = "LensMake"
        const val TAG_LENS_MODEL = "LensModel"
        const val TAG_GPS_VERSION_ID = "GPSVersionID"
        const val TAG_GPS_LATITUDE_REF = "GPSLatitudeRef"
        const val TAG_GPS_LATITUDE = "GPSLatitude"
        const val TAG_GPS_LONGITUDE_REF = "GPSLongitudeRef"
        const val TAG_GPS_LONGITUDE = "GPSLongitude"
        const val TAG_GPS_ALTITUDE_REF = "GPSAltitudeRef"
        const val TAG_GPS_ALTITUDE = "GPSAltitude"
        const val TAG_GPS_TIMESTAMP = "GPSTimeStamp"
        const val TAG_GPS_IMG_DIRECTION_REF = "GPSImgDirectionRef"
        const val TAG_GPS_IMG_DIRECTION = "GPSImgDirection"
        const val TAG_GPS_DATESTAMP = "GPSDateStamp"
        const val TAG_XMP = "Xmp"

        const val ORIENTATION_UNDEFINED = 0
        const val ORIENTATION_NORMAL = 1
        const val ORIENTATION_FLIP_HORIZONTAL = 2
        const val ORIENTATION_ROTATE_180 = 3
        const val ORIENTATION_FLIP_VERTICAL = 4
        const val ORIENTATION_TRANSPOSE = 5
        const val ORIENTATION_ROTATE_90 = 6
        const val ORIENTATION_TRANSVERSE = 7
        const val ORIENTATION_ROTATE_270 = 8

        private val MAIN_TAGS = mapOf(
            0x010E to TAG_IMAGE_DESCRIPTION, 0x010F to TAG_MAKE, 0x0110 to TAG_MODEL, 0x0112 to TAG_ORIENTATION,
            0x011A to TAG_X_RESOLUTION, 0x011B to TAG_Y_RESOLUTION, 0x0128 to TAG_RESOLUTION_UNIT,
            0x0131 to TAG_SOFTWARE, 0x0132 to TAG_DATETIME, 0x013B to TAG_ARTIST, 0x8298 to TAG_COPYRIGHT,
        )
        private val EXIF_TAGS = mapOf(
            0x829A to TAG_EXPOSURE_TIME, 0x829D to TAG_F_NUMBER, 0x8822 to TAG_EXPOSURE_PROGRAM, 0x8827 to TAG_PHOTOGRAPHIC_SENSITIVITY,
            0x9003 to TAG_DATETIME_ORIGINAL, 0x9004 to TAG_DATETIME_DIGITIZED, 0x9010 to TAG_OFFSET_TIME, 0x9011 to TAG_OFFSET_TIME_ORIGINAL,
            0x9012 to TAG_OFFSET_TIME_DIGITIZED, 0x9201 to TAG_SHUTTER_SPEED_VALUE, 0x9202 to TAG_APERTURE_VALUE, 0x9204 to TAG_EXPOSURE_BIAS_VALUE,
            0x9205 to TAG_MAX_APERTURE_VALUE, 0x9207 to TAG_METERING_MODE, 0x9209 to TAG_FLASH, 0x920A to TAG_FOCAL_LENGTH, 0x9286 to TAG_USER_COMMENT,
            0xA001 to TAG_COLOR_SPACE, 0xA002 to TAG_PIXEL_X_DIMENSION, 0xA003 to TAG_PIXEL_Y_DIMENSION, 0xA402 to TAG_EXPOSURE_MODE,
            0xA403 to TAG_WHITE_BALANCE, 0xA404 to TAG_DIGITAL_ZOOM_RATIO, 0xA405 to TAG_FOCAL_LENGTH_IN_35MM_FILM, 0xA406 to TAG_SCENE_CAPTURE_TYPE,
            0xA431 to TAG_BODY_SERIAL_NUMBER, 0xA432 to TAG_LENS_SPECIFICATION, 0xA433 to TAG_LENS_MAKE, 0xA434 to TAG_LENS_MODEL,
        )
        private val GPS_TAGS = mapOf(
            0x0000 to TAG_GPS_VERSION_ID, 0x0001 to TAG_GPS_LATITUDE_REF, 0x0002 to TAG_GPS_LATITUDE, 0x0003 to TAG_GPS_LONGITUDE_REF,
            0x0004 to TAG_GPS_LONGITUDE, 0x0005 to TAG_GPS_ALTITUDE_REF, 0x0006 to TAG_GPS_ALTITUDE, 0x0007 to TAG_GPS_TIMESTAMP,
            0x0010 to TAG_GPS_IMG_DIRECTION_REF, 0x0011 to TAG_GPS_IMG_DIRECTION, 0x001D to TAG_GPS_DATESTAMP,
        )
        private val GPS_RATIONAL_LISTS = setOf(0x0002, 0x0004, 0x0006, 0x0007, 0x0011)
        private val RATIONAL = setOf(0x011A, 0x011B, 0x829A, 0x829D, 0x9202, 0x9205, 0x920A, 0xA404, 0xA432)
        private val SRATIONAL = setOf(0x9201, 0x9204)
        private val SHORT = setOf(0x0112, 0x0128, 0x8822, 0x8827, 0x9207, 0x9209, 0xA001, 0xA402, 0xA403, 0xA405, 0xA406)
        private val LONG = setOf(0xA002, 0xA003)
        private val UNDEFINED = setOf<Int>()
    }
}

/** Remembers which file an open file handle belongs to (for code that passes handles around). */
object FileLinks {
    private val map = java.util.Collections.synchronizedMap(java.util.WeakHashMap<FileDescriptor, File>())
    fun link(fd: FileDescriptor, f: File) { map[fd] = f }
    fun fileOf(fd: FileDescriptor): File? = map[fd]
}
