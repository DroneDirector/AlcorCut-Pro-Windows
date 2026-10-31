package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import java.io.File

/** Photo exports: the PC has the memory, so they run in the app itself. */
internal object PhotoExportRemote {
    suspend fun run(context: Context, req: PhotoExportReq): PhotoExportRes =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runPhotoExport(context, req) }
}

/** AVIF saving with avifenc (libavif + libaom, BSD), which comes with the app. */
object AvifWriter {
    @Volatile var lastError: String? = null
    val supported: Boolean get() = WinEncode.has("avifenc.exe")
    fun write(src: Bitmap, out: File, quality: Int): Boolean {
        val b = WinEncode.avif(src, quality)
        lastError = WinEncode.lastError
        if (b == null) return false
        out.writeBytes(b); return true
    }
}

/**
 * Lottie animations as layers: After Effects animations exported as .json or .zip, drawn with Skia's
 * Lottie player (Skottie, BSD licence, part of the app's graphics library). They loop over the layer's time.
 */
object LottieRender {
    private val anims = HashMap<String, org.jetbrains.skia.skottie.Animation?>()

    /** Copies a picked .json / .zip into the app (so projects keep it); returns the copy, or null if it isn't a Lottie file. */
    fun import(context: Context, uri: android.net.Uri): android.net.Uri? = try {
        val dir = File(context.filesDir, "lottie").apply { mkdirs() }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw java.io.IOException()
        val zip = bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val f = File(dir, "${bytes.contentHashCode().toUInt().toString(16)}.${if (zip) "zip" else "json"}")
        if (!f.exists()) f.writeBytes(bytes)
        val u = android.net.Uri.fromFile(f)
        if (anim(u) == null) { f.delete(); null } else u
    } catch (_: Throwable) { null }

    private fun anim(uri: android.net.Uri): org.jetbrains.skia.skottie.Animation? = synchronized(anims) {
        anims.getOrPut(uri.toString()) {
            try {
                val f = uri.toFile() ?: return@getOrPut null
                val json = if (f.name.endsWith(".zip")) {
                    java.util.zip.ZipInputStream(f.inputStream()).use { z ->
                        var e = z.nextEntry; var text: String? = null
                        while (e != null && text == null) { if (e.name.endsWith(".json") && !e.name.endsWith("manifest.json")) text = String(z.readBytes()); e = z.nextEntry }
                        text
                    }
                } else f.readText()
                json?.let { org.jetbrains.skia.skottie.Animation.makeFromString(it) }
            } catch (_: Throwable) { null }
        }
    }

    /** Length of one loop (ms). */
    fun durationMs(context: Context, l: Layer): Long = l.imageUri?.let { anim(it) }?.let { (it.duration * 1000).toLong().coerceAtLeast(1) } ?: 1000L

    /** Which frame shows at timeline [atMs] (loops over the layer's time). */
    fun frameIndex(context: Context, l: Layer, atMs: Long): Int {
        val a = l.imageUri?.let { anim(it) } ?: return 0
        val start = if (l.wholeVideo) 0L else l.startMs
        val dur = (a.duration * 1000).toLong().coerceAtLeast(1)
        val t = ((atMs - start).coerceAtLeast(0) % dur).toFloat() / dur
        return (t * (a.outPoint - a.inPoint)).toInt()
    }

    /** The animation at frame [frame], [h0] px tall. */
    fun render(context: Context, l: Layer, h0: Float, frameW: Int, frame: Int): Bitmap? {
        val a = l.imageUri?.let { anim(it) } ?: return null
        if (a.width <= 0f || a.height <= 0f) return null
        val h = h0.toInt().coerceAtLeast(4)
        val w = (h * a.width / a.height).toInt().coerceIn(4, frameW * 2)
        val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(w, h)
        synchronized(a) {
            a.seekFrame((a.inPoint + frame).coerceIn(a.inPoint, a.outPoint))
            a.render(surface.canvas, org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()))
        }
        val img = surface.makeImageSnapshot()
        val bm = org.jetbrains.skia.Bitmap(); bm.allocPixels(org.jetbrains.skia.ImageInfo.makeN32(w, h, org.jetbrains.skia.ColorAlphaType.UNPREMUL))
        img.readPixels(bm, 0, 0)
        val bytes = bm.readPixels() ?: return null
        val px = IntArray(w * h); java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(px)
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}
