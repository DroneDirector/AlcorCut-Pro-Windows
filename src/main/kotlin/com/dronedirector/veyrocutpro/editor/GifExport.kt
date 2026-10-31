package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import com.dronedirector.veyrocutpro.camera.GradeIO
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** Minimal animated GIF writer: 256-colour palette per frame, LZW compression, loops forever. */
class GifWriter(private val out: OutputStream, private val w: Int, private val h: Int, private val delayCs: Int) {
    private var started = false

    fun add(bmp: Bitmap) {
        val px = IntArray(w * h)
        val b = if (bmp.width == w && bmp.height == h) bmp else Bitmap.createScaledBitmap(bmp, w, h, true)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val (palette, index) = quantize(px)
        if (!started) {
            out.write("GIF89a".toByteArray())
            short(w); short(h)
            out.write(0x00); out.write(0); out.write(0)   // no global palette
            // Loop forever (NETSCAPE2.0).
            out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B) + "NETSCAPE2.0".toByteArray() + byteArrayOf(3, 1, 0, 0, 0))
            started = true
        }
        // Frame delay.
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0)); short(delayCs); out.write(0); out.write(0)
        // Image with its own 256-colour palette.
        out.write(0x2C); short(0); short(0); short(w); short(h); out.write(0x87)
        for (i in 0 until 256) { val c = palette.getOrElse(i) { 0 }; out.write((c shr 16) and 0xFF); out.write((c shr 8) and 0xFF); out.write(c and 0xFF) }
        lzw(index)
    }

    fun finish() { out.write(0x3B); out.flush() }

    private fun short(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }

    /** Most-used colours (5 bits per channel), then nearest colour for every pixel. */
    private fun quantize(px: IntArray): Pair<IntArray, ByteArray> {
        val counts = IntArray(32768)
        for (c in px) counts[((c shr 9) and 0x7C00) or ((c shr 6) and 0x3E0) or ((c shr 3) and 0x1F)]++
        val top = counts.indices.filter { counts[it] > 0 }.sortedByDescending { counts[it] }.take(256)
        val palette = IntArray(top.size) { val k = top[it]; (((k shr 10) and 31) * 255 / 31 shl 16) or (((k shr 5) and 31) * 255 / 31 shl 8) or ((k and 31) * 255 / 31) }
        val map = IntArray(32768) { -1 }
        val idx = ByteArray(px.size)
        for (i in px.indices) {
            val c = px[i]
            val k = ((c shr 9) and 0x7C00) or ((c shr 6) and 0x3E0) or ((c shr 3) and 0x1F)
            var m = map[k]
            if (m < 0) {
                val r = ((k shr 10) and 31) * 255 / 31; val g = ((k shr 5) and 31) * 255 / 31; val b = (k and 31) * 255 / 31
                var best = 0; var bd = Int.MAX_VALUE
                for (j in palette.indices) {
                    val p = palette[j]
                    val dr = r - ((p shr 16) and 0xFF); val dg = g - ((p shr 8) and 0xFF); val db = b - (p and 0xFF)
                    val d = dr * dr * 2 + dg * dg * 4 + db * db
                    if (d < bd) { bd = d; best = j }
                }
                m = best; map[k] = m
            }
            idx[i] = m.toByte()
        }
        return palette to idx
    }

    /** GIF LZW, 8-bit pixels. */
    private fun lzw(data: ByteArray) {
        val minCode = 8
        out.write(minCode)
        val buf = ByteArrayOutputStream()
        var bitBuf = 0; var bitCount = 0
        fun emit(code: Int, size: Int) {
            bitBuf = bitBuf or (code shl bitCount); bitCount += size
            while (bitCount >= 8) { buf.write(bitBuf and 0xFF); bitBuf = bitBuf ushr 8; bitCount -= 8 }
        }
        val clear = 1 shl minCode; val eoi = clear + 1
        var codeSize = minCode + 1
        var next = eoi + 1
        val dict = HashMap<Int, Int>(8192)
        emit(clear, codeSize)
        var prefix = data[0].toInt() and 0xFF
        for (i in 1 until data.size) {
            val k = data[i].toInt() and 0xFF
            val key = (prefix shl 8) or k
            val found = dict[key]
            if (found != null) { prefix = found; continue }
            emit(prefix, codeSize)
            if (next < 4096) {
                dict[key] = next++
                if (next > (1 shl codeSize) && codeSize < 12) codeSize++
            } else {
                emit(clear, codeSize)
                dict.clear(); next = eoi + 1; codeSize = minCode + 1
            }
            prefix = k
        }
        emit(prefix, codeSize)
        emit(eoi, codeSize)
        if (bitCount > 0) buf.write(bitBuf and 0xFF)
        val bytes = buf.toByteArray()
        var o = 0
        while (o < bytes.size) { val n = minOf(255, bytes.size - o); out.write(n); out.write(bytes, o, n); o += n }
        out.write(0)
    }
}

/** Animated GIF of (part of) the timeline: graded frames with text / logo layers. */
object GifExport {
    fun render(context: Context, clips: List<Clip>, layers: List<Layer>, fromMs: Long, toMs: Long, width: Int, fps: Int, out: OutputStream, progress: (Int) -> Unit): Boolean {
        val starts = clipStarts(clips)
        val first = clips.firstOrNull() ?: return false
        val aspect = (first.width * (first.desqueeze ?: 1f)) / first.height.coerceAtLeast(1)
        val w = (width / 2 * 2).coerceAtLeast(16)
        val h = ((w / aspect).toInt() / 2 * 2).coerceAtLeast(16)
        val step = 1000L / fps
        val gif = GifWriter(out, w, h, (100 / fps).coerceAtLeast(2))
        var t = fromMs
        val total = (toMs - fromMs).coerceAtLeast(1)
        while (t < toMs) {
            val i = starts.indices.lastOrNull { starts[it] <= t } ?: 0
            val c = clips[i]
            val src = c.srcAtOffset(t - starts[i])
            val f = GradeIO.videoFrame(context, c.uri, src * 1000, maxOf(w, h)) ?: return false
            var img = ImageOps.render(f, c.grade, c.detail.copy(aiDenoise = 0f), Geometry(), if (c.grade.isIdentity) null else c.grade.tables())
            for (l in layers) if (l.activeAt(t) && l.kind != LayerKind.VIDEO) img = stampLayer(context, img, l)
            gif.add(img)
            progress(((t - fromMs) * 100 / total).toInt())
            t += step
        }
        gif.finish()
        return true
    }
}
