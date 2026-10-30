package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.camera.GradeIO
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.hintedScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Photo book: the picked photos laid out on pages (a cover with the title, then 1–4 photos a page),
 * saved as a print-ready PDF at about 200 dpi. Own code on Android's PdfDocument.
 */
object PhotoBook {
    /** Page sizes in points (1/72 inch). */
    val SIZES = listOf("A4 landscape" to (842 to 595), "A4 portrait" to (595 to 842), "Square 21 cm" to (595 to 595), "Letter landscape" to (792 to 612))
    val LAYOUTS = listOf("Mixed", "1 a page", "2 a page", "4 a page")

    class Options(val title: String, val subtitle: String, val size: Int, val layout: Int, val margin: Int, val dark: Boolean, val numbers: Boolean, val cover: Boolean)

    private fun load(c: Context, u: Uri, px: Int): Bitmap? = try {
        if (RawDng.isRaw(c, u)) RawDng.embedded(c, u, px)?.bitmap ?: GradeIO.decode(c, u, px) else GradeIO.decode(c, u, px)
    } catch (_: Throwable) { null }

    /** Draws [b] filling [r] (cropped to fit). */
    private fun fill(cv: Canvas, b: Bitmap, r: RectF) {
        val k = maxOf(r.width() / b.width, r.height() / b.height)
        val sw = (r.width() / k).toInt().coerceAtMost(b.width); val sh = (r.height() / k).toInt().coerceAtMost(b.height)
        val sx = (b.width - sw) / 2; val sy = (b.height - sh) / 2
        cv.drawBitmap(b, Rect(sx, sy, sx + sw, sy + sh), r, Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
    }

    /** How many photos go on each page. */
    private fun plan(n: Int, layout: Int): List<Int> {
        val out = ArrayList<Int>(); var left = n
        val cycle = listOf(1, 2, 1, 3, 4)
        var i = 0
        while (left > 0) {
            val want = when (layout) { 1 -> 1; 2 -> 2; 3 -> 4; else -> cycle[i++ % cycle.size] }
            val take = minOf(want, left); out += take; left -= take
        }
        return out
    }

    /** Cells (in points) for [n] photos on a page of the area [a]. */
    private fun cells(n: Int, a: RectF, gap: Float): List<RectF> {
        val land = a.width() >= a.height()
        return when (n) {
            1 -> listOf(a)
            2 -> if (land) listOf(RectF(a.left, a.top, a.centerX() - gap / 2, a.bottom), RectF(a.centerX() + gap / 2, a.top, a.right, a.bottom))
                else listOf(RectF(a.left, a.top, a.right, a.centerY() - gap / 2), RectF(a.left, a.centerY() + gap / 2, a.right, a.bottom))
            3 -> if (land) { val x = a.left + a.width() * 0.62f
                listOf(RectF(a.left, a.top, x - gap / 2, a.bottom), RectF(x + gap / 2, a.top, a.right, a.centerY() - gap / 2), RectF(x + gap / 2, a.centerY() + gap / 2, a.right, a.bottom)) }
                else { val y = a.top + a.height() * 0.62f
                listOf(RectF(a.left, a.top, a.right, y - gap / 2), RectF(a.left, y + gap / 2, a.centerX() - gap / 2, a.bottom), RectF(a.centerX() + gap / 2, y + gap / 2, a.right, a.bottom)) }
            else -> listOf(RectF(a.left, a.top, a.centerX() - gap / 2, a.centerY() - gap / 2), RectF(a.centerX() + gap / 2, a.top, a.right, a.centerY() - gap / 2),
                RectF(a.left, a.centerY() + gap / 2, a.centerX() - gap / 2, a.bottom), RectF(a.centerX() + gap / 2, a.centerY() + gap / 2, a.right, a.bottom))
        }
    }

    /** Writes the book to [out]; returns the number of pages. */
    fun write(c: Context, uris: List<Uri>, out: Uri, o: Options, progress: (Int, Int) -> Unit): Int {
        val (pw, ph) = SIZES[o.size.coerceIn(0, SIZES.lastIndex)].second
        val m = listOf(0f, 18f, 36f)[o.margin.coerceIn(0, 2)]
        val gap = if (m == 0f) 4f else 10f
        val bg = if (o.dark) 0xFF101010.toInt() else 0xFFFFFFFF.toInt()
        val ink = if (o.dark) 0xFFEDEDED.toInt() else 0xFF222222.toInt()
        val dpi = 200f / 72f
        val doc = PdfDocument()
        var pageNo = 0
        var done = 0
        try {
            if (o.cover && uris.isNotEmpty()) {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, ++pageNo).create())
                val cv = page.canvas
                cv.drawColor(0xFF000000.toInt())
                load(c, uris[0], (maxOf(pw, ph) * dpi).toInt())?.let { b -> fill(cv, b, RectF(0f, 0f, pw.toFloat(), ph.toFloat())); b.recycle() }
                cv.drawRect(0f, ph * 0.55f, pw.toFloat(), ph.toFloat(), Paint().apply { shader = LinearGradient(0f, ph * 0.55f, 0f, ph.toFloat(), 0, 0xCC000000.toInt(), Shader.TileMode.CLAMP) })
                val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = minOf(pw, ph) * 0.075f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
                cv.drawText(o.title.ifBlank { "Photo book" }, pw * 0.07f, ph * 0.86f, tp)
                if (o.subtitle.isNotBlank()) cv.drawText(o.subtitle, pw * 0.07f, ph * 0.86f + tp.textSize * 0.75f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xDDFFFFFF.toInt(); textSize = tp.textSize * 0.4f })
                doc.finishPage(page)
            }
            var at = 0
            for (n in plan(uris.size, o.layout)) {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, ++pageNo).create())
                val cv = page.canvas
                cv.drawColor(bg)
                val bottom = if (o.numbers) 22f else 0f
                val area = RectF(m, m, pw - m, ph - m - bottom)
                for (r in cells(n, area, gap)) {
                    val u = uris.getOrNull(at++) ?: break
                    load(c, u, (maxOf(r.width(), r.height()) * dpi).toInt().coerceAtLeast(400))?.let { b -> fill(cv, b, r); b.recycle() }
                    progress(++done, uris.size)
                }
                if (o.numbers) {
                    val np = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 9f; textAlign = Paint.Align.CENTER }
                    cv.drawText("${pageNo - (if (o.cover) 1 else 0)}", pw / 2f, ph - m.coerceAtLeast(8f) - 2f, np)
                }
                doc.finishPage(page)
            }
            c.contentResolver.openOutputStream(out, "wt")?.use { doc.writeTo(it) } ?: throw IllegalStateException("Couldn't write the file")
        } finally { doc.close() }
        com.dronedirector.veyrocutpro.ui.AppLog.i("Photo book: ${uris.size} photo(s), $pageNo page(s)")
        return pageNo
    }
}

/** The photo book settings; the PDF is saved where you choose. */
@Composable
fun PhotoBookDialog(uris: List<Uri>, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(0) }
    var layout by remember { mutableStateOf(0) }
    var margin by remember { mutableStateOf(1) }
    var dark by remember { mutableStateOf(false) }
    var numbers by remember { mutableStateOf(true) }
    var cover by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { out ->
        if (out == null) return@rememberLauncherForActivityResult
        busy = "Making the book…"
        scope.launch {
            val o = PhotoBook.Options(title, subtitle, size, layout, margin, dark, numbers, cover)
            val res = withContext(Dispatchers.IO) {
                try { "Photo book saved — ${PhotoBook.write(context, uris, out, o) { d, t -> busy = "Making the book… $d of $t" }} pages" } catch (t: Throwable) { "Couldn't make the book: ${t.message}" }
            }
            busy = null
            android.widget.Toast.makeText(context, res, android.widget.Toast.LENGTH_LONG).show()
            onDone()
        }
    }
    val colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Pro.Fg, unfocusedTextColor = Pro.Fg, focusedBorderColor = Pro.Amber, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber)
    AlertDialog(onDismissRequest = { if (busy == null) onDone() }, containerColor = Ed.Panel,
        title = { Text("Photo book", color = Pro.Fg) },
        text = {
            Column(Modifier.hintedScroll(horizontal = false)) {
                Text("${uris.size} photos laid out on pages, in this order — a print-ready PDF for a print shop or online book service.", color = Pro.Dim, fontSize = 12.sp)
                OutlinedTextField(title, { title = it }, label = { Text("Title (on the cover)") }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(subtitle, { subtitle = it }, label = { Text("Subtitle (e.g. Faroe Islands 2026)") }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth())
                Text("Page size", color = Pro.Fg, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.hintedScroll(horizontal = true), horizontalArrangement = Arrangement.spacedBy(6.dp)) { PhotoBook.SIZES.forEachIndexed { i, s -> PillChip(s.first, size == i) { size = i } } }
                Text("Photos a page", color = Pro.Fg, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.hintedScroll(horizontal = true), horizontalArrangement = Arrangement.spacedBy(6.dp)) { PhotoBook.LAYOUTS.forEachIndexed { i, s -> PillChip(s, layout == i) { layout = i } } }
                Text("Border", color = Pro.Fg, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("None (to the edge)", "Thin", "Wide").forEachIndexed { i, s -> PillChip(s, margin == i) { margin = i } } }
                Row(Modifier.hintedScroll(horizontal = true).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillChip("White pages", !dark) { dark = false }; PillChip("Black pages", dark) { dark = true }
                    PillChip("Cover", cover) { cover = !cover }; PillChip("Page numbers", numbers) { numbers = !numbers }
                }
                busy?.let { Text(it, color = Pro.Amber, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = { TextButton(enabled = busy == null, onClick = { try { save.launch((title.ifBlank { "Photo book" }) + ".pdf") } catch (_: Throwable) {} }) { Text("SAVE PDF", color = Pro.Amber) } },
        dismissButton = { TextButton(enabled = busy == null, onClick = onDone) { Text("CANCEL", color = Pro.Dim) } },
    )
}
