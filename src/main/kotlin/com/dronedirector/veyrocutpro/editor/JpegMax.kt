package com.dronedirector.veyrocutpro.editor

import java.io.OutputStream
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * The app's own baseline JPEG encoder, for exports at full colour detail. Own code.
 *
 * Unlike the system encoder it keeps every colour sample (4:4:4 — no halving of the colour), and the
 * quality follows the usual 1–100 scale up to 100, where nothing is rounded away beyond the format
 * itself. It reads the picture a strip of 8 rows at a time, so even very large photos need almost no
 * memory. Opens everywhere a normal JPEG does.
 */
object JpegMax {
    private val ZZ = intArrayOf(
        0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5, 12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28,
        35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51, 58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63,
    )
    private val LUMA = intArrayOf(
        16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55, 14, 13, 16, 24, 40, 57, 69, 56, 14, 17, 22, 29, 51, 87, 80, 62,
        18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92, 49, 64, 78, 87, 103, 121, 120, 101, 72, 92, 95, 98, 112, 100, 103, 99,
    )
    private val CHROMA = IntArray(64) { 99 }.also { t ->
        val top = intArrayOf(17, 18, 24, 47, 18, 21, 26, 66, 24, 26, 56, 99, 47, 66, 99, 99)
        for (y in 0 until 4) for (x in 0 until 4) t[y * 8 + x] = top[y * 4 + x]
    }
    private val DC_L_BITS = intArrayOf(0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0)
    private val DC_L_VALS = IntArray(12) { it }
    private val DC_C_BITS = intArrayOf(0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0)
    private val DC_C_VALS = IntArray(12) { it }
    private val AC_L_BITS = intArrayOf(0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d)
    private val AC_L_VALS = intArrayOf(
        0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07, 0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xa1, 0x08,
        0x23, 0x42, 0xb1, 0xc1, 0x15, 0x52, 0xd1, 0xf0, 0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0a, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28,
        0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59,
        0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
        0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6,
        0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xe1, 0xe2,
        0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea, 0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8, 0xf9, 0xfa,
    )
    private val AC_C_BITS = intArrayOf(0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 0x77)
    private val AC_C_VALS = intArrayOf(
        0x00, 0x01, 0x02, 0x03, 0x11, 0x04, 0x05, 0x21, 0x31, 0x06, 0x12, 0x41, 0x51, 0x07, 0x61, 0x71, 0x13, 0x22, 0x32, 0x81, 0x08, 0x14, 0x42, 0x91,
        0xa1, 0xb1, 0xc1, 0x09, 0x23, 0x33, 0x52, 0xf0, 0x15, 0x62, 0x72, 0xd1, 0x0a, 0x16, 0x24, 0x34, 0xe1, 0x25, 0xf1, 0x17, 0x18, 0x19, 0x1a, 0x26,
        0x27, 0x28, 0x29, 0x2a, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58,
        0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87,
        0x88, 0x89, 0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4,
        0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda,
        0xe2, 0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8, 0xf9, 0xfa,
    )

    /** cos((2x+1)uπ/16) · C(u) / 2, so a row pass then a column pass give the standard DCT. */
    private val COS = FloatArray(64).also { c ->
        for (u in 0 until 8) for (x in 0 until 8) c[u * 8 + x] = (cos((2 * x + 1) * u * Math.PI / 16) * (if (u == 0) Math.sqrt(0.5) else 1.0) / 2).toFloat()
    }

    private class Huff(bits: IntArray, vals: IntArray) {
        val code = IntArray(256); val size = IntArray(256)
        init {
            var c = 0; var k = 0
            for (len in 1..16) { repeat(bits[len - 1]) { val v = vals[k++]; code[v] = c; size[v] = len; c++ }; c = c shl 1 }
        }
    }

    private class Bits(val out: OutputStream) {
        private var acc = 0L; private var n = 0
        private val buf = ByteArray(1 shl 16); private var pos = 0
        private fun byte(b: Int) { buf[pos++] = b.toByte(); if (pos == buf.size) { out.write(buf, 0, pos); pos = 0 } }
        fun put(code: Int, len: Int) {
            acc = (acc shl len) or (code.toLong() and ((1L shl len) - 1)); n += len
            while (n >= 8) { val b = ((acc shr (n - 8)) and 0xFF).toInt(); byte(b); if (b == 0xFF) byte(0); n -= 8 }
        }
        fun finish() { if (n > 0) put(0x7F, 7 - ((n - 1) % 8)); out.write(buf, 0, pos); pos = 0 }
    }

    /** The 64 quantiser steps (natural order) for [quality] 1…100, like the common IJG scaling. */
    fun table(base: IntArray, quality: Int): IntArray {
        val q = quality.coerceIn(1, 100)
        val s = if (q < 50) 5000 / q else 200 - 2 * q
        return IntArray(64) { ((base[it] * s + 50) / 100).coerceIn(1, 255) }
    }

    /**
     * Writes a w×h picture. [rows] fills `dst` with `count` rows of ARGB pixels from row `y`.
     * [icc] (optional) is embedded so wide-gamut exports keep their colour space.
     */
    fun write(out: OutputStream, w: Int, h: Int, quality: Int, icc: ByteArray?, rows: (y: Int, count: Int, dst: IntArray) -> Unit) {
        val ql = table(LUMA, quality); val qc = table(CHROMA, quality)
        fun seg(marker: Int, body: ByteArray) {
            out.write(0xFF); out.write(marker); out.write((body.size + 2) shr 8); out.write((body.size + 2) and 0xFF); out.write(body)
        }
        out.write(0xFF); out.write(0xD8)
        seg(0xE0, byteArrayOf(0x4A, 0x46, 0x49, 0x46, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0))
        if (icc != null && icc.isNotEmpty()) {
            // ICC_PROFILE in APP2 chunks (65519 bytes of profile at most per chunk).
            val max = 65519; val n = (icc.size + max - 1) / max
            for (i in 0 until n) {
                val part = icc.copyOfRange(i * max, minOf(icc.size, (i + 1) * max))
                seg(0xE2, "ICC_PROFILE".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, (i + 1).toByte(), n.toByte()) + part)
            }
        }
        seg(0xDB, ByteArray(130).also { b -> b[0] = 0; b[65] = 1; for (i in 0 until 64) { b[1 + i] = ql[ZZ[i]].toByte(); b[66 + i] = qc[ZZ[i]].toByte() } })
        seg(0xC0, byteArrayOf(8, (h shr 8).toByte(), h.toByte(), (w shr 8).toByte(), w.toByte(), 3, 1, 0x11, 0, 2, 0x11, 1, 3, 0x11, 1))
        fun dht(cls: Int, id: Int, bits: IntArray, vals: IntArray) =
            byteArrayOf(((cls shl 4) or id).toByte()) + ByteArray(16) { bits[it].toByte() } + ByteArray(vals.size) { vals[it].toByte() }
        seg(0xC4, dht(0, 0, DC_L_BITS, DC_L_VALS) + dht(1, 0, AC_L_BITS, AC_L_VALS) + dht(0, 1, DC_C_BITS, DC_C_VALS) + dht(1, 1, AC_C_BITS, AC_C_VALS))
        seg(0xDA, byteArrayOf(3, 1, 0x00, 2, 0x11, 3, 0x11, 0, 63, 0))

        val dcL = Huff(DC_L_BITS, DC_L_VALS); val acL = Huff(AC_L_BITS, AC_L_VALS)
        val dcC = Huff(DC_C_BITS, DC_C_VALS); val acC = Huff(AC_C_BITS, AC_C_VALS)
        // Reciprocal steps, so quantising is a multiply.
        val rl = FloatArray(64) { 1f / ql[it] }; val rc = FloatArray(64) { 1f / qc[it] }
        val bits = Bits(out)
        val strip = IntArray(w * 8)
        val yb = FloatArray(64); val cbb = FloatArray(64); val crb = FloatArray(64)
        val tmp = FloatArray(64); val coef = FloatArray(64); val qz = IntArray(64)
        var pY = 0; var pCb = 0; var pCr = 0

        fun category(v: Int): Int { var a = if (v < 0) -v else v; var n = 0; while (a > 0) { a = a shr 1; n++ }; return n }
        fun block(src: FloatArray, recip: FloatArray, prev: Int, dc: Huff, ac: Huff): Int {
            // 2-D DCT: rows, then columns.
            for (y in 0 until 8) for (u in 0 until 8) {
                var s = 0f; val o = y * 8; val c = u * 8
                for (x in 0 until 8) s += src[o + x] * COS[c + x]
                tmp[o + u] = s
            }
            for (u in 0 until 8) for (v in 0 until 8) {
                var s = 0f; val c = v * 8
                for (y in 0 until 8) s += tmp[y * 8 + u] * COS[c + y]
                coef[v * 8 + u] = s
            }
            for (i in 0 until 64) { val n = ZZ[i]; qz[i] = (coef[n] * recip[n]).roundToInt() }
            val d = qz[0] - prev
            val cat = category(d)
            bits.put(dc.code[cat], dc.size[cat]); if (cat > 0) bits.put(if (d < 0) d - 1 else d, cat)
            var run = 0
            for (i in 1 until 64) {
                val v = qz[i]
                if (v == 0) { run++; continue }
                while (run > 15) { bits.put(ac.code[0xF0], ac.size[0xF0]); run -= 16 }
                val c = category(v); val sym = (run shl 4) or c
                bits.put(ac.code[sym], ac.size[sym]); bits.put(if (v < 0) v - 1 else v, c)
                run = 0
            }
            if (run > 0) bits.put(ac.code[0], ac.size[0])
            return qz[0]
        }

        var y0 = 0
        while (y0 < h) {
            val n = minOf(8, h - y0)
            rows(y0, n, strip)
            var bx = 0
            while (bx < w) {
                for (yy in 0 until 8) {
                    val row = minOf(yy, n - 1) * w
                    for (xx in 0 until 8) {
                        val p = strip[row + minOf(bx + xx, w - 1)]
                        val r = ((p shr 16) and 0xFF).toFloat(); val g = ((p shr 8) and 0xFF).toFloat(); val b = (p and 0xFF).toFloat()
                        val i = yy * 8 + xx
                        yb[i] = 0.299f * r + 0.587f * g + 0.114f * b - 128f
                        cbb[i] = -0.168736f * r - 0.331264f * g + 0.5f * b
                        crb[i] = 0.5f * r - 0.418688f * g - 0.081312f * b
                    }
                }
                pY = block(yb, rl, pY, dcL, acL)
                pCb = block(cbb, rc, pCb, dcC, acC)
                pCr = block(crb, rc, pCr, dcC, acC)
                bx += 8
            }
            y0 += 8
        }
        bits.finish()
        out.write(0xFF); out.write(0xD9)
        out.flush()
    }
}
