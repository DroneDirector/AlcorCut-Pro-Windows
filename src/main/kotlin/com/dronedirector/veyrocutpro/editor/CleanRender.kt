package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * AI noise reduction done once per clip and reused: the whole source clip is cleaned by the phone's AI
 * at its full size and kept in the video catalog (with the clip). Exports and re-exports read the
 * cleaned clip instead of running the AI again on every frame. Own code.
 *
 * The cleaned clip is stored at a very high bitrate (HEVC where the phone has it), so it looks the same as
 * the AI's output; a video file can't be kept losslessly at a practical size. Changing the strength
 * makes a new one; the sound is carried over untouched.
 */
@UnstableApi
object CleanRender {
    private fun dir(c: Context) = MediaCatalog.sub(c, "aiclean")

    private fun key(c: Clip) = MediaId.of(c) + "_" + "%.2f".format(java.util.Locale.US, c.detail.aiDenoise).replace('.', '_')

    private fun fileFor(ctx: Context, c: Clip) = File(dir(ctx), key(c) + ".mp4").also { MediaCatalog.claim(ctx, c.uri, it) }

    /** The cleaned clip to read instead of the original, when it's ready and fits this export. */
    fun sourceFor(ctx: Context, c: Clip, s: DeliverSettings): Uri? {
        if (c.detail.aiDenoise <= 0f || s.keepHdr || s.audioOnly || c.nested != null) return null
        return fileFor(ctx, c).takeIf { it.exists() && it.length() > 1000 }?.let { Uri.fromFile(it) }
    }

    /** Clips of [clips] still to be cleaned (one per source and strength). */
    fun missing(ctx: Context, clips: List<Clip>, s: DeliverSettings): List<Clip> =
        if (s.keepHdr || s.audioOnly) emptyList()
        else clips.filter { it.detail.aiDenoise > 0f && it.nested == null && sourceFor(ctx, it, s) == null }.distinctBy { key(it) }

    /** Cleans [c] (the whole source); returns false if it couldn't. [onProgress] 0–100. */
    suspend fun make(ctx: Context, c: Clip, onProgress: (Int) -> Unit): Boolean = suspendCancellableCoroutine { cont ->
        val target = fileFor(ctx, c)
        val tmp = File(target.parentFile, target.name + ".part")
        tmp.delete()
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        fun done(ok: Boolean) { if (finished) return; finished = true; if (cont.isActive) cont.resume(ok) }
        // Bitrate high enough to keep the AI's result intact (about 0.35 bits per pixel, up to 160 Mbit/s).
        val fps = 30
        val bitrate = (c.width.toLong() * c.height * fps * 35 / 100).coerceIn(20_000_000L, 160_000_000L).toInt()
        val transformer = Transformer.Builder(ctx)
            .also { b -> c.decoder?.let { b.setAssetLoaderFactory(Chroma.assetLoaders(ctx, listOf(it))) } }
            .setVideoMimeType(MimeTypes.VIDEO_H265)
            .setEncoderFactory(DefaultEncoderFactory.Builder(ctx)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
                .setEnableFallback(true).build())
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    done(tmp.renameTo(target))
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    tmp.delete(); done(false)
                }
            })
            .build()
        val item = EditedMediaItem.Builder(MediaItem.fromUri(c.uri))
            .setEffects(Effects(emptyList(), listOf(AiDenoiseEffect(ctx, c.detail.aiDenoise))))
            .build()
        val comp = Composition.Builder(EditedMediaItemSequence.Builder(listOf(item)).build())
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
        try { transformer.start(comp, tmp.path) } catch (_: Throwable) { done(false); return@suspendCancellableCoroutine }
        fun poll() {
            if (finished) return
            val ph = ProgressHolder()
            if (transformer.getProgress(ph) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(ph.progress)
            handler.postDelayed({ poll() }, 500)
        }
        poll()
        cont.invokeOnCancellation { handler.post { if (!finished) { finished = true; try { transformer.cancel() } catch (_: Throwable) {}; tmp.delete() } } }
    }
}
