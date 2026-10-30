package androidx.compose.ui.graphics

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder

private class Shown(val gen: Int, val w: Int, val h: Int, val img: ImageBitmap)
private val shown = java.util.WeakHashMap<android.graphics.Bitmap, Shown>()

/**
 * Shows an app picture on screen. The screen copy is kept with the picture and only made again
 * when its pixels change, so redrawing the screen (sliders, zoom, animations) stays fast.
 */
fun android.graphics.Bitmap.asImageBitmap(): ImageBitmap {
    synchronized(shown) { shown[this]?.let { s -> if (s.gen == gen && s.w == width && s.h == height) return s.img } }
    val g = gen
    val img = makeScreenImage()
    synchronized(shown) { shown[this] = Shown(g, width, height, img) }
    return img
}

private fun android.graphics.Bitmap.makeScreenImage(): ImageBitmap {
    val px = IntArray(width * height)
    getPixels(px, 0, width, 0, 0, width, height)
    val buf = ByteBuffer.allocate(px.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    buf.asIntBuffer().put(px)
    val info = ImageInfo(width, height, ColorType.BGRA_8888, if (hasAlpha()) ColorAlphaType.UNPREMUL else ColorAlphaType.OPAQUE)
    return Image.makeRaster(info, buf.array(), width * 4).toComposeImageBitmap()
}

/** A screen image back to an app picture. */
fun ImageBitmap.asAndroidBitmap(): android.graphics.Bitmap {
    val px = IntArray(width * height)
    readPixels(px)
    return android.graphics.Bitmap.createBitmap(px, width, height, android.graphics.Bitmap.Config.ARGB_8888)
}
