package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.dronedirector.veyrocutpro.camera.CloudAi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Cloud AI noise reduction for video: each clip with AI noise reduction is trimmed to its used
 * range, cleaned by Topaz Nyx on fal.ai and swapped in for the export. Results are cached, so
 * exporting again doesn't upload (or pay) twice.
 */
@UnstableApi
object CloudVideoPrep {
    private fun dir(c: Context) = File(c.cacheDir, "cloudai").apply { mkdirs() }

    private fun keyOf(c: Clip) = "${c.uri}|${c.startMs}|${c.endMs}|${CloudAi.videoModel.name}|${"%.2f".format(c.detail.aiDenoise)}"
        .hashCode().toUInt().toString(16)

    suspend fun prepare(context: Context, clips: List<Clip>): List<Clip> {
        val done = HashMap<String, File>()
        val need = clips.filter { it.detail.aiDenoise > 0f }.distinctBy { keyOf(it) }
        need.forEachIndexed { i, c ->
            val key = keyOf(c)
            // Cloud AI results are kept in the video catalog with the clip: never paid for twice.
            val out = File(MediaCatalog.sub(context, "cloudai"), "nyx_$key.mp4").also { MediaCatalog.claim(context, c.uri, it) }
            if (!out.exists() || out.length() < 1024) {
                CloudAi.show("CLOUD AI · PREPARING CLIP ${i + 1}/${need.size}", 0)
                val trimmed = trim(context, c)
                try {
                    withContext(Dispatchers.IO) { CloudAi.denoiseVideo(context, Uri.fromFile(trimmed), out, c.detail.aiDenoise) }
                } finally { trimmed.delete() }
            }
            done[key] = out
        }
        return clips.map { c ->
            val f = if (c.detail.aiDenoise > 0f) done[keyOf(c)] else null
            if (f == null) c else {
                val len = c.endMs - c.startMs
                c.copy(uri = Uri.fromFile(f), startMs = 0, endMs = len, durationMs = len, detail = c.detail.copy(aiDenoise = 0f))
            }
        }
    }

    /** Copy of just the used range of the clip (start on the main thread). */
    suspend fun trim(context: Context, c: Clip): File = withContext(Dispatchers.Main) {
        val out = File(dir(context), "trim_${System.currentTimeMillis()}.mp4")
        suspendCancellableCoroutine { cont ->
            val t = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) { if (cont.isActive) cont.resume(out) }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        out.delete(); if (cont.isActive) cont.resumeWithException(CloudAi.CloudError("Couldn't prepare the clip (${exportException.errorCodeName})"))
                    }
                })
                .build()
            val item = EditedMediaItem.Builder(
                MediaItem.Builder().setUri(c.uri)
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.startMs).setEndPositionMs(c.endMs).build())
                    .build()
            ).build()
            t.start(item, out.path)
            cont.invokeOnCancellation { t.cancel(); out.delete() }
        }
    }
}
