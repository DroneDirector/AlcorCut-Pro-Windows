package com.dronedirector.veyrocutpro.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * Privacy face blur (Settings → PRIVACY). Faces are found on the phone (ML Kit) in the small
 * analysis frames; their positions, in the camera's own (sensor) coordinates, are handed to the
 * live filter, which pixelates them in the viewfinder and in video recordings. Photos are blurred
 * after they're saved.
 */
object FaceBlur {
    const val MAX = 8

    /** Faces as (centre x, centre y, half width, half height), 0..1 of the sensor image. */
    @Volatile var regions: FloatArray = FloatArray(0)
        private set
    @Volatile private var lastUpdate = 0L
    @Volatile private var busy = false
    private var frame = 0L

    private val live: FaceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.08f)
                .build()
        )
    }
    private val still: FaceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setMinFaceSize(0.04f)
                .build()
        )
    }

    /** Faces older than this are dropped (e.g. the person left the frame). */
    fun current(): FloatArray = if (System.currentTimeMillis() - lastUpdate > 1200) FloatArray(0) else regions

    fun clear() { regions = FloatArray(0) }

    /** Called with each analysis frame (YUV); detection runs a few times a second. */
    fun submit(image: ImageProxy) {
        if (busy || (frame++ % 3L) != 0L) return
        val w = image.width; val h = image.height
        val plane = image.planes[0]
        val buf = plane.buffer
        val row = plane.rowStride; val ps = plane.pixelStride
        // NV21 with grey chroma: faces are found from brightness alone.
        val nv21 = ByteArray(w * h * 3 / 2)
        buf.rewind()
        if (ps == 1 && row == w) buf.get(nv21, 0, minOf(w * h, buf.remaining()))
        else for (y in 0 until h) for (x in 0 until w) {
            val i = y * row + x * ps
            if (i < buf.limit()) nv21[y * w + x] = buf.get(i)
        }
        java.util.Arrays.fill(nv21, w * h, nv21.size, 128.toByte())
        detect(nv21, w, h, image.imageInfo.rotationDegrees, null)
    }

    val isBusy: Boolean get() = busy

    // GPU feed: which extra quarter-turn makes the faces upright (found by trying, then kept).
    private var gpuTurn = 0
    private var gpuMisses = 0

    /**
     * Frames read straight from the camera texture by the live filter (used when the phone can't
     * run a separate analysis stream, e.g. while filming 4K). [luma] is w×h brightness in texture
     * coordinates; [rot] the sensor rotation. If faces aren't found for a while, other
     * orientations are tried, so it works whichever way the texture is stored.
     */
    fun submitLuma(luma: ByteArray, w: Int, h: Int, rot: Int) {
        if (busy) return
        val nv21 = ByteArray(w * h * 3 / 2)
        System.arraycopy(luma, 0, nv21, 0, w * h)
        java.util.Arrays.fill(nv21, w * h, nv21.size, 128.toByte())
        detect(nv21, w, h, (rot + gpuTurn * 90) % 360) { found ->
            if (found) gpuMisses = 0
            else if (++gpuMisses >= 4) { gpuMisses = 0; gpuTurn = (gpuTurn + 1) % 4 }
        }
    }

    private fun detect(nv21: ByteArray, w: Int, h: Int, rot: Int, onResult: ((Boolean) -> Unit)?) {
        busy = true
        live.process(InputImage.fromByteArray(nv21, w, h, rot, InputImage.IMAGE_FORMAT_NV21))
            .addOnSuccessListener { faces ->
                onResult?.invoke(faces.isNotEmpty())
                val out = ArrayList<Float>()
                for (f in faces.take(MAX)) {
                    val b = f.boundingBox
                    // Back from the upright picture ML Kit saw to the sensor image.
                    val pts = listOf(b.left to b.top, b.right to b.top, b.left to b.bottom, b.right to b.bottom).map { (ux, uy) ->
                        when (rot) {
                            90 -> uy.toFloat() to (h - ux).toFloat()
                            180 -> (w - ux).toFloat() to (h - uy).toFloat()
                            270 -> (w - uy).toFloat() to ux.toFloat()
                            else -> ux.toFloat() to uy.toFloat()
                        }
                    }
                    val x0 = pts.minOf { it.first } / w; val x1 = pts.maxOf { it.first } / w
                    val y0 = pts.minOf { it.second } / h; val y1 = pts.maxOf { it.second } / h
                    // A little bigger than the face box, so hair and chin are covered too.
                    out += (x0 + x1) / 2f; out += (y0 + y1) / 2f
                    out += (x1 - x0) * 0.68f; out += (y1 - y0) * 0.75f
                }
                regions = out.toFloatArray()
                lastUpdate = System.currentTimeMillis()
            }
            .addOnCompleteListener { busy = false }
    }

    /** Blurs the faces in a saved JPEG (rewrites it, keeping its camera info). Call off the main thread. */
    fun applyToSavedJpeg(context: Context, uri: Uri): Boolean {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
            val exif = ExifInterface(bytes.inputStream())
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val rot = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
            val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.copy(Bitmap.Config.ARGB_8888, true) ?: return false
            val faces = Tasks.await(still.process(InputImage.fromBitmap(src, rot)))
            if (faces.isEmpty()) { src.recycle(); return true }
            val w = src.width; val h = src.height
            val canvas = Canvas(src)
            for (f in faces) {
                val b = f.boundingBox
                // ML Kit's boxes are in the upright picture; the stored pixels are not rotated.
                val pts = listOf(b.left to b.top, b.right to b.bottom).map { (ux, uy) ->
                    when (rot) {
                        90 -> uy.toFloat() to (h - ux).toFloat()
                        180 -> (w - ux).toFloat() to (h - uy).toFloat()
                        270 -> (w - uy).toFloat() to ux.toFloat()
                        else -> ux.toFloat() to uy.toFloat()
                    }
                }
                val l = minOf(pts[0].first, pts[1].first); val r = maxOf(pts[0].first, pts[1].first)
                val t = minOf(pts[0].second, pts[1].second); val bt = maxOf(pts[0].second, pts[1].second)
                val padX = (r - l) * 0.18f; val padY = (bt - t) * 0.25f
                val rect = RectF((l - padX).coerceAtLeast(0f), (t - padY).coerceAtLeast(0f), (r + padX).coerceAtMost(w.toFloat()), (bt + padY).coerceAtMost(h.toFloat()))
                if (rect.width() < 4 || rect.height() < 4) continue
                // Pixelate: shrink the face to ~12 blocks across and scale back up without smoothing.
                val sub = Bitmap.createBitmap(src, rect.left.toInt(), rect.top.toInt(), rect.width().toInt(), rect.height().toInt())
                val blocks = 12
                val small = Bitmap.createScaledBitmap(sub, blocks, (blocks * rect.height() / rect.width()).toInt().coerceAtLeast(1), true)
                val big = Bitmap.createScaledBitmap(small, sub.width, sub.height, false)
                val clip = Path().apply { addOval(rect, Path.Direction.CW) }
                canvas.save(); canvas.clipPath(clip)
                canvas.drawBitmap(big, rect.left, rect.top, Paint())
                canvas.restore()
                sub.recycle(); small.recycle(); big.recycle()
            }
            val tmp = java.io.File(context.cacheDir, "faceblur_${System.currentTimeMillis()}.jpg")
            tmp.outputStream().use { src.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            src.recycle()
            // Keep the camera info (date, lens, orientation…) from the original.
            try {
                val outExif = ExifInterface(tmp.path)
                for (tag in listOf(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                    ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
                )) exif.getAttribute(tag)?.let { outExif.setAttribute(tag, it) }
                outExif.saveAttributes()
            } catch (_: Throwable) {}
            context.contentResolver.openOutputStream(uri, "wt")?.use { o -> tmp.inputStream().use { it.copyTo(o) } }
            tmp.delete()
            return true
        } catch (_: Throwable) {
            return false
        }
    }
}
