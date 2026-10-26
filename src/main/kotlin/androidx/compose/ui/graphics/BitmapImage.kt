package androidx.compose.ui.graphics

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Shows an app picture on screen (copied into a screen image). */
fun android.graphics.Bitmap.asImageBitmap(): ImageBitmap {
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
