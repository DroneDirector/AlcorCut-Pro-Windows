package com.dronedirector.veyrocutpro.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

/**
 * Colour looks. Each is a 4×5 colour matrix, the same one drives the live GPU
 * preview/video filter and the post-processing applied to saved JPEGs.
 */
enum class Filter(val label: String, val swatch: Long) {
    NONE("Natural", 0xFF9E9E9E),
    VIVID("Vivid", 0xFFFF5252),
    CHROME("Chrome", 0xFF40C4FF),
    WARM("Warm", 0xFFFFAB40),
    COOL("Cool", 0xFF448AFF),
    CINEMA("Cinema", 0xFF26A69A),
    FADE("Fade", 0xFFBCAAA4),
    SEPIA("Sepia", 0xFFA1887F),
    MONO("Mono", 0xFFE0E0E0),
    NOIR("Noir", 0xFF424242),

    // Film looks: colour matrix plus tone curve, split toning and faded blacks, rendered live.
    TEAL_ORANGE("Teal & Orange", 0xFF00897B),
    PRINT("Print Film", 0xFFD7A86E),
    BLEACH("Bleach Bypass", 0xFF90A4AE),
    GOLDEN("Golden Hour", 0xFFFFB74D),
    THRILLER("Cold Thriller", 0xFF4F7CAC),
    VINTAGE("Vintage 8mm", 0xFFC9A27E);

    /** Extra tone work for film looks (null = plain colour matrix). */
    val look: Look? get() = when (this) {
        TEAL_ORANGE -> Look(floatArrayOf(-0.04f, 0.02f, 0.06f), floatArrayOf(0.06f, 0.025f, -0.05f), 0.30f, 0.0f)
        PRINT -> Look(floatArrayOf(0.0f, 0.01f, 0.03f), floatArrayOf(0.035f, 0.015f, -0.02f), 0.35f, 0.035f)
        BLEACH -> Look(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 0f), 0.55f, 0.0f)
        GOLDEN -> Look(floatArrayOf(0.02f, 0.0f, -0.01f), floatArrayOf(0.07f, 0.035f, -0.04f), 0.15f, 0.02f)
        THRILLER -> Look(floatArrayOf(-0.02f, 0.02f, 0.05f), floatArrayOf(-0.01f, 0.01f, 0.03f), 0.35f, 0.0f)
        VINTAGE -> Look(floatArrayOf(0.03f, 0.0f, 0.04f), floatArrayOf(0.04f, 0.03f, -0.03f), 0.10f, 0.09f)
        else -> null
    }
    val isFilmLook: Boolean get() = look != null

    /** Android ColorMatrix values (offsets in 0..255). */
    val matrix: FloatArray by lazy { build().array.copyOf() }

    private fun build(): ColorMatrix {
        val m = ColorMatrix()
        when (this) {
            NONE -> {}
            VIVID -> { m.setSaturation(1.45f); m.postConcat(contrast(1.08f)) }
            CHROME -> { m.setSaturation(1.2f); m.postConcat(contrast(1.18f)) }
            WARM -> m.postConcat(scale(1.08f, 1.0f, 0.86f, 6f, 2f, -4f))
            COOL -> m.postConcat(scale(0.9f, 1.0f, 1.1f, -4f, 0f, 8f))
            CINEMA -> {
                // Teal shadows, warm skin: push blue into darks, red into mids.
                m.set(
                    floatArrayOf(
                        1.10f, -0.05f, -0.05f, 0f, 4f,
                        -0.03f, 1.02f, 0.01f, 0f, 2f,
                        -0.08f, 0.08f, 1.00f, 0f, 14f,
                        0f, 0f, 0f, 1f, 0f,
                    )
                )
                val sat = ColorMatrix().apply { setSaturation(0.9f) }
                m.postConcat(sat)
                m.postConcat(contrast(1.1f))
            }
            FADE -> {
                m.setSaturation(0.72f)
                m.postConcat(contrast(0.82f))
                m.postConcat(scale(1f, 1f, 1f, 14f, 14f, 16f))
            }
            SEPIA -> m.set(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
            MONO -> m.setSaturation(0f)
            NOIR -> { m.setSaturation(0f); m.postConcat(contrast(1.4f)) }
            TEAL_ORANGE -> m.setSaturation(1.12f)
            PRINT -> { m.setSaturation(0.88f); m.postConcat(scale(1.03f, 1.0f, 0.96f, 2f, 0f, 0f)) }
            BLEACH -> m.setSaturation(0.42f)
            GOLDEN -> { m.setSaturation(1.08f); m.postConcat(scale(1.08f, 1.0f, 0.88f, 4f, 2f, -4f)) }
            THRILLER -> { m.setSaturation(0.68f); m.postConcat(scale(0.94f, 1.0f, 1.08f, 0f, 2f, 6f)) }
            VINTAGE -> { m.setSaturation(0.74f); m.postConcat(contrast(0.92f)) }
        }
        return m
    }

    /** Column-major mat3 for GLSL plus a vec3 offset in 0..1. */
    fun glMatrix(): FloatArray {
        val a = matrix
        return floatArrayOf(a[0], a[5], a[10], a[1], a[6], a[11], a[2], a[7], a[12])
    }

    fun glOffset(): FloatArray {
        val a = matrix
        return floatArrayOf(a[4] / 255f, a[9] / 255f, a[14] / 255f)
    }

    companion object {
        private fun contrast(c: Float): ColorMatrix {
            val t = 128f * (1f - c)
            return ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        }

        private fun scale(r: Float, g: Float, b: Float, ro: Float, go: Float, bo: Float) = ColorMatrix(
            floatArrayOf(
                r, 0f, 0f, 0f, ro,
                0f, g, 0f, 0f, go,
                0f, 0f, b, 0f, bo,
                0f, 0f, 0f, 1f, 0f,
            )
        )

        private val keptExif = listOf(
            ExifInterface.TAG_ORIENTATION, ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_FLASH, ExifInterface.TAG_EXPOSURE_BIAS_VALUE, ExifInterface.TAG_SOFTWARE,
        )

        /** Same maths as the live shader's film-look stage, applied to a bitmap row by row. */
        fun applyLook(bmp: Bitmap, look: Look) {
            val w = bmp.width
            val row = IntArray(w)
            for (y in 0 until bmp.height) {
                bmp.getPixels(row, 0, w, 0, y, w, 1)
                for (x in 0 until w) {
                    val c = row[x]
                    val rgb = floatArrayOf(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)
                    look.apply(rgb)
                    row[x] = (0xFF shl 24) or ((rgb[0] * 255f + 0.5f).toInt() shl 16) or
                        ((rgb[1] * 255f + 0.5f).toInt() shl 8) or (rgb[2] * 255f + 0.5f).toInt()
                }
                bmp.setPixels(row, 0, w, 0, y, w, 1)
            }
        }

        /**
         * Applies a filter to a JPEG already saved in MediaStore, in place, keeping the
         * important EXIF tags (orientation, exposure, date). Runs on a background thread.
         */
        fun applyToSavedJpeg(context: Context, uri: Uri, filter: Filter): Boolean {
            if (filter == NONE) return true
            val resolver = context.contentResolver
            return try {
                val saved = resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val exif = ExifInterface(pfd.fileDescriptor)
                    keptExif.associateWith { exif.getAttribute(it) }.filterValues { it != null }
                } ?: emptyMap()

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val pixels = bounds.outWidth.toLong() * bounds.outHeight
                val opts = BitmapFactory.Options().apply {
                    inMutable = true
                    // Keep memory sane on very high resolution sensors.
                    inSampleSize = if (pixels > 30_000_000L) 2 else 1
                }
                val src = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false
                val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
                Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(filter.matrix) })
                src.recycle()
                filter.look?.let { applyLook(out, it) }
                val bytes = ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
                out.recycle()

                resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: return false
                resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                    val exif = ExifInterface(pfd.fileDescriptor)
                    saved.forEach { (k, v) -> exif.setAttribute(k, v) }
                    exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "AlcorCut Pro ${filter.label}")
                    exif.saveAttributes()
                }
                true
            } catch (_: Throwable) {
                false
            }
        }
    }
}

/** Tone stage of a film look: S-curve contrast, shadow/highlight tints and lifted (faded) blacks. */
class Look(val shadow: FloatArray, val highlight: FloatArray, val sCurve: Float, val fade: Float) {
    fun apply(rgb: FloatArray) {
        val l = 0.2126f * rgb[0] + 0.7152f * rgb[1] + 0.0722f * rgb[2]
        val sh = 1f - smooth(0f, 0.5f, l)
        val hi = smooth(0.5f, 1f, l)
        for (i in 0..2) {
            var v = rgb[i]
            val s = v * v * (3f - 2f * v)
            v += (s - v) * sCurve
            v += shadow[i] * sh + highlight[i] * hi
            v = fade + v * (1f - fade)
            rgb[i] = v.coerceIn(0f, 1f)
        }
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
