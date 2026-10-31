package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Retro looks drawn over the picture: VHS tape, an old CRT TV, a camcorder with its date stamp, old film and a
 * security camera. Everything is worked out from the frame number, so the preview and the export match.
 *
 * Layer fields: fxA style, fxX the stamp's day (days since 1970), fxY its time of day in minutes.
 * Strength is the layer's opacity.
 */
object RetroFx {
    val STYLES = listOf("VHS tape", "Old TV", "Camcorder", "Old film", "Security camera")
    const val STEP_MS = 40L

    private fun rnd(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    /** The stamp's date and time at [ms] into the layer: it runs on with the video. */
    private fun stamp(l: Layer, ms: Long, pattern: String): String {
        val day = (l.fxX ?: (System.currentTimeMillis() / 86_400_000L).toFloat()).toLong()
        val min = (l.fxY ?: 720f).toLong()
        val t = day * 86_400_000L + min * 60_000L + (ms - l.startMs).coerceAtLeast(0)
        val f = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
        f.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return f.format(java.util.Date(t)).uppercase(java.util.Locale.US)
    }

    /** Fine grain / snow: a small random picture stretched over the frame without smoothing. */
    private fun noise(c: Canvas, w: Int, h: Int, seed: Int, cell: Int, density: Float, alpha: Int, mono: Boolean = true) {
        val nw = max(1, w / cell); val nh = max(1, h / cell)
        val px = IntArray(nw * nh)
        for (i in px.indices) {
            val r = rnd(seed, i)
            if (r < density) {
                val v = (rnd(seed + 7, i) * 255).toInt()
                val a = (alpha * (0.4f + 0.6f * rnd(seed + 13, i))).toInt()
                px[i] = (a shl 24) or (if (mono) (v shl 16) or (v shl 8) or v else (v shl 16) or ((255 - v) shl 8) or (v / 2))
            }
        }
        val b = Bitmap.createBitmap(px, nw, nh, Bitmap.Config.ARGB_8888)
        c.drawBitmap(b, Rect(0, 0, nw, nh), Rect(0, 0, w, h), Paint().apply { isFilterBitmap = false })
        b.recycle()
    }

    private fun scanlines(c: Canvas, w: Int, h: Int, every: Float, alpha: Int) {
        val p = Paint().apply { color = (alpha shl 24) }
        var y = 0f
        val th = max(1f, every * 0.45f)
        while (y < h) { c.drawRect(0f, y, w.toFloat(), y + th, p); y += every }
    }

    private fun vignette(c: Canvas, w: Int, h: Int, strength: Float) {
        val r = max(w, h) * 0.75f
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = RadialGradient(w / 2f, h / 2f, r, intArrayOf(0, 0, ((strength * 255).toInt().coerceIn(0, 255) shl 24)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        })
    }

    private fun mono(size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textSize = size; this.color = color
        setShadowLayer(size * 0.08f, size * 0.05f, size * 0.05f, 0xAA000000.toInt())
    }

    fun render(l: Layer, w: Int, h: Int, ms: Long): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val n = (max(0L, ms - l.startMs) / STEP_MS).toInt()
        val fw = w.toFloat(); val fh = h.toFloat()
        val s = min(fw, fh)
        when ((l.fxA ?: 0f).toInt()) {
            0 -> { // VHS tape
                c.drawColor(0x1AFF8040)                                      // warm, faded tape colour
                scanlines(c, w, h, max(2f, fh / 240f), 34)
                noise(c, w, h, n, 3, 0.10f, 70)
                // The tracking band: a bright, noisy strip rolling slowly up the picture.
                val bandY = fh - ((n * 6f) % (fh * 1.6f))
                val bandH = fh * 0.06f
                c.save(); c.clipRect(0f, bandY, fw, bandY + bandH)
                noise(c, w, h, n * 3 + 1, 2, 0.55f, 170)
                c.restore()
                // A few torn lines near the bottom (head switching).
                val p = Paint().apply { color = 0x66FFFFFF }
                for (k in 0 until 3) { val y = fh * (0.93f + 0.02f * k) + rnd(n, k) * 4f; c.drawRect(rnd(n, k + 9) * fw * 0.3f, y, fw, y + max(1f, fh / 360f), p) }
                val t = mono(s * 0.055f, 0xFFF2F2F2.toInt())
                if ((n / 12) % 2 == 0) c.drawText("PLAY ▶", fw * 0.05f, fh * 0.1f, t)
                c.drawText(stamp(l, ms, "HH:mm:ss"), fw * 0.05f, fh * 0.94f, t)
                c.drawText("SP", fw * 0.88f, fh * 0.1f, t)
            }
            1 -> { // Old TV
                scanlines(c, w, h, max(3f, fh / 200f), 70)
                // Red / green / blue stripes of the screen, very faint.
                val sp = Paint()
                val step = max(3f, fw / 420f)
                var x = 0f; var k = 0
                while (x < fw) { sp.color = when (k % 3) { 0 -> 0x10FF0000; 1 -> 0x1000FF00; else -> 0x100000FF }; c.drawRect(x, 0f, x + step / 3f, fh, sp); x += step / 3f; k++ }
                noise(c, w, h, n, 2, 0.06f, 50)
                // Flicker and a soft rolling bar.
                c.drawColor(((8 + (rnd(n, 1) * 14).toInt()) shl 24) or 0xFFFFFF)
                val barY = (n * 9f) % (fh * 1.3f) - fh * 0.15f
                c.drawRect(0f, barY, fw, barY + fh * 0.15f, Paint().apply { shader = LinearGradient(0f, barY, 0f, barY + fh * 0.15f, intArrayOf(0, 0x14FFFFFF, 0), null, Shader.TileMode.CLAMP) })
                vignette(c, w, h, 0.85f)
                // The rounded glass of the tube: black corners.
                val path = android.graphics.Path().apply {
                    addRect(0f, 0f, fw, fh, android.graphics.Path.Direction.CW)
                    addRoundRect(RectF(fw * 0.015f, fh * 0.02f, fw * 0.985f, fh * 0.98f), s * 0.09f, s * 0.09f, android.graphics.Path.Direction.CCW)
                }
                c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() })
            }
            2 -> { // Camcorder
                noise(c, w, h, n, 3, 0.05f, 40)
                c.drawColor(0x0D3060FF)
                val t = mono(s * 0.06f, 0xFFFFA42A.toInt())
                c.drawText(stamp(l, ms, "MMM d yyyy"), fw * 0.62f, fh * 0.86f, t)
                c.drawText(stamp(l, ms, "h:mm:ss a"), fw * 0.62f, fh * 0.93f, t)
                val w2 = mono(s * 0.055f, 0xFFFFFFFF.toInt())
                if ((n / 12) % 2 == 0) c.drawCircle(fw * 0.06f, fh * 0.085f, s * 0.018f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF2A2A.toInt() })
                c.drawText("REC", fw * 0.085f, fh * 0.1f, w2)
                c.drawText("SP", fw * 0.06f, fh * 0.93f, w2)
                // Battery: an outline with three cells.
                val bx = fw * 0.86f; val by = fh * 0.06f; val bw = s * 0.09f; val bh = s * 0.04f
                val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = max(2f, s * 0.005f); color = 0xFFFFFFFF.toInt() }
                c.drawRect(bx, by, bx + bw, by + bh, st)
                c.drawRect(bx + bw, by + bh * 0.3f, bx + bw + bh * 0.2f, by + bh * 0.7f, Paint().apply { color = 0xFFFFFFFF.toInt() })
                val cellP = Paint().apply { color = 0xFFFFFFFF.toInt() }
                for (k in 0 until 3) c.drawRect(bx + bw * (0.08f + 0.31f * k), by + bh * 0.2f, bx + bw * (0.31f + 0.31f * k), by + bh * 0.8f, cellP)
                // Viewfinder corners.
                val cl = s * 0.06f; val m = s * 0.04f
                for ((x, y, dx, dy) in listOf(floatArrayOf(m, m, 1f, 1f), floatArrayOf(fw - m, m, -1f, 1f), floatArrayOf(m, fh - m, 1f, -1f), floatArrayOf(fw - m, fh - m, -1f, -1f)).map { listOf(it[0], it[1], it[2], it[3]) }) {
                    c.drawLine(x, y, x + cl * dx, y, st); c.drawLine(x, y, x, y + cl * dy, st)
                }
            }
            3 -> { // Old film
                c.drawColor(0x22B08040)
                val f = 0.06f + 0.10f * rnd(n, 3)
                c.drawColor(((f * 255).toInt() shl 24) or (if (rnd(n, 4) > 0.5f) 0xFFFFFF else 0x000000))
                noise(c, w, h, n, 2, 0.08f, 60)
                // Dust and hairs, new every frame.
                val dp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC111111.toInt() }
                for (k in 0 until 7) if (rnd(n, 20 + k) > 0.45f) c.drawCircle(rnd(n, 40 + k) * fw, rnd(n, 60 + k) * fh, s * (0.002f + 0.006f * rnd(n, 80 + k)), dp)
                val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = max(1f, s * 0.002f); color = 0xAA111111.toInt() }
                if (rnd(n, 99) > 0.7f) { val x = rnd(n, 98) * fw; val y = rnd(n, 97) * fh; c.drawArc(RectF(x, y, x + s * 0.06f, y + s * 0.03f), 200f, 120f, false, hp) }
                // Scratches: long thin lines that stay a few frames.
                val sc = Paint().apply { color = 0x55F8F8F0 }
                for (k in 0 until 2) { val g = n / 6 + k * 31; if (rnd(g, 5) > 0.4f) { val x = rnd(g, 6) * fw + sin(n * 0.7f + k) * 3f; c.drawRect(x, 0f, x + max(1f, fw / 900f), fh, sc) } }
                vignette(c, w, h, 0.7f)
            }
            else -> { // Security camera
                c.drawColor(0x2A40FF60)
                scanlines(c, w, h, max(2f, fh / 260f), 40)
                noise(c, w, h, n, 3, 0.08f, 55)
                val t = mono(s * 0.05f, 0xFFF0F0F0.toInt())
                c.drawText("CAM 01", fw * 0.04f, fh * 0.09f, t)
                c.drawText(stamp(l, ms, "yyyy-MM-dd  HH:mm:ss"), fw * 0.04f, fh * 0.95f, t)
                if ((n / 12) % 2 == 0) c.drawText("● REC", fw * 0.84f, fh * 0.09f, mono(s * 0.05f, 0xFFFF3030.toInt()))
                vignette(c, w, h, 0.5f)
            }
        }
        return out
    }
}
