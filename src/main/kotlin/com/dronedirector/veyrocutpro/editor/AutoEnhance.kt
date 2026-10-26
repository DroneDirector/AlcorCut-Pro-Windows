package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.dronedirector.veyrocutpro.camera.GradeIO
import kotlin.math.ln

/**
 * One-tap auto-enhance: looks at the photo's brightness, contrast and colour cast and picks gentle
 * corrections (exposure, whites / blacks, highlights / shadows, contrast, white balance, vibrance).
 */
object AutoEnhance {
    fun gradeFor(bmp: Bitmap): ProGrade {
        val s = Bitmap.createScaledBitmap(bmp, 256, (256f * bmp.height / bmp.width).toInt().coerceAtLeast(1), true)
        val px = IntArray(s.width * s.height); s.getPixels(px, 0, s.width, 0, 0, s.width, s.height)
        if (s !== bmp) s.recycle()
        val hist = IntArray(256)
        var sr = 0.0; var sg = 0.0; var sb = 0.0; var mid = 0
        for (c in px) {
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            val l = (r * 54 + g * 183 + b * 19) shr 8
            hist[l]++
            // White balance from the midtones only (skies and shadows lie about the light).
            if (l in 50..205) { sr += r; sg += g; sb += b; mid++ }
        }
        fun pct(p: Float): Float {
            val target = (px.size * p).toInt(); var acc = 0
            for (i in 0..255) { acc += hist[i]; if (acc >= target) return i / 255f }
            return 1f
        }
        val p1 = pct(0.01f); val p10 = pct(0.10f); val p50 = pct(0.5f); val p90 = pct(0.9f); val p99 = pct(0.99f)
        val clipHi = hist.sliceArray(248..255).sum().toFloat() / px.size
        val darkShare = hist.sliceArray(0..25).sum().toFloat() / px.size
        val exposure = (ln(0.45f / p50.coerceAtLeast(0.02f)) / ln(2f) * 0.6f).coerceIn(-0.8f, 0.8f)
        val whites = if (p99 < 0.92f) ((0.96f - p99) * 1.5f).coerceIn(0f, 0.45f) else 0f
        val blacks = if (p1 > 0.05f) (-(p1 - 0.02f) * 2f).coerceIn(-0.45f, 0f) else 0f
        val highlights = if (clipHi > 0.02f) -0.35f else if (p90 > 0.85f) -0.15f else 0f
        val shadows = if (darkShare > 0.15f) 0.35f else if (p10 < 0.12f) 0.15f else 0f
        val contrast = if (p90 - p10 < 0.5f) (0.5f - (p90 - p10)).coerceIn(0f, 0.3f) else 0f
        var temperature = 0f; var tint = 0f
        if (mid > 100) {
            val r = sr / mid; val g = sg / mid; val b = sb / mid; val avg = (r + g + b) / 3
            // Half-strength correction, so sunsets and candlelight keep their mood.
            temperature = ((b - r) / avg).toFloat().coerceIn(-0.5f, 0.5f)
            tint = (((g - (r + b) / 2) / avg) * 2.5).toFloat().coerceIn(-0.4f, 0.4f)
        }
        return ProGrade(
            exposure = exposure, contrast = contrast, whites = whites, blacks = blacks,
            highlights = highlights, shadows = shadows, temperature = temperature, tint = tint, vibrance = 0.15f,
        )
    }

    /** Saves an enhanced copy of the photo at [uri] next to it. Call off the main thread. */
    fun saveCopy(context: Context, uri: Uri): Uri? = try {
        val src = GradeIO.decode(context, uri, 0)
        if (src == null) null else {
            val g = gradeFor(src)
            val out = ImageOps.render(src, g, Detail(), Geometry(), if (g.isIdentity) null else g.tables())
            savePicture(context, out, png = false, quality = 95, area = com.dronedirector.veyrocutpro.camera.StorageArea.CAPTURE)
        }
    } catch (_: Throwable) { null }
}
