package android.graphics

import kotlin.math.pow

/**
 * Colour spaces for saving (sRGB, Display P3, Adobe RGB) with the conversion maths and the ICC
 * profile written into the file. Own code.
 */
open class ColorSpace private constructor(val name: String, val id: Int, internal val toXyz: FloatArray, internal val gamma: Float, internal val srgbCurve: Boolean) {
    enum class Named { SRGB, LINEAR_SRGB, EXTENDED_SRGB, LINEAR_EXTENDED_SRGB, BT709, BT2020, DCI_P3, DISPLAY_P3, NTSC_1953, SMPTE_C, ADOBE_RGB, PRO_PHOTO_RGB, ACES, ACESCG, CIE_XYZ, CIE_LAB, BT2020_HLG, BT2020_PQ }

    override fun toString() = name
    override fun equals(other: Any?) = other is ColorSpace && other.id == id
    override fun hashCode() = id

    internal fun decode(v: Float): Float = if (srgbCurve) (if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)) else v.pow(gamma)
    internal fun encode(v: Float): Float = if (srgbCurve) (if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f) else v.pow(1f / gamma)

    companion object {
        private fun m(vararg f: Float) = f
        private val srgb = ColorSpace("sRGB IEC61966-2.1", 0, m(0.4124f, 0.3576f, 0.1805f, 0.2126f, 0.7152f, 0.0722f, 0.0193f, 0.1192f, 0.9505f), 2.2f, true)
        private val p3 = ColorSpace("Display P3", 7, m(0.4866f, 0.2657f, 0.1982f, 0.2290f, 0.6917f, 0.0793f, 0.0000f, 0.0451f, 1.0439f), 2.2f, true)
        private val adobe = ColorSpace("Adobe RGB (1998)", 11, m(0.5767f, 0.1856f, 0.1882f, 0.2974f, 0.6273f, 0.0753f, 0.0270f, 0.0707f, 0.9911f), 563f / 256f, false)
        private val linear = ColorSpace("sRGB IEC61966-2.1 (Linear)", 1, srgb.toXyz, 1f, false)

        fun get(n: Named): ColorSpace = when (n) {
            Named.DISPLAY_P3, Named.DCI_P3 -> p3
            Named.ADOBE_RGB -> adobe
            Named.LINEAR_SRGB, Named.LINEAR_EXTENDED_SRGB -> linear
            else -> srgb
        }

        private fun inv(a: FloatArray): FloatArray {
            val d = a[0] * (a[4] * a[8] - a[5] * a[7]) - a[1] * (a[3] * a[8] - a[5] * a[6]) + a[2] * (a[3] * a[7] - a[4] * a[6])
            return floatArrayOf(
                (a[4] * a[8] - a[5] * a[7]) / d, (a[2] * a[7] - a[1] * a[8]) / d, (a[1] * a[5] - a[2] * a[4]) / d,
                (a[5] * a[6] - a[3] * a[8]) / d, (a[0] * a[8] - a[2] * a[6]) / d, (a[2] * a[3] - a[0] * a[5]) / d,
                (a[3] * a[7] - a[4] * a[6]) / d, (a[1] * a[6] - a[0] * a[7]) / d, (a[0] * a[4] - a[1] * a[3]) / d,
            )
        }

        /** Converts ARGB pixels from one space to another, in place. */
        internal fun convert(px: IntArray, from: ColorSpace, to: ColorSpace) {
            if (from == to) return
            val back = inv(to.toXyz); val a = from.toXyz
            val mm = FloatArray(9)
            for (i in 0..2) for (j in 0..2) mm[i * 3 + j] = back[i * 3] * a[j] + back[i * 3 + 1] * a[3 + j] + back[i * 3 + 2] * a[6 + j]
            val lin = FloatArray(256) { from.decode(it / 255f) }
            val enc = IntArray(4096) { (to.encode(it / 4095f) * 255f + 0.5f).toInt().coerceIn(0, 255) }
            java.util.stream.IntStream.range(0, px.size).parallel().forEach { k ->
                val p = px[k]
                val r = lin[(p shr 16) and 255]; val g = lin[(p shr 8) and 255]; val b = lin[p and 255]
                fun o(i: Int) = enc[((mm[i] * r + mm[i + 1] * g + mm[i + 2] * b).coerceIn(0f, 1f) * 4095f + 0.5f).toInt()]
                px[k] = (p and 0xFF000000.toInt()) or (o(0) shl 16) or (o(3) shl 8) or o(6)
            }
        }

        /** A small ICC v2 profile (primaries + tone curve) for [cs]. */
        internal fun icc(cs: ColorSpace): ByteArray {
            // D50-adapted colourants (Bradford) as ICC wants them.
            val brad = floatArrayOf(1.0478f, 0.0229f, -0.0501f, 0.0295f, 0.9905f, -0.0171f, -0.0092f, 0.0151f, 0.7517f)
            val x = cs.toXyz
            fun col(j: Int) = FloatArray(3) { i -> brad[i * 3] * x[j] + brad[i * 3 + 1] * x[3 + j] + brad[i * 3 + 2] * x[6 + j] }
            val tags = ArrayList<Pair<String, ByteArray>>()
            fun s15(v: Float) = java.nio.ByteBuffer.allocate(4).putInt(Math.round(v * 65536f)).array()
            fun xyz(v: FloatArray) = "XYZ ".toByteArray() + ByteArray(4) + s15(v[0]) + s15(v[1]) + s15(v[2])
            fun text(s: String) = "text".toByteArray() + ByteArray(4) + s.toByteArray(Charsets.US_ASCII) + 0
            fun desc(s: String): ByteArray {
                val a = s.toByteArray(Charsets.US_ASCII) + 0
                return "desc".toByteArray() + ByteArray(4) + java.nio.ByteBuffer.allocate(4).putInt(a.size).array() + a + ByteArray(12) + ByteArray(67)
            }
            val curve = if (cs.srgbCurve) {
                val n = 1024
                val b = java.nio.ByteBuffer.allocate(12 + n * 2)
                b.put("curv".toByteArray()); b.putInt(0); b.putInt(n)
                for (i in 0 until n) b.putShort((cs.decode(i / (n - 1f)) * 65535f + 0.5f).toInt().coerceIn(0, 65535).toShort())
                b.array()
            } else "curv".toByteArray() + ByteArray(4) + java.nio.ByteBuffer.allocate(6).putInt(1).putShort((cs.gamma * 256f + 0.5f).toInt().toShort()).array()
            tags += "desc" to desc(cs.name)
            tags += "cprt" to text("No copyright, use freely")
            tags += "wtpt" to xyz(floatArrayOf(0.9642f, 1.0f, 0.8249f))
            tags += "rXYZ" to xyz(col(0)); tags += "gXYZ" to xyz(col(1)); tags += "bXYZ" to xyz(col(2))
            tags += "rTRC" to curve; tags += "gTRC" to curve; tags += "bTRC" to curve
            val head = 128 + 4 + tags.size * 12
            val offsets = ArrayList<Int>(); var at = head
            val unique = LinkedHashMap<ByteArray, Int>()
            for ((_, d) in tags) {
                val existing = unique.entries.firstOrNull { it.key === d }?.value
                if (existing != null) { offsets += existing; continue }
                offsets += at; unique[d] = at; at += (d.size + 3) / 4 * 4
            }
            val out = java.nio.ByteBuffer.allocate(at)
            out.putInt(at); out.put("none".toByteArray()); out.putInt(0x02100000); out.put("mntr".toByteArray()); out.put("RGB ".toByteArray()); out.put("XYZ ".toByteArray())
            out.put(ByteArray(12)); out.put("acsp".toByteArray()); out.put("MSFT".toByteArray()); out.putInt(0); out.put(ByteArray(8)); out.put(ByteArray(8))
            out.putInt(0); out.put(s15(0.9642f)); out.put(s15(1.0f)); out.put(s15(0.8249f)); out.put(ByteArray(4))
            out.position(128); out.putInt(tags.size)
            tags.forEachIndexed { i, (sig, d) -> out.put(sig.toByteArray()); out.putInt(offsets[i]); out.putInt(d.size) }
            for ((d, o) in unique) { out.position(o); out.put(d) }
            return out.array()
        }
    }
}

/** Ultra HDR gain maps: saving them comes later on Windows (exports are standard range for now). */
class Gainmap(val gainmapContents: Bitmap) {
    init { throw UnsupportedOperationException("Ultra HDR") }
    fun setRatioMin(r: Float, g: Float, b: Float) {}
    fun setRatioMax(r: Float, g: Float, b: Float) {}
    fun setGamma(r: Float, g: Float, b: Float) {}
    fun setEpsilonSdr(r: Float, g: Float, b: Float) {}
    fun setEpsilonHdr(r: Float, g: Float, b: Float) {}
    var minDisplayRatioForHdrTransition = 1f
    var displayRatioForFullHdr = 1f
}
