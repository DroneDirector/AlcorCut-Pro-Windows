package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri

/** Pictures for social posts from an edited photo: before / after, and a panorama split into carousel slides. Own code. */
object SocialExport {
    /**
     * Before and after in one picture: side by side for upright photos, one above the other for wide ones,
     * with small BEFORE / AFTER labels.
     */
    fun beforeAfter(before0: Bitmap, after: Bitmap): Bitmap {
        val before = if (before0.width == after.width && before0.height == after.height) before0
            else Bitmap.createScaledBitmap(before0, after.width, after.height, true)
        val wide = after.width > after.height
        val gap = (maxOf(after.width, after.height) * 0.006f).toInt().coerceAtLeast(2)
        val out = if (wide) Bitmap.createBitmap(after.width, after.height * 2 + gap, Bitmap.Config.ARGB_8888)
            else Bitmap.createBitmap(after.width * 2 + gap, after.height, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        cv.drawColor(Color.WHITE)
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        cv.drawBitmap(before, 0f, 0f, p)
        if (wide) cv.drawBitmap(after, 0f, (after.height + gap).toFloat(), p) else cv.drawBitmap(after, (after.width + gap).toFloat(), 0f, p)
        val ts = minOf(after.width, after.height) * 0.045f
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = ts; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.08f }
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
        fun label(t: String, x: Float, y: Float) {
            val w = tp.measureText(t); val pad = ts * 0.45f
            cv.drawRoundRect(x, y, x + w + pad * 2, y + ts + pad * 1.6f, pad, pad, bg)
            tp.color = Color.WHITE; cv.drawText(t, x + pad, y + ts + pad * 0.5f, tp)
        }
        val m = ts * 0.6f
        label("BEFORE", m, m)
        if (wide) label("AFTER", m, after.height + gap + m) else label("AFTER", after.width + gap + m, m)
        if (before !== before0) before.recycle()
        return out
    }

    /**
     * A wide photo cut into [n] slides of the shape [aspect] (width / height) that join up seamlessly when
     * swiped through: the picture is first trimmed to the whole strip's shape, keeping its middle.
     */
    fun carousel(src: Bitmap, n: Int, aspect: Float): List<Bitmap> {
        val want = n * aspect
        val have = src.width.toFloat() / src.height
        val (cw, ch) = if (have > want) (src.height * want).toInt() to src.height else src.width to (src.width / want).toInt()
        val x0 = (src.width - cw) / 2; val y0 = (src.height - ch) / 2
        val sw = cw / n
        // Each slide at least 1080 px wide (Instagram's size), more when the photo has it.
        val outW = maxOf(1080, sw); val outH = (outW / aspect).toInt()
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        return List(n) { i ->
            val b = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            Canvas(b).drawBitmap(src, Rect(x0 + i * sw, y0, x0 + (i + 1) * sw, y0 + ch), Rect(0, 0, outW, outH), p)
            b
        }
    }

    /** Saves the pictures as JPEGs (each named from the file-name template); how many were saved. */
    fun save(context: Context, pics: List<Bitmap>, from: Uri?): Int {
        var ok = 0
        for (b in pics) { if (savePhotoAs(context, b, 0, 95, from, true) != null) ok++; b.recycle() }
        return ok
    }
}
