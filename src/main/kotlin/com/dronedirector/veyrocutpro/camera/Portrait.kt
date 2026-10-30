package com.dronedirector.veyrocutpro.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Portrait modes (Smart → PORTRAIT and AI PORTRAIT): after the shot, the person (ML Kit selfie segmentation, on the
 * phone) — or any other subject (the editor's subject model, or Cloud AI when it's on) — is kept
 * sharp and the background is blurred like a fast lens, with a soft, halo-free edge.
 */
object Portrait {
    /** Blur strengths, shown as lens apertures: f/1.4 (most blur) to f/16 (least), in third stops. */
    val F_STOPS = floatArrayOf(1.4f, 1.6f, 1.8f, 2f, 2.2f, 2.5f, 2.8f, 3.2f, 3.5f, 4f, 4.5f, 5f, 5.6f, 6.3f, 7.1f, 8f, 9f, 10f, 11f, 13f, 14f, 16f)
    /** Pass as the f-number to let the app pick the blur from how much of the frame the subject fills. */
    const val AUTO = 0f

    fun label(f: Float): String = "f/" + if (f >= 10f) f.roundToInt().toString()
        else String.format(java.util.Locale.US, "%.1f", f).removeSuffix(".0")

    /** Nearest third-stop index for an f-number. */
    fun indexOf(f: Float): Int = F_STOPS.indices.minByOrNull { kotlin.math.abs(F_STOPS[it] - f) } ?: 3

    /** 1 at f/1.4, a bit under ¾ per stop after that (matches the old five-step scale). */
    internal fun strength(f: Float): Float {
        val stops = 2f * (kotlin.math.ln(f / 1.4f) / kotlin.math.ln(2f))
        return Math.pow(0.72, stops.toDouble()).toFloat().coerceIn(0.06f, 1f)
    }

    /** Auto: close-ups get a gentler blur (hair edges stay clean), small / distant subjects a stronger one so it still reads. */
    internal fun autoF(mask: FloatArray): Float {
        val cover = mask.count { it > 0.5f }.toFloat() / mask.size
        return when {
            cover >= 0.45f -> 2.8f
            cover >= 0.25f -> 2.2f
            cover >= 0.12f -> 1.8f
            else -> 1.4f
        }
    }
    private const val MAX_SIDE = 3264
    private const val MASK = 512

    private val segmenter by lazy {
        Segmentation.getClient(
            SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build()
        )
    }

    /** Person mask (0..1) at [mw]×[mh], or null if no person is found. */
    private fun personMask(small: Bitmap): FloatArray? = try {
        val m = Tasks.await(segmenter.process(InputImage.fromBitmap(small, 0)))
        val buf = m.buffer.order(ByteOrder.nativeOrder()); buf.rewind()
        val raw = FloatArray(m.width * m.height) { buf.float }
        val out = if (m.width == small.width && m.height == small.height) raw else resample(raw, m.width, m.height, small.width, small.height)
        val cover = out.count { it > 0.5f }.toFloat() / out.size
        if (cover < 0.02f) null else out
    } catch (_: Throwable) { null }

    private val subjectSegmenter by lazy {
        SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build())
    }

    /**
     * Whatever stands out in the frame — pets, flowers, rocks, branches, objects (ML Kit subject
     * segmentation, on the phone; its model comes with Google Play services). Mask at [small]'s size.
     * Call off the main thread.
     */
    internal fun anySubjectMask(small: Bitmap): FloatArray? = try {
        // The model wants at least ~512 px; upscale small frames for it.
        val side = max(small.width, small.height)
        val inp = if (side >= 512) small else Bitmap.createScaledBitmap(small, (small.width * 512f / side).roundToInt(), (small.height * 512f / side).roundToInt(), true)
        val r = Tasks.await(subjectSegmenter.process(InputImage.fromBitmap(inp, 0)))
        val fb = r.foregroundConfidenceMask
        if (fb == null) null else {
            fb.rewind()
            val iw = inp.width; val ih = inp.height
            val raw = FloatArray(iw * ih) { if (fb.hasRemaining()) fb.get() else 0f }
            if (inp !== small) inp.recycle()
            val out = if (iw == small.width && ih == small.height) raw else resample(raw, iw, ih, small.width, small.height)
            if (out.count { it > 0.5f } < out.size * 0.02f) null else out
        }
    } catch (_: Throwable) { null }

    /** Person mask for the live viewfinder (stream mode: fast, steadier between frames). */
    private val liveSegmenter by lazy {
        Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.STREAM_MODE).build())
    }
    internal fun livePersonMask(small: Bitmap): FloatArray? = try {
        val m = Tasks.await(liveSegmenter.process(InputImage.fromBitmap(small, 0)))
        val buf = m.buffer.order(ByteOrder.nativeOrder()); buf.rewind()
        val raw = FloatArray(m.width * m.height) { buf.float }
        val out = if (m.width == small.width && m.height == small.height) raw else resample(raw, m.width, m.height, small.width, small.height)
        if (out.count { it > 0.5f }.toFloat() / out.size < 0.02f) null else out
    } catch (_: Throwable) { null }

    /** Any subject: cloud cut-out when Cloud AI is on, then ML Kit (pets, flowers, rocks…), then the editor's subject AI. */
    private fun subjectMask(context: Context, small: Bitmap): FloatArray? =
        (if (CloudAi.usePhotos(context)) editorSubjectMask(context, small) else null)
            ?: anySubjectMask(small) ?: editorSubjectMask(context, small)

    /** The editor's AI subject selection (cloud when chosen, else DeepLab on the phone). */
    internal fun editorSubjectMask(context: Context, small: Bitmap, allowCloud: Boolean = true): FloatArray? = try {
        val useCloud = allowCloud && CloudAi.usePhotos(context)
        val mb = com.dronedirector.veyrocutpro.editor.AiSegment.subject(context, small, useCloud)
        mb?.let { b ->
            val s = if (b.width == small.width && b.height == small.height) b else Bitmap.createScaledBitmap(b, small.width, small.height, true)
            val px = IntArray(s.width * s.height); s.getPixels(px, 0, s.width, 0, 0, s.width, s.height)
            val out = FloatArray(px.size) { ((px[it] shr 8) and 0xFF) / 255f }
            if (out.count { it > 0.5f } < out.size * 0.02f) null else out
        }
    } catch (_: Throwable) { null }

    internal fun resample(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray = FloatArray(dw * dh) { i ->
        val x = (i % dw) * (sw - 1f) / max(1, dw - 1); val y = (i / dw) * (sh - 1f) / max(1, dh - 1)
        val x0 = x.toInt().coerceAtMost(sw - 2).coerceAtLeast(0); val y0 = y.toInt().coerceAtMost(sh - 2).coerceAtLeast(0)
        val fx = x - x0; val fy = y - y0
        val a = src[y0 * sw + x0]; val b = src[y0 * sw + min(x0 + 1, sw - 1)]
        val c = src[min(y0 + 1, sh - 1) * sw + x0]; val d = src[min(y0 + 1, sh - 1) * sw + min(x0 + 1, sw - 1)]
        (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy
    }

    /** Box blur of a float plane, [passes] times (≈ gaussian). */
    internal fun blurPlane(p: FloatArray, w: Int, h: Int, r: Int, passes: Int = 3) {
        if (r < 1) return
        val tmp = FloatArray(max(w, h))
        repeat(passes) {
            for (y in 0 until h) {
                val o = y * w
                var acc = 0f
                for (x in -r..r) acc += p[o + x.coerceIn(0, w - 1)]
                for (x in 0 until w) {
                    tmp[x] = acc / (2 * r + 1)
                    acc += p[o + (x + r + 1).coerceAtMost(w - 1)] - p[o + (x - r).coerceAtLeast(0)]
                }
                System.arraycopy(tmp, 0, p, o, w)
            }
            for (x in 0 until w) {
                var acc = 0f
                for (y in -r..r) acc += p[y.coerceIn(0, h - 1) * w + x]
                for (y in 0 until h) {
                    tmp[y] = acc / (2 * r + 1)
                    acc += p[(y + r + 1).coerceAtMost(h - 1) * w + x] - p[(y - r).coerceAtLeast(0) * w + x]
                }
                for (y in 0 until h) p[y * w + x] = tmp[y]
            }
        }
    }

    /** Background blur on an upright picture, with the f-number used; null if no subject was found. [fNumber] ≤ 0 = auto. */
    fun render(context: Context, img: Bitmap, fNumber: Float): Pair<Bitmap, Float>? {
        val w = img.width; val h = img.height
        val ms = MASK.toFloat() / max(w, h)
        val mw = (w * ms).roundToInt().coerceAtLeast(8); val mh = (h * ms).roundToInt().coerceAtLeast(8)
        val small = Bitmap.createScaledBitmap(img, mw, mh, true)
        val mask = personMask(small) ?: subjectMask(context, small) ?: return null
        val f = if (fNumber <= 0f) autoF(mask) else fNumber.coerceIn(F_STOPS.first(), F_STOPS.last())
        // Firm up, then soften the edge a little (no hard cut-out look).
        for (i in mask.indices) mask[i] = ((mask[i] - 0.35f) / 0.4f).coerceIn(0f, 1f)
        blurPlane(mask, mw, mh, 1, 2)

        // Blur at a reduced size (fast, and a large blur looks the same).
        val strength = strength(f)
        val bs = 4
        val bw = (w / bs).coerceAtLeast(8); val bh = (h / bs).coerceAtLeast(8)
        val bsmall = Bitmap.createScaledBitmap(img, bw, bh, true)
        val px = IntArray(bw * bh); bsmall.getPixels(px, 0, bw, 0, 0, bw, bh)
        bsmall.recycle()
        val bmask = resample(mask, mw, mh, bw, bh)
        // Only the background is blurred (subject colours don't bleed out as a halo):
        // blur(colour × background) / blur(background).
        val R = FloatArray(px.size); val G = FloatArray(px.size); val B = FloatArray(px.size); val W = FloatArray(px.size)
        for (i in px.indices) {
            val bg = 1f - bmask[i]
            val c = px[i]
            R[i] = ((c shr 16) and 0xFF) * bg; G[i] = ((c shr 8) and 0xFF) * bg; B[i] = (c and 0xFF) * bg; W[i] = bg
        }
        val radius = (max(bw, bh) * 0.018f * strength).roundToInt().coerceAtLeast(1)
        for (pl in listOf(R, G, B, W)) blurPlane(pl, bw, bh, radius)
        for (i in px.indices) {
            val ww = W[i]
            px[i] = if (ww > 0.02f) {
                (0xFF shl 24) or ((R[i] / ww).toInt().coerceIn(0, 255) shl 16) or ((G[i] / ww).toInt().coerceIn(0, 255) shl 8) or (B[i] / ww).toInt().coerceIn(0, 255)
            } else px[i]
        }
        val blurredSmall = Bitmap.createBitmap(px, bw, bh, Bitmap.Config.ARGB_8888)
        val blurred = Bitmap.createScaledBitmap(blurredSmall, w, h, true)
        blurredSmall.recycle()

        // Composite row by row: subject from the original, background from the blur.
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val rowO = IntArray(w); val rowB = IntArray(w)
        for (y in 0 until h) {
            img.getPixels(rowO, 0, w, 0, y, w, 1)
            blurred.getPixels(rowB, 0, w, 0, y, w, 1)
            val my = y * (mh - 1f) / max(1, h - 1)
            val y0 = my.toInt().coerceAtMost(mh - 2).coerceAtLeast(0); val fy = my - y0
            for (x in 0 until w) {
                val mx = x * (mw - 1f) / max(1, w - 1)
                val x0 = mx.toInt().coerceAtMost(mw - 2).coerceAtLeast(0); val fx = mx - x0
                val a = (mask[y0 * mw + x0] * (1 - fx) + mask[y0 * mw + x0 + 1] * fx) * (1 - fy) +
                    (mask[(y0 + 1) * mw + x0] * (1 - fx) + mask[(y0 + 1) * mw + x0 + 1] * fx) * fy
                val o = rowO[x]; val b = rowB[x]
                val r = (((o shr 16) and 0xFF) * a + ((b shr 16) and 0xFF) * (1 - a)).toInt()
                val g = (((o shr 8) and 0xFF) * a + ((b shr 8) and 0xFF) * (1 - a)).toInt()
                val bl = ((o and 0xFF) * a + (b and 0xFF) * (1 - a)).toInt()
                rowO[x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
            }
            out.setPixels(rowO, 0, w, 0, y, w, 1)
        }
        blurred.recycle(); small.recycle()
        return out to f
    }

    /**
     * Blurs the background of a saved JPEG. Rewrites it in place (or saves a copy next to it when
     * [keepOriginal]), keeping its camera info. Returns the portrait's uri and the f-number used
     * ([fNumber] ≤ 0 = auto), or null if nothing was found.
     */
    fun applyToSaved(context: Context, uri: Uri, fNumber: Float, keepOriginal: Boolean): Pair<Uri, Float>? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val exif = ExifInterface(bytes.inputStream())
        val rot = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90; ExifInterface.ORIENTATION_ROTATE_180 -> 180; ExifInterface.ORIENTATION_ROTATE_270 -> 270; else -> 0
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        if (max(bmp.width, bmp.height) > MAX_SIDE) {
            val s = MAX_SIDE.toFloat() / max(bmp.width, bmp.height)
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * s).roundToInt(), (bmp.height * s).roundToInt(), true)
        }
        if (rot != 0) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
        val (out, usedF) = render(context, bmp, fNumber) ?: return null
        bmp.recycle()
        val tmp = java.io.File(context.cacheDir, "portrait_${System.currentTimeMillis()}.jpg")
        try {
            tmp.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            out.recycle()
            try {
                val o = ExifInterface(tmp.path)
                for (tag in listOf(
                    ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                    ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT,
                )) exif.getAttribute(tag)?.let { o.setAttribute(tag, it) }
                o.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                o.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "AlcorCut Pro portrait ${label(usedF)}${if (fNumber <= 0f) " (auto)" else ""}")
                o.saveAttributes()
            } catch (_: Throwable) {}
            return if (keepOriginal) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "PRO_" + System.currentTimeMillis() + "_portrait.jpg")
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/AlcorCutPro")
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }
                val target = Storage.insert(context, Storage.load(context), values, Storage.Kind.IMAGE) ?: return null
                context.contentResolver.openOutputStream(target)?.use { o -> tmp.inputStream().use { it.copyTo(o) } }
                Storage.publish(context, target)
                target to usedF
            } else {
                context.contentResolver.openOutputStream(uri, "wt")?.use { o -> tmp.inputStream().use { it.copyTo(o) } }
                uri to usedF
            }
        } finally { tmp.delete() }
    }
}
