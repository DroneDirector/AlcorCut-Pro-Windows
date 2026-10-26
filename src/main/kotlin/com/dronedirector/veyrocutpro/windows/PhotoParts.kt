package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import com.dronedirector.veyrocutpro.camera.Storage
import com.dronedirector.veyrocutpro.camera.StorageArea
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Windows versions of pieces whose phone files also hold phone-only parts (video tracking, the
 * phone's GPU preview). Same behaviour for photos.
 */

/** A tracked corner set at time [t] (video). */
data class PinQuad(val t: Long, val p: List<Float>)

/** Shape (pen) masks: a smooth closed curve through the points you place, with a soft edge. */
object PenMask {
    fun pointsAt(m: PhotoMask, srcMs: Long): FloatArray = m.pts?.toFloatArray() ?: FloatArray(0)

    /** Smooth closed curve through [p] (Catmull–Rom as cubic Béziers), scaled to w×h. */
    fun path(p: FloatArray, w: Float, h: Float): Path {
        val n = p.size / 2
        val path = Path()
        if (n < 2) return path
        fun x(i: Int) = p[2 * ((i % n + n) % n)] * w
        fun y(i: Int) = p[2 * ((i % n + n) % n) + 1] * h
        path.moveTo(x(0), y(0))
        for (i in 0 until n) {
            val c1x = x(i) + (x(i + 1) - x(i - 1)) / 6f; val c1y = y(i) + (y(i + 1) - y(i - 1)) / 6f
            val c2x = x(i + 1) - (x(i + 2) - x(i)) / 6f; val c2y = y(i + 1) - (y(i + 2) - y(i)) / 6f
            path.cubicTo(c1x, c1y, c2x, c2y, x(i + 1), y(i + 1))
        }
        path.close()
        return path
    }

    /** The mask as a grey picture (white inside), with a soft edge from [PhotoMask.feather]. */
    fun raster(m: PhotoMask, srcMs: Long, w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFF000000.toInt())
        val p = pointsAt(m, srcMs)
        if (p.size >= 6) {
            val r = m.feather.coerceIn(0f, 1f) * 0.06f * minOf(w, h)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt(); style = Paint.Style.FILL
                if (r >= 1f) maskFilter = BlurMaskFilter(r, BlurMaskFilter.Blur.NORMAL)
            }
            c.drawPath(path(p, w.toFloat(), h.toFloat()), paint)
        }
        return b
    }

    /** A first shape: a soft-cornered diamond in the middle of the picture. */
    fun starter(): List<Float> = listOf(0.5f, 0.28f, 0.68f, 0.5f, 0.5f, 0.72f, 0.32f, 0.5f)

    fun boundsQuad(p: FloatArray): FloatArray {
        var x0 = 1f; var y0 = 1f; var x1 = 0f; var y1 = 0f
        for (i in 0 until p.size / 2) { x0 = minOf(x0, p[2 * i]); x1 = maxOf(x1, p[2 * i]); y0 = minOf(y0, p[2 * i + 1]); y1 = maxOf(y1, p[2 * i + 1]) }
        return floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1)
    }
}

/** Depth AI (comes with the AI models on Windows). */
object DepthModel {
    fun infer(context: Context, bmp: Bitmap, longSide: Int = 518): Triple<FloatArray, Int, Int>? = null
}
