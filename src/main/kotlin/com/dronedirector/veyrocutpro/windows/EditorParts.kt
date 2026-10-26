package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import java.io.File

/** Photo exports: the PC has the memory, so they run in the app itself. */
internal object PhotoExportRemote {
    suspend fun run(context: Context, req: PhotoExportReq): PhotoExportRes =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runPhotoExport(context, req) }
}

/** AVIF saving comes later on Windows (JPEG, PNG, TIFF and the rest work). */
object AvifWriter {
    @Volatile var lastError: String? = "AVIF saving isn't on Windows yet"
    val supported: Boolean = false
    fun write(src: Bitmap, out: File, quality: Int): Boolean = false
}

/** People masks (face and body parts) come with the AI models for Windows. */
object PeopleMask {
    val PARTS = listOf("Whole person", "Face skin", "Body skin", "Eye whites", "Irises", "Eyebrows", "Lips", "Teeth", "Hair", "Clothes")
    @Volatile var lastFaces = -1
    fun make(context: Context, src: Bitmap, parts: List<Int>): Bitmap? = null
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
