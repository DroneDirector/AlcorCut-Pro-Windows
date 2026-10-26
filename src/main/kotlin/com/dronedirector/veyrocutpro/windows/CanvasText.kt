package com.dronedirector.veyrocutpro.editor

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
