package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.dronedirector.veyrocutpro.camera.GradeIO
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Library → Print layout (PDF at exact print sizes) and Collage (one picture from several photos).
 * Own code on Android's PdfDocument and Canvas.
 */
object PrintLayout {
    class Paper(val label: String, val wMm: Float, val hMm: Float, val photoPaper: Boolean = false)
    val PAPERS = listOf(
        Paper("A4", 210f, 297f), Paper("A3", 297f, 420f), Paper("Letter", 215.9f, 279.4f),
        Paper("10×15 cm", 102f, 152f, true), Paper("13×18 cm", 127f, 178f, true),
    )
    /** Layouts: photos per page, or a fixed photo size in mm (as many as fit). */
    class Layout(val label: String, val perPage: Int = 0, val wMm: Float = 0f, val hMm: Float = 0f)
    val LAYOUTS = listOf(
        Layout("1 per page", 1), Layout("2 per page", 2), Layout("4 per page", 4), Layout("6 per page", 6),
        Layout("10×15 prints", wMm = 100f, hMm = 150f), Layout("13×18 prints", wMm = 130f, hMm = 180f), Layout("9×13 prints", wMm = 90f, hMm = 130f),
        Layout("Wallet 6×9", wMm = 60f, hMm = 90f),
    )
    class Options(val paper: Int = 0, val layout: Int = 0, val borderMm: Float = 5f, val fill: Boolean = true, val marks: Boolean = true)

    private const val PT = 72f / 25.4f   // points per mm

    /** Cells (in mm, on the page) for one page. */
    private fun cells(p: Paper, l: Layout, borderMm: Float): List<RectF> {
        val margin = if (p.photoPaper) 0f else 8f
        val aw = p.wMm - 2 * margin; val ah = p.hMm - 2 * margin
        val out = ArrayList<RectF>()
        if (l.perPage > 0) {
            // Pick rows × cols closest to square cells.
            var best = 1 to l.perPage; var bestScore = Float.MAX_VALUE
            for (r in 1..l.perPage) if (l.perPage % r == 0) {
                val c = l.perPage / r
                val cw = aw / c; val ch = ah / r
                val score = max(cw / ch, ch / cw)
                if (score < bestScore) { bestScore = score; best = r to c }
            }
            val (r, c) = best
            val cw = aw / c; val ch = ah / r
            for (y in 0 until r) for (x in 0 until c) out += RectF(margin + x * cw + borderMm, margin + y * ch + borderMm, margin + (x + 1) * cw - borderMm, margin + (y + 1) * ch - borderMm)
        } else {
            // Fixed print size: try both orientations, keep the one that fits more.
            fun fitIn(w: Float, h: Float): Pair<Int, Int> = ((aw + 0.01f) / (w + borderMm)).toInt() to ((ah + 0.01f) / (h + borderMm)).toInt()
            val a = fitIn(l.wMm, l.hMm); val b = fitIn(l.hMm, l.wMm)
            val (w, h, cr) = if (a.first * a.second >= b.first * b.second) Triple(l.wMm, l.hMm, a) else Triple(l.hMm, l.wMm, b)
            val (c, r) = cr
            if (c < 1 || r < 1) return listOf(RectF(margin, margin, margin + aw, margin + ah))
            val gx = (aw - c * w) / (c + 1); val gy = (ah - r * h) / (r + 1)
            for (y in 0 until r) for (x in 0 until c) {
                val l0 = margin + gx + x * (w + gx); val t0 = margin + gy + y * (h + gy)
                out += RectF(l0, t0, l0 + w, t0 + h)
            }
        }
        return out
    }

    fun write(c: Context, uris: List<Uri>, out: Uri, o: Options, progress: (Int, Int) -> Unit): Int {
        val p = PAPERS[o.paper.coerceIn(0, PAPERS.lastIndex)]; val l = LAYOUTS[o.layout.coerceIn(0, LAYOUTS.lastIndex)]
        val cs = cells(p, l, if (l.perPage > 0) o.borderMm / 2 else 0f)
        val pages = ceil(uris.size / cs.size.toFloat()).toInt()
        val doc = PdfDocument()
        val pw = (p.wMm * PT).toInt(); val ph = (p.hMm * PT).toInt()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; strokeWidth = 0.4f }
        try {
            for (pg in 0 until pages) {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pg + 1).create())
                val cv = page.canvas
                for ((k, cell) in cs.withIndex()) {
                    val i = pg * cs.size + k
                    if (i >= uris.size) break
                    progress(i + 1, uris.size)
                    val r = RectF(cell.left * PT, cell.top * PT, cell.right * PT, cell.bottom * PT)
                    // Inner border for fixed print sizes (white frame inside the print).
                    if (l.perPage == 0 && o.borderMm > 0f) r.inset(o.borderMm * PT, o.borderMm * PT)
                    // About 300 dpi for the cell.
                    val needPx = (max(r.width(), r.height()) / 72f * 300f).toInt().coerceIn(600, 3600)
                    var b = GradeIO.decode(c, uris[i], needPx) ?: continue
                    // Turn the photo to match the cell's orientation.
                    if ((b.width > b.height) != (r.width() > r.height()) && b.width != b.height) {
                        val m = android.graphics.Matrix().apply { postRotate(90f) }
                        val rb = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true); b.recycle(); b = rb
                    }
                    drawInto(cv, b, r, o.fill, paint)
                    b.recycle()
                    if (o.marks) {
                        val cell2 = RectF(cell.left * PT, cell.top * PT, cell.right * PT, cell.bottom * PT)
                        val len = 4f * PT; val gap = 1f * PT
                        for (x in floatArrayOf(cell2.left, cell2.right)) for (y in floatArrayOf(cell2.top, cell2.bottom)) {
                            val sx = if (x == cell2.left) -1 else 1; val sy = if (y == cell2.top) -1 else 1
                            cv.drawLine(x + sx * gap, y, x + sx * (gap + len), y, mark)
                            cv.drawLine(x, y + sy * gap, x, y + sy * (gap + len), mark)
                        }
                    }
                }
                doc.finishPage(page)
            }
            c.contentResolver.openOutputStream(out, "wt")?.use { doc.writeTo(it) } ?: throw IllegalStateException("Couldn't save the PDF")
        } finally { doc.close() }
        return pages
    }

    internal fun drawInto(cv: Canvas, b: Bitmap, r: RectF, fill: Boolean, paint: Paint) {
        val s = if (fill) max(r.width() / b.width, r.height() / b.height) else min(r.width() / b.width, r.height() / b.height)
        val w = r.width() / s; val h = r.height() / s
        if (fill) {
            val src = Rect(((b.width - w) / 2).toInt().coerceAtLeast(0), ((b.height - h) / 2).toInt().coerceAtLeast(0), ((b.width + w) / 2).toInt().coerceAtMost(b.width), ((b.height + h) / 2).toInt().coerceAtMost(b.height))
            cv.drawBitmap(b, src, r, paint)
        } else {
            val dw = b.width * s; val dh = b.height * s
            cv.drawBitmap(b, null, RectF(r.centerX() - dw / 2, r.centerY() - dh / 2, r.centerX() + dw / 2, r.centerY() + dh / 2), paint)
        }
    }
}

object Collage {
    val TEMPLATES = listOf("Grid", "Big + small", "Rows", "Columns", "Mosaic")
    val ASPECTS = listOf("1:1" to 1f, "4:5" to 0.8f, "9:16" to 0.5625f, "16:9" to 1.7778f, "3:2" to 1.5f)
    val BACKGROUNDS = listOf(Color.WHITE, Color.BLACK, Color.rgb(242, 236, 224), Color.rgb(28, 28, 30))

    class Options(val template: Int = 0, val aspect: Int = 0, val spacing: Float = 0.02f, val corners: Float = 0f, val bg: Int = 0)

    /** Cells as fractions (left, top, right, bottom) of the canvas. */
    fun cells(n: Int, template: Int, aspect: Float): List<RectF> {
        val out = ArrayList<RectF>()
        if (n <= 0) return out
        fun grid(rows: Int, cols: Int, count: Int, area: RectF = RectF(0f, 0f, 1f, 1f)) {
            var k = 0
            for (r in 0 until rows) {
                val inRow = min(cols, count - r * cols)
                if (inRow <= 0) break
                for (c in 0 until inRow) {
                    val w = area.width() / inRow; val h = area.height() / rows
                    out += RectF(area.left + c * w, area.top + r * h, area.left + (c + 1) * w, area.top + (r + 1) * h); k++
                }
            }
        }
        when (template) {
            1 -> if (n == 1) out += RectF(0f, 0f, 1f, 1f) else if (aspect >= 1f) {
                out += RectF(0f, 0f, 0.62f, 1f); grid(n - 1, 1, n - 1, RectF(0.62f, 0f, 1f, 1f))
            } else {
                out += RectF(0f, 0f, 1f, 0.62f); grid(1, n - 1, n - 1, RectF(0f, 0.62f, 1f, 1f))
            }
            2 -> grid(n, 1, n)
            3 -> grid(1, n, n)
            4 -> {
                // Alternating rows of 2 and 3.
                val rows = ArrayList<Int>(); var left = n; var two = true
                while (left > 0) { val k = min(if (two) 2 else 3, left); rows += k; left -= k; two = !two }
                val h = 1f / rows.size
                rows.forEachIndexed { ri, k -> for (c in 0 until k) out += RectF(c / k.toFloat(), ri * h, (c + 1) / k.toFloat(), (ri + 1) * h) }
            }
            else -> {
                val cols = ceil(sqrt(n * aspect)).toInt().coerceIn(1, n); val rows = ceil(n / cols.toFloat()).toInt()
                grid(rows, cols, n)
            }
        }
        return out
    }

    fun render(c: Context, uris: List<Uri>, o: Options, longSide: Int, cache: MutableMap<Uri, Bitmap>? = null): Bitmap {
        val aspect = ASPECTS[o.aspect.coerceIn(0, ASPECTS.lastIndex)].second
        val w = if (aspect >= 1f) longSide else (longSide * aspect).toInt(); val h = if (aspect >= 1f) (longSide / aspect).toInt() else longSide
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        cv.drawColor(BACKGROUNDS[o.bg.coerceIn(0, BACKGROUNDS.lastIndex)])
        val gap = o.spacing * min(w, h)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val cs = cells(uris.size, o.template, aspect)
        for ((i, f) in cs.withIndex()) {
            val r = RectF(f.left * w + gap, f.top * h + gap, f.right * w - gap, f.bottom * h - gap)
            // Half gaps between cells, full gaps at the edges.
            if (f.left > 0f) r.left -= gap / 2; if (f.right < 1f) r.right += gap / 2
            if (f.top > 0f) r.top -= gap / 2; if (f.bottom < 1f) r.bottom += gap / 2
            val need = (max(r.width(), r.height()) * 1.1f).toInt().coerceAtLeast(64)
            val b = cache?.get(uris[i])?.takeIf { max(it.width, it.height) >= need * 0.8f } ?: GradeIO.decode(c, uris[i], need)?.also { cache?.put(uris[i], it) } ?: continue
            cv.save()
            if (o.corners > 0f) { val rad = o.corners * min(w, h); cv.clipPath(Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) }) }
            PrintLayout.drawInto(cv, b, r, true, paint)
            cv.restore()
            if (cache == null) b.recycle()
        }
        return out
    }
}
