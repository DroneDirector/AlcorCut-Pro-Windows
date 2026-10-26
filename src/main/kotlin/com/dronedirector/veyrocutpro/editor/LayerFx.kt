package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import kotlin.math.sqrt

/** One keyframe of a layer: where it is, how big and how visible at timeline time [t] (ms). */
data class LayerKey(val t: Long, val x: Float, val y: Float, val size: Float, val opacity: Float)

/** How a picture layer mixes with the video under it. */
enum class LayerBlend(val label: String) {
    NORMAL("Normal"), SCREEN("Screen"), MULTIPLY("Multiply"), OVERLAY("Overlay"), ADD("Add"),
    LIGHTEN("Lighten"), DARKEN("Darken"), SOFT_LIGHT("Soft light"), DIFFERENCE("Difference");

    val compose: androidx.compose.ui.graphics.BlendMode get() = when (this) {
        NORMAL -> androidx.compose.ui.graphics.BlendMode.SrcOver
        SCREEN -> androidx.compose.ui.graphics.BlendMode.Screen
        MULTIPLY -> androidx.compose.ui.graphics.BlendMode.Multiply
        OVERLAY -> androidx.compose.ui.graphics.BlendMode.Overlay
        ADD -> androidx.compose.ui.graphics.BlendMode.Plus
        LIGHTEN -> androidx.compose.ui.graphics.BlendMode.Lighten
        DARKEN -> androidx.compose.ui.graphics.BlendMode.Darken
        SOFT_LIGHT -> androidx.compose.ui.graphics.BlendMode.Softlight
        DIFFERENCE -> androidx.compose.ui.graphics.BlendMode.Difference
    }
}

/** Makes [key]-coloured pixels transparent (CPU, for picture layers). */
fun chromaKeyBitmap(src: Bitmap, key: Int, tol: Float, soft: Float): Bitmap {
    val w = src.width; val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    val (kcb, kcr) = cbcr(((key shr 16) and 0xFF) / 255f, ((key shr 8) and 0xFF) / 255f, (key and 0xFF) / 255f)
    val t0 = 0.04f + tol * 0.3f; val t1 = t0 + 0.02f + soft * 0.2f
    for (i in px.indices) {
        val c = px[i]
        val r = ((c shr 16) and 0xFF) / 255f; val g = ((c shr 8) and 0xFF) / 255f; val b = (c and 0xFF) / 255f
        val (cb, cr) = cbcr(r, g, b)
        val d = sqrt((cb - kcb) * (cb - kcb) + (cr - kcr) * (cr - kcr))
        val a = ((d - t0) / (t1 - t0)).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        val oa = ((c ushr 24) and 0xFF) / 255f * a
        px[i] = ((oa * 255f).toInt() shl 24) or (c and 0x00FFFFFF)
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

/** Makes the dark (mode 1) or bright (mode 2) parts of a picture transparent. */
fun lumaKeyBitmap(src: Bitmap, mode: Int, level: Float, soft: Float): Bitmap {
    val w = src.width; val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    for (i in px.indices) {
        val c = px[i]
        val y = (0.2126f * ((c shr 16) and 0xFF) + 0.7152f * ((c shr 8) and 0xFF) + 0.0722f * (c and 0xFF)) / 255f
        val oa = ((c ushr 24) and 0xFF) / 255f * lumaAlpha(y, mode, level, soft)
        px[i] = ((oa * 255f).toInt() shl 24) or (c and 0x00FFFFFF)
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

/** Default luma-key level: cut below 15 % (dark) or above 85 % (bright). */
fun Layer.lumaLevelOrDefault(): Float = lumaLevel ?: if (lumaKey == 2) 0.85f else 0.15f

/** Luma key alpha: [mode] 1 removes dark parts, 2 removes bright parts, with a soft edge. */
fun lumaAlpha(y: Float, mode: Int, level: Float, soft: Float): Float {
    val e = 0.01f + soft * 0.35f
    val t = when (mode) {
        1 -> ((y - level) / e).coerceIn(0f, 1f)
        2 -> ((level - y) / e).coerceIn(0f, 1f)
        else -> return 1f
    }
    return t * t * (3f - 2f * t)
}

private fun cbcr(r: Float, g: Float, b: Float): Pair<Float, Float> =
    (-0.1687f * r - 0.3313f * g + 0.5f * b) to (0.5f * r - 0.4187f * g - 0.0813f * b)
