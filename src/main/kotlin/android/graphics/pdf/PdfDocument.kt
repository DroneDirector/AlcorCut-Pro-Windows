package android.graphics.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * PDF pages drawn with a Canvas. Each page is drawn at print resolution (300 dpi) and stored in the
 * PDF as a high-quality JPEG. Own code (minimal PDF writer).
 */
class PdfDocument {
    class PageInfo private constructor(val pageWidth: Int, val pageHeight: Int, val pageNumber: Int) {
        class Builder(private val w: Int, private val h: Int, private val n: Int) { fun create() = PageInfo(w, h, n) }
    }

    class Page internal constructor(val info: PageInfo, internal val bmp: Bitmap) {
        val canvas: Canvas = Canvas(bmp).also { val k = SCALE; it.scale(k, k) }
    }

    private val pages = ArrayList<Pair<PageInfo, ByteArray>>()

    fun startPage(info: PageInfo): Page {
        val b = Bitmap.createBitmap((info.pageWidth * SCALE).toInt().coerceAtLeast(1), (info.pageHeight * SCALE).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        b.eraseColor(0xFFFFFFFF.toInt())
        return Page(info, b)
    }

    fun finishPage(p: Page) {
        val out = ByteArrayOutputStream()
        p.bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        pages += p.info to out.toByteArray()
        p.bmp.recycle()
    }

    fun writeTo(out: OutputStream) {
        val body = ByteArrayOutputStream()
        val offsets = ArrayList<Int>()
        fun obj(s: String) { offsets += body.size(); body.write(s.toByteArray(Charsets.ISO_8859_1)) }
        body.write("%PDF-1.4\n%âãÏÓ\n".toByteArray(Charsets.ISO_8859_1))
        val n = pages.size
        // 1: catalog, 2: pages, then per page: page, content, image.
        obj("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n")
        obj("2 0 obj << /Type /Pages /Count $n /Kids [" + (0 until n).joinToString(" ") { "${3 + it * 3} 0 R" } + "] >> endobj\n")
        pages.forEachIndexed { i, (info, jpg) ->
            val pg = 3 + i * 3; val cs = pg + 1; val im = pg + 2
            val w = info.pageWidth; val h = info.pageHeight
            obj("$pg 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 $w $h] /Resources << /XObject << /I$i $im 0 R >> >> /Contents $cs 0 R >> endobj\n")
            val draw = "q $w 0 0 $h 0 0 cm /I$i Do Q"
            obj("$cs 0 obj << /Length ${draw.length} >> stream\n$draw\nendstream endobj\n")
            val px = jpgSize(jpg)
            offsets += body.size()
            body.write("$im 0 obj << /Type /XObject /Subtype /Image /Width ${px.first} /Height ${px.second} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpg.size} >> stream\n".toByteArray(Charsets.ISO_8859_1))
            body.write(jpg)
            body.write("\nendstream endobj\n".toByteArray(Charsets.ISO_8859_1))
        }
        val xref = body.size()
        val sb = StringBuilder("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        for (o in offsets) sb.append(String.format("%010d 00000 n \n", o))
        sb.append("trailer << /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        body.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        out.write(body.toByteArray())
    }

    fun close() { pages.clear() }

    private fun jpgSize(b: ByteArray): Pair<Int, Int> {
        var i = 2
        while (i + 9 < b.size) {
            val m = b[i + 1].toInt() and 0xFF
            val len = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
            if (m in 0xC0..0xC3) return (((b[i + 7].toInt() and 0xFF) shl 8) or (b[i + 8].toInt() and 0xFF)) to (((b[i + 5].toInt() and 0xFF) shl 8) or (b[i + 6].toInt() and 0xFF))
            i += 2 + len
        }
        return 1 to 1
    }

    companion object { private const val SCALE = 300f / 72f }
}
