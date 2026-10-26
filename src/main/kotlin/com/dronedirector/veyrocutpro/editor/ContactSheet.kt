package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import com.dronedirector.veyrocutpro.camera.GradeIO

/**
 * Contact sheet (Library → select photos → Contact sheet PDF): the photos as a grid on printable
 * pages with file name, stars and flag under each. Own code on Android's PdfDocument.
 */
object ContactSheet {
    class Options(val cols: Int = 4, val letter: Boolean = false, val captions: Boolean = true, val title: String = "Contact sheet")

    private fun name(c: Context, u: Uri): String = try {
        c.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Throwable) { null } ?: (u.lastPathSegment ?: "photo")

    private fun thumb(c: Context, u: Uri, px: Int): Bitmap? = try {
        if (RawDng.isRaw(c, u)) RawDng.embedded(c, u, px)?.bitmap ?: GradeIO.decode(c, u, px) else GradeIO.decode(c, u, px)
    } catch (_: Throwable) { null }

    /** Writes the PDF to [out]; returns the number of pages, or throws. */
    fun write(c: Context, uris: List<Uri>, out: Uri, o: Options, progress: (Int, Int) -> Unit): Int {
        val pw = if (o.letter) 612 else 595; val ph = if (o.letter) 792 else 842
        val margin = 30f; val head = 34f; val gap = 8f
        val cols = o.cols.coerceIn(2, 8)
        val cellW = (pw - 2 * margin - (cols - 1) * gap) / cols
        val capH = if (o.captions) 22f else 0f
        val cellH = cellW + capH
        val rows = ((ph - 2 * margin - head + gap) / (cellH + gap)).toInt().coerceAtLeast(1)
        val per = rows * cols
        val pages = (uris.size + per - 1) / per
        val doc = PdfDocument()
        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val smallP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 7f; color = Color.DKGRAY }
        val starP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 7f; color = Color.rgb(200, 140, 0) }
        val frame = Paint().apply { color = Color.rgb(235, 235, 235) }
        val bmpP = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        // Thumbnails at about 200 dpi for the cell size.
        val px = (cellW / 72f * 200f).toInt().coerceIn(240, 900)
        val date = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        try {
            for (pg in 0 until pages) {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pg + 1).create())
                val cv = page.canvas
                cv.drawText(o.title, margin, margin + 12f, titleP)
                val right = "$date · page ${pg + 1} / $pages · ${uris.size} photos"
                cv.drawText(right, pw - margin - smallP.measureText(right), margin + 12f, smallP)
                for (k in 0 until per) {
                    val i = pg * per + k
                    if (i >= uris.size) break
                    progress(i + 1, uris.size)
                    val u = uris[i]
                    val x = margin + (k % cols) * (cellW + gap); val y = margin + head + (k / cols) * (cellH + gap)
                    cv.drawRect(x, y, x + cellW, y + cellW, frame)
                    thumb(c, u, px)?.let { b ->
                        val f = minOf(cellW / b.width, cellW / b.height)
                        val w = b.width * f; val h = b.height * f
                        cv.drawBitmap(b, null, RectF(x + (cellW - w) / 2, y + (cellW - h) / 2, x + (cellW + w) / 2, y + (cellW + h) / 2), bmpP)
                        b.recycle()
                    }
                    if (o.captions) {
                        var n = name(c, u)
                        while (n.length > 4 && smallP.measureText(n) > cellW) n = n.dropLast(2)
                        cv.drawText(n, x, y + cellW + 9f, smallP)
                        val m = LibraryStore.get(u.toString())
                        val stars = "★".repeat(m.rating.coerceIn(0, 5)) + when (m.flag) { 1 -> "  ⚑ pick"; -1 -> "  ✕ reject"; else -> "" }
                        if (stars.isNotEmpty()) cv.drawText(stars, x, y + cellW + 18f, starP)
                    }
                }
                doc.finishPage(page)
            }
            c.contentResolver.openOutputStream(out, "wt")?.use { doc.writeTo(it) } ?: throw IllegalStateException("Couldn't save the PDF")
        } finally { doc.close() }
        return pages
    }
}
