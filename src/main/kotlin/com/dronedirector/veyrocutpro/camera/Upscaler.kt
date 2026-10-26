package com.dronedirector.veyrocutpro.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.sqrt

/** AI upscale settings (off unless the user turns it on). */
object UpscaleSettings {
    private const val PREFS = "procam"
    var enabled by mutableStateOf(false)     // shows the AI sizes in the photo size picker
        private set
    /** Size chosen for the current photo (set by the camera from the selected AI size). */
    var targetMp by mutableIntStateOf(0)
    var high by mutableStateOf(false)         // High quality: slower, keeps more real detail
        private set
    var keepOriginal by mutableStateOf(true)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = p.getBoolean("ai_upscale_on", false)
        high = p.getBoolean("ai_upscale_high", false)
        keepOriginal = p.getBoolean("ai_upscale_keep", true)
        loaded = true
    }

    fun set(context: Context, on: Boolean = enabled, hq: Boolean = high, keep: Boolean = keepOriginal) {
        enabled = on; high = hq; keepOriginal = keep
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("ai_upscale_on", on).putBoolean("ai_upscale_high", hq).putBoolean("ai_upscale_keep", keep).apply()
    }
}

/**
 * On-device AI super-resolution (ESRGAN, 4×) run in overlapping tiles on the GPU when possible,
 * then resampled to the chosen megapixel size. Each photo is upscaled in the background and
 * saved as a new picture next to the original.
 */
object Upscaler {
    var pending by mutableIntStateOf(0)
        private set
    var progress by mutableIntStateOf(0)
        private set
    var lastNote by mutableStateOf<String?>(null)

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "ai-upscale").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val main = Handler(Looper.getMainLooper())
    private var model: com.google.ai.edge.litert.CompiledModel? = null
    private var inBufs: List<com.google.ai.edge.litert.TensorBuffer> = emptyList()
    private var outBufs: List<com.google.ai.edge.litert.TensorBuffer> = emptyList()
    @Volatile private var jobGen = 0
    private var tileIn = 0
    private var tileOut = 0
    private val factor get() = tileOut / tileIn.coerceAtLeast(1)
    private var modelMissing = false

    /** Bumped by "stop": queued and running jobs from an older generation end straight away. */
    private val generation = java.util.concurrent.atomic.AtomicInteger(0)
    private class Stopped : RuntimeException()

    /** Set while an export runs in the export process: Stop is passed on to it. */
    @Volatile var onStop: (() -> Unit)? = null
    /** Shows the export process's upscaling progress here. */
    private var remoteOn = false
    fun showRemote(on: Boolean, p: Int) { if (on != remoteOn) { remoteOn = on; pending += if (on) 1 else -1 }; progress = p }

    fun cancelAll() {
        onStop?.invoke()
        generation.incrementAndGet()
        lastNote = "AI upscaling stopped"
    }

    fun enqueue(context: Context, uri: Uri, mp: Int) {
        val gen = generation.get()
        if (mp <= 0) return
        val app = context.applicationContext
        val hq = UpscaleSettings.high
        val keep = UpscaleSettings.keepOriginal
        main.post { pending++ }
        worker.execute {
            val note = try {
                if (gen != generation.get()) null else { jobGen = gen; process(app, uri, mp, hq, keep) }
            } catch (_: Stopped) { null } catch (t: Throwable) { "AI upscale failed: ${t.message ?: t.javaClass.simpleName}" }
            main.post { pending--; progress = 0; if (note != null) lastNote = note }
        }
    }

    private fun setProgress(p: Int) = main.post { progress = p }

    /**
     * Photo editor: AI-upscales an edited picture to [tw]×[th] (cloud first when chosen in settings).
     * Blocks; call off the main thread. Returns null when stopped.
     */
    fun upscaleBitmap(context: Context, src: Bitmap, tw: Int, th: Int): Bitmap? {
        val gen = generation.get()
        val app = context.applicationContext
        main.post { pending++ }
        try {
            return worker.submit<Bitmap?> {
                jobGen = gen
                if (CloudAi.usePhotos(app)) {
                    try {
                        val jpeg = java.io.ByteArrayOutputStream().also { src.compress(Bitmap.CompressFormat.JPEG, 96, it) }.toByteArray()
                        val res = CloudAi.upscalePhoto(app, jpeg, tw.toFloat() / src.width)
                        BitmapFactory.decodeByteArray(res, 0, res.size)?.let { b ->
                            return@submit if (b.width == tw && b.height == th) b else Bitmap.createScaledBitmap(b, tw, th, true).also { b.recycle() }
                        }
                    } catch (_: CloudAi.Stopped) { throw Stopped() } catch (_: Throwable) {}
                }
                val out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
                if (ensureModel(app)) {
                    val f = factor.coerceAtLeast(1)
                    val pre = (tw.toDouble() / src.width / f).coerceAtMost(1.0)
                    val iw = (src.width * pre).toInt().coerceAtLeast(8); val ih = (src.height * pre).toInt().coerceAtLeast(8)
                    val inp = if (iw == src.width && ih == src.height) src else Bitmap.createScaledBitmap(src, iw, ih, true)
                    runTiles(inp, out, tw.toFloat() / (iw * f), th.toFloat() / (ih * f))
                    if (inp !== src) inp.recycle()
                } else {
                    Canvas(out).drawBitmap(src, null, RectF(0f, 0f, tw.toFloat(), th.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                }
                out
            }.get()
        } catch (e: java.util.concurrent.ExecutionException) {
            if (e.cause is Stopped) return null
            throw e.cause ?: e
        } finally {
            main.post { pending--; progress = 0 }
        }
    }

    /** Loads ESRGAN once; GPU first, CPU if the GPU path isn't available. Returns false if the model isn't in the app. */
    private fun ensureModel(context: Context): Boolean {
        if (model != null) return true
        if (modelMissing) return false
        // Downloaded on first launch; not there yet = classic resize for now (asked again next time).
        if (!com.dronedirector.veyrocutpro.editor.AppData.has(context, "esrgan.tflite")) return false
        for (acc in listOf(com.google.ai.edge.litert.Accelerator.GPU, com.google.ai.edge.litert.Accelerator.CPU)) {
            try {
                val m = com.google.ai.edge.litert.CompiledModel.create(com.dronedirector.veyrocutpro.editor.AppData.file(context, "esrgan.tflite").path, com.google.ai.edge.litert.CompiledModel.Options(acc), null)
                val ins = m.createInputBuffers(); val outs = m.createOutputBuffers()
                // ESRGAN: 50×50 RGB in (0..255, NHWC) → 200×200 out. Self-test on a colour ramp.
                val tin = 50; val tout = 200
                val inp = FloatArray(tin * tin * 3)
                var i = 0
                for (y in 0 until tin) for (x in 0 until tin) { inp[i++] = 40f + 160f * x / tin; inp[i++] = 60f + 120f * y / tin; inp[i++] = 120f }
                ins[0].writeFloat(inp)
                m.run(ins, outs)
                val out = outs[0].readFloat()
                if (out.size != tout * tout * 3) throw IllegalStateException("shape")
                var err = 0.0; var n = 0
                for (y in 0 until tout step 7) for (x in 0 until tout step 7) {
                    val o = (y * tout + x) * 3
                    err += kotlin.math.abs(out[o] - (40f + 160f * (x / 4) / tin)) + kotlin.math.abs(out[o + 1] - (60f + 120f * (y / 4) / tin)); n += 2
                }
                if (err / n > 14.0) throw IllegalStateException("self-test failed")
                model = m; inBufs = ins; outBufs = outs; tileIn = tin; tileOut = tout
                return true
            } catch (_: Throwable) {}
        }
        modelMissing = true
        return false
    }

    private fun process(context: Context, uri: Uri, mp: Int, hq: Boolean, keep: Boolean): String? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth; val h = bounds.outHeight
        if (w <= 0 || h <= 0) return "AI upscale: couldn't read the photo"
        val target = mp * 1_000_000.0
        if (w.toLong() * h >= target * 0.95) return null   // already that big
        val s = sqrt(target / (w.toDouble() * h))
        val tw = (w * s).toInt() / 2 * 2; val th = (h * s).toInt() / 2 * 2
        val tmp = File(context.cacheDir, "upscale_${System.currentTimeMillis()}.jpg")

        // Cloud AI (Topaz on fal.ai) when chosen in settings; falls back to the phone if it fails.
        var cloudOk = false
        var cloudNote: String? = null
        if (CloudAi.usePhotos(context)) {
            try {
                val bytes = cr.openInputStream(uri)!!.use { it.readBytes() }
                val res = CloudAi.upscalePhoto(context, bytes, s.toFloat())
                // Resample the cloud result to exactly the chosen size.
                val bmp = BitmapFactory.decodeByteArray(res, 0, res.size)
                if (bmp != null) {
                    val sized = if (bmp.width == tw && bmp.height == th) bmp else Bitmap.createScaledBitmap(bmp, tw, th, true)
                    tmp.outputStream().use { sized.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    if (sized !== bmp) sized.recycle(); bmp.recycle()
                    cloudOk = true
                }
            } catch (_: CloudAi.Stopped) { throw Stopped() } catch (t: Throwable) { cloudNote = t.message }
        }
        val ai = if (cloudOk) true else ensureModel(context)
        if (!cloudOk) {
        val src = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }) }
            ?: return "AI upscale: couldn't read the photo"

        val out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        if (ai) {
            // ESRGAN is 4×: feed it a smaller copy (Standard) or close to full size (High), then resample.
            val f = factor.coerceAtLeast(1).toDouble()
            val pre = (if (hq) s * 2.0 / f else s / f).coerceAtMost(1.0)
            val iw = (w * pre).toInt().coerceAtLeast(8); val ih = (h * pre).toInt().coerceAtLeast(8)
            val inp = if (iw == w && ih == h) src else Bitmap.createScaledBitmap(src, iw, ih, true)
            runTiles(inp, out, tw.toFloat() / (iw * factor), th.toFloat() / (ih * factor))
            if (inp !== src) inp.recycle()
        } else {
            Canvas(out).drawBitmap(src, null, RectF(0f, 0f, tw.toFloat(), th.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        src.recycle()
        tmp.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        out.recycle()
        }

        // Save as a new JPEG next to the original, keeping its EXIF (orientation, time, GPS, lens).
        val baseName = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?.substringBeforeLast('.') ?: "IMG"
        try {
            val srcExif = cr.openInputStream(uri)?.use { ExifInterface(it) }
            val dst = ExifInterface(tmp.path)
            if (srcExif != null) for (tag in EXIF_TAGS) srcExif.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
            dst.setAttribute(ExifInterface.TAG_SOFTWARE, "AlcorCut Pro AI upscale ${mp} MP")
            dst.saveAttributes()
        } catch (_: Throwable) {}

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${baseName}_AI${mp}MP.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/AlcorCutPro")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val target2 = Storage.insert(context, Storage.load(context), values, Storage.Kind.IMAGE) ?: run { tmp.delete(); return "AI upscale: couldn't save" }
        cr.openOutputStream(target2)?.use { os -> tmp.inputStream().use { it.copyTo(os) } }
        Storage.publish(context, target2)
        tmp.delete()
        if (!keep) Storage.discard(context, uri)
        return when {
            cloudOk -> "Cloud AI upscaled to ${mp} MP (${tw}×${th})"
            cloudNote != null -> "Cloud AI unavailable ($cloudNote) — upscaled on the phone"
            ai -> "AI upscaled to ${mp} MP (${tw}×${th})"
            else -> "Upscaled to ${mp} MP (AI model unavailable — classic resize used)"
        }
    }

    /** Runs ESRGAN over overlapping tiles and paints each result into [out] scaled by kx/ky. */
    private fun runTiles(inp: Bitmap, out: Bitmap, kx: Float, ky: Float) {
        val m = model ?: return
        val t = tileIn
        val f = factor
        val pad = (t / 10).coerceIn(3, 8)
        val step = t - 2 * pad
        val iw = inp.width; val ih = inp.height
        val px = IntArray(iw * ih)
        inp.getPixels(px, 0, iw, 0, 0, iw, ih)
        val inArr = FloatArray(t * t * 3)
        val ot = tileOut
        val tilePx = IntArray(ot * ot)
        val tileBmp = Bitmap.createBitmap(ot, ot, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val cols = ceil(iw / step.toDouble()).toInt(); val rows = ceil(ih / step.toDouble()).toInt()
        val total = cols * rows
        var done = 0
        for (ty in 0 until rows) for (tx in 0 until cols) {
            val x0 = tx * step; val y0 = ty * step
            var k = 0
            for (yy in 0 until t) {
                val sy = (y0 - pad + yy).coerceIn(0, ih - 1)
                for (xx in 0 until t) {
                    val sx = (x0 - pad + xx).coerceIn(0, iw - 1)
                    val c = px[sy * iw + sx]
                    inArr[k++] = ((c shr 16) and 0xFF).toFloat()
                    inArr[k++] = ((c shr 8) and 0xFF).toFloat()
                    inArr[k++] = (c and 0xFF).toFloat()
                }
            }
            val vw = minOf(step, iw - x0); val vh = minOf(step, ih - y0)
            val dst = RectF(x0 * f * kx, y0 * f * ky, (x0 + vw) * f * kx, (y0 + vh) * f * ky)
            // Flat areas (sky, walls) gain nothing from the AI: a smooth resize is identical and instant.
            if (isFlat(px, iw, x0, y0, vw, vh)) {
                canvas.drawBitmap(inp, android.graphics.Rect(x0, y0, x0 + vw, y0 + vh), dst, paint)
                done++
                if (jobGen != generation.get()) { tileBmp.recycle(); throw Stopped() }
                if (done % 8 == 0) setProgress(done * 100 / total)
                continue
            }
            inBufs[0].writeFloat(inArr)
            m.run(inBufs, outBufs)
            val res = outBufs[0].readFloat()
            for (i in 0 until ot * ot) {
                val r = res[i * 3].toInt().coerceIn(0, 255)
                val g = res[i * 3 + 1].toInt().coerceIn(0, 255)
                val b = res[i * 3 + 2].toInt().coerceIn(0, 255)
                tilePx[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            tileBmp.setPixels(tilePx, 0, ot, 0, 0, ot, ot)
            // Keep the inner part of the tile (edges of each tile are less accurate).
            val srcR = android.graphics.Rect(pad * f, pad * f, (pad + vw) * f, (pad + vh) * f)
            canvas.drawBitmap(tileBmp, srcR, dst, paint)
            done++
            if (jobGen != generation.get()) { tileBmp.recycle(); throw Stopped() }
            if (done % 4 == 0) setProgress(done * 100 / total)
        }
        tileBmp.recycle()
    }

    /** True when a tile has almost no detail (low contrast in luminance). */
    private fun isFlat(px: IntArray, iw: Int, x0: Int, y0: Int, w: Int, h: Int): Boolean {
        var mn = 255; var mx = 0
        var y = y0
        while (y < y0 + h) {
            var x = x0
            while (x < x0 + w) {
                val c = px[y * iw + x]
                val l = (((c shr 16) and 0xFF) * 3 + ((c shr 8) and 0xFF) * 6 + (c and 0xFF)) / 10
                if (l < mn) mn = l; if (l > mx) mx = l
                if (mx - mn > 10) return false
                x += 2
            }
            y += 2
        }
        return true
    }

    private val EXIF_TAGS = listOf(
        ExifInterface.TAG_ORIENTATION, ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
        ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_FLASH, ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF, ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
    )
}
