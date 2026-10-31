package com.dronedirector.veyrocutpro.editor

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap

/** Text on the screen canvas with the app's Paint (the phone's canvas has this built in). */
internal fun org.jetbrains.skia.Canvas.drawText(text: String, x: Float, y: Float, paint: android.graphics.Paint) {
    val bold = paint.isFakeBoldText || (paint.typeface?.style ?: 0) and 1 != 0
    val tf = org.jetbrains.skia.FontMgr.default.matchFamilyStyle(null, if (bold) org.jetbrains.skia.FontStyle.BOLD else org.jetbrains.skia.FontStyle.NORMAL)
    val font = org.jetbrains.skia.Font(tf, paint.textSize)
    val p = org.jetbrains.skia.Paint().apply { color = paint.color; isAntiAlias = true }
    val w = font.measureTextWidth(text)
    val dx = when (paint.textAlign) { android.graphics.Paint.Align.CENTER -> -w / 2f; android.graphics.Paint.Align.RIGHT -> -w; else -> 0f }
    drawString(text, x + dx, y, font, p)
}

/** The phone's Matrix applied to the screen canvas. */
internal fun org.jetbrains.skia.Canvas.concat(m: android.graphics.Matrix) {
    val v = FloatArray(9); m.getValues(v)
    concat(org.jetbrains.skia.Matrix33(*v))
}

/** A phone Bitmap drawn on the screen canvas (alpha from the paint). */
internal fun org.jetbrains.skia.Canvas.drawBitmap(b: android.graphics.Bitmap, x: Float, y: Float, paint: android.graphics.Paint?) {
    val img = b.asImageBitmapForSkia().asSkiaBitmap()
    val p = org.jetbrains.skia.Paint().apply { alpha = paint?.alpha ?: 255 }
    drawImage(org.jetbrains.skia.Image.makeFromBitmap(img), x, y, p)
}

private fun android.graphics.Bitmap.asImageBitmapForSkia(): androidx.compose.ui.graphics.ImageBitmap = this.asImageBitmap()

/** A phone Path drawn on the screen canvas with the phone's Paint (stroke or fill, colour, width). */
internal fun org.jetbrains.skia.Canvas.drawPath(path: android.graphics.Path, paint: android.graphics.Paint) {
    val sp = org.jetbrains.skia.Path()
    val it = path.shape.getPathIterator(null)
    val c = FloatArray(6)
    while (!it.isDone) {
        when (it.currentSegment(c)) {
            java.awt.geom.PathIterator.SEG_MOVETO -> sp.moveTo(c[0], c[1])
            java.awt.geom.PathIterator.SEG_LINETO -> sp.lineTo(c[0], c[1])
            java.awt.geom.PathIterator.SEG_QUADTO -> sp.quadTo(c[0], c[1], c[2], c[3])
            java.awt.geom.PathIterator.SEG_CUBICTO -> sp.cubicTo(c[0], c[1], c[2], c[3], c[4], c[5])
            java.awt.geom.PathIterator.SEG_CLOSE -> sp.closePath()
        }
        it.next()
    }
    val p = org.jetbrains.skia.Paint().apply {
        color = paint.color; isAntiAlias = true
        mode = if (paint.style == android.graphics.Paint.Style.STROKE) org.jetbrains.skia.PaintMode.STROKE else org.jetbrains.skia.PaintMode.FILL
        strokeWidth = paint.strokeWidth
    }
    drawPath(sp, p)
}
