package androidx.camera.core

/** What the Capture screen knows of a camera; on Windows filled from the PC camera. */
open class CameraInfo
object CameraSelector { const val LENS_FACING_UNKNOWN = -1; const val LENS_FACING_FRONT = 0; const val LENS_FACING_BACK = 1; const val LENS_FACING_EXTERNAL = 2 }

/** A camera picture handed to an analyser (on Windows: the brightness plane of the PC camera's picture). */
class ImageProxy(val width: Int, val height: Int, val planes: Array<PlaneProxy>, val imageInfo: ImageInfo = ImageInfo(0)) : AutoCloseable {
    class PlaneProxy(val buffer: java.nio.ByteBuffer, val rowStride: Int, val pixelStride: Int)
    class ImageInfo(val rotationDegrees: Int)
    val format: Int get() = 35
    override fun close() {}
}
object ImageAnalysis {
    fun interface Analyzer { fun analyze(image: ImageProxy) }
    const val STRATEGY_KEEP_ONLY_LATEST = 0
}
