package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Shape layers: what they look like. */
enum class ShapeKind(val label: String) { RECT("Rectangle"), ROUNDED("Rounded"), ELLIPSE("Ellipse"), LINE("Line"), ARROW("Arrow"), TRIANGLE("Triangle"), STAR("Star"), HEART("Heart") }

object ShapeRender {
    /** Draws shape layer [l] [h] px tall. */
    fun render(l: Layer, h0: Float, frameW: Int): Bitmap {
        val kind = ShapeKind.entries.getOrElse(l.shape ?: 0) { ShapeKind.RECT }
        val aspect = (l.shapeAspect ?: if (kind == ShapeKind.LINE || kind == ShapeKind.ARROW) 4f else if (kind == ShapeKind.RECT || kind == ShapeKind.ROUNDED) 1.6f else 1f).coerceIn(0.1f, 12f)
        val h = h0.coerceAtLeast(4f)
        val w = (h * aspect).coerceIn(4f, frameW * 2f)
        val stroke = (l.stroke ?: 0f).coerceIn(0f, 0.5f)
        val sw = if (stroke > 0f || kind == ShapeKind.LINE) max(2f, (if (stroke > 0f) stroke else 0.08f) * min(w, h)) else 0f
        val pad = sw / 2f + (if (l.shadow) h * 0.06f else 1f)
        val bmp = Bitmap.createBitmap((w + pad * 2).toInt().coerceAtLeast(4), (h + pad * 2).toInt().coerceAtLeast(4), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = l.color
            style = if (sw > 0f) Paint.Style.STROKE else Paint.Style.FILL
            strokeWidth = sw; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            if (l.shadow) setShadowLayer(h * 0.05f, 0f, h * 0.025f, 0x99000000.toInt())
        }
        val r = RectF(pad, pad, pad + w, pad + h)
        when (kind) {
            ShapeKind.RECT -> c.drawRect(r, p)
            ShapeKind.ROUNDED -> c.drawRoundRect(r, min(w, h) * 0.22f, min(w, h) * 0.22f, p)
            ShapeKind.ELLIPSE -> c.drawOval(r, p)
            ShapeKind.LINE -> { p.style = Paint.Style.STROKE; c.drawLine(r.left, r.centerY(), r.right, r.centerY(), p) }
            ShapeKind.ARROW -> {
                val head = min(h, w * 0.35f)
                val path = Path().apply {
                    moveTo(r.left, r.centerY() - h * 0.16f); lineTo(r.right - head, r.centerY() - h * 0.16f); lineTo(r.right - head, r.top)
                    lineTo(r.right, r.centerY()); lineTo(r.right - head, r.bottom); lineTo(r.right - head, r.centerY() + h * 0.16f)
                    lineTo(r.left, r.centerY() + h * 0.16f); close()
                }
                c.drawPath(path, p)
            }
            ShapeKind.TRIANGLE -> c.drawPath(Path().apply { moveTo(r.centerX(), r.top); lineTo(r.right, r.bottom); lineTo(r.left, r.bottom); close() }, p)
            ShapeKind.STAR -> {
                val path = Path()
                for (i in 0 until 10) {
                    val a = -Math.PI / 2 + i * Math.PI / 5
                    val rr = if (i % 2 == 0) 1f else 0.42f
                    val x = r.centerX() + (cos(a) * rr * w / 2).toFloat(); val y = r.centerY() + (sin(a) * rr * h / 2).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close(); c.drawPath(path, p)
            }
            ShapeKind.HEART -> {
                val path = Path().apply {
                    moveTo(r.centerX(), r.bottom)
                    cubicTo(r.left - w * 0.05f, r.top + h * 0.55f, r.left + w * 0.05f, r.top - h * 0.05f, r.centerX(), r.top + h * 0.28f)
                    cubicTo(r.right - w * 0.05f, r.top - h * 0.05f, r.right + w * 0.05f, r.top + h * 0.55f, r.centerX(), r.bottom)
                    close()
                }
                c.drawPath(path, p)
            }
        }
        return bmp
    }
}
