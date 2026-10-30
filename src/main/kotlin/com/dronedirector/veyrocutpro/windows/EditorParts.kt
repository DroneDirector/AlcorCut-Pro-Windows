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

/** Voice isolation (video editor). */
class VoiceIsolator {
    companion object {
        val available: Boolean = false
        val studioAvailable: Boolean = false
        fun studioReady(context: Context): Boolean = false
        fun prepare(context: Context) {}
    }
}

/** Sound waveforms (video editor). */
object Waveforms {
    const val BUCKET_MS = 20L
}

/** Animated (Lottie) layers come with the video editor on Windows. */
object LottieRender {
    fun render(context: Context, l: Layer, h0: Float, frameW: Int, frame: Int): Bitmap? = null
    fun frameIndex(context: Context, l: Layer, atMs: Long): Int = 0
}
