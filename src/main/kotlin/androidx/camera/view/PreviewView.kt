package androidx.camera.view

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toComposeImageBitmap

/**
 * The viewfinder on Windows: the camera hands it each new picture and the screen draws the latest one.
 * Own code; same name and the parts of the phone's preview view the Capture screen uses.
 */
open class PreviewView(context: android.content.Context? = null) : android.view.View() {
    enum class ImplementationMode { PERFORMANCE, COMPATIBLE }
    enum class ScaleType { FILL_START, FILL_CENTER, FILL_END, FIT_START, FIT_CENTER, FIT_END }
    var implementationMode = ImplementationMode.COMPATIBLE
    var scaleType = ScaleType.FILL_CENTER


    /** The newest picture for the screen. */
    var frame by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null); private set
    @Volatile private var last: android.graphics.Bitmap? = null

    /** Called by the camera with a new picture (ARGB). */
    fun post(px: IntArray, w: Int, h: Int) {
        val info = org.jetbrains.skia.ImageInfo.makeN32(w, h, org.jetbrains.skia.ColorAlphaType.OPAQUE)
        val bytes = java.nio.ByteBuffer.allocate(w * h * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bytes.asIntBuffer().put(px, 0, w * h)
        val img = org.jetbrains.skia.Image.makeRaster(info, bytes.array(), w * 4)
        lastPx = px; lastW = w; lastH = h; last = null
        frame = img.toComposeImageBitmap()
    }
    @Volatile private var lastPx: IntArray? = null
    @Volatile private var lastW = 0
    @Volatile private var lastH = 0

    fun clear() { frame = null; lastPx = null; last = null }

    /** The current picture at the size it's shown (as on the phone), or null before the first one. */
    val bitmap: android.graphics.Bitmap?
        get() {
            last?.let { return it }
            val px = lastPx ?: return null
            val b = android.graphics.Bitmap.createBitmap(px, lastW, lastH, android.graphics.Bitmap.Config.ARGB_8888)
            val vw = width; val vh = height
            val out = if (vw > 0 && vh > 0) {
                // FILL_CENTER: the picture covers the view, cut at the sides.
                val k = maxOf(vw.toFloat() / lastW, vh.toFloat() / lastH)
                val sw = (vw / k).toInt().coerceIn(1, lastW); val sh = (vh / k).toInt().coerceIn(1, lastH)
                val crop = android.graphics.Bitmap.createBitmap(b, (lastW - sw) / 2, (lastH - sh) / 2, sw, sh)
                android.graphics.Bitmap.createScaledBitmap(crop, vw, vh, true)
            } else b
            last = out
            return out
        }

    /** The raw camera picture (full size) and its size, for analysis. */
    fun raw(): Triple<IntArray, Int, Int>? = lastPx?.let { Triple(it, lastW, lastH) }
}
