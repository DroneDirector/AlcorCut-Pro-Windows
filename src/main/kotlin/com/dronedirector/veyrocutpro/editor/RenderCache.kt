package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
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
import java.io.File

/**
 * Render cache (Proxy page → RENDER CACHE): the heavy effects that make playback stutter — AI noise
 * reduction, background cut-out and AI depth effects — are rendered once into a preview file, so the
 * clip plays smoothly with them. The grade, crop and everything else stay live; the export always
 * renders from the original. The file covers the whole source clip, so trimming keeps it valid;
 * changing one of those effects makes a new one necessary.
 */
@UnstableApi
object RenderCache {
    private fun dir(c: Context) = MediaCatalog.sub(c, "rendercache")

    /** Clips with something worth caching. */
    fun heavy(c: Clip) = c.nested == null && (c.detail.aiDenoise > 0f || c.bg != null || (c.depth?.frames ?: 0) > 0)

    /** Key of the clip's media and the settings the cache bakes in. */
    fun key(c: Clip): String {
        val sig = listOf(c.detail.aiDenoise, c.bg, c.depth).toString().hashCode()
        return MediaId.of(c) + "_" + Integer.toHexString(sig)
    }

    private fun fileFor(ctx: Context, c: Clip) = File(dir(ctx), key(c).replace(Regex("[^A-Za-z0-9_]"), "_") + ".mp4")
        .also { MediaCatalog.claim(ctx, c.uri, it) }

    fun existing(ctx: Context, c: Clip): Uri? = fileFor(ctx, c).takeIf { it.exists() && it.length() > 1000 }?.let { Uri.fromFile(it) }

    fun clearAll(ctx: Context): Long { var n = 0L; dir(ctx).listFiles()?.forEach { n += it.length(); it.delete() }; return n }
    fun sizeOnDisk(ctx: Context): Long = dir(ctx).listFiles()?.sumOf { it.length() } ?: 0L

    /** Renders [c]'s cache; returns a function that cancels it. */
    fun make(ctx: Context, c: Clip, onProgress: (Int) -> Unit, onDone: (Uri?, String?) -> Unit): () -> Unit {
        val target = fileFor(ctx, c)
        val tmp = File(target.parentFile, target.name + ".part")
        tmp.delete()
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        // The effects work in source time: give them the whole clip at normal speed.
        val whole = c.copy(startMs = 0L, endMs = c.durationMs, speed = 1f, ramp = null)
        val fx = ArrayList<Effect>()
        if (c.detail.aiDenoise > 0f) fx += AiDenoiseEffect(ctx, c.detail.aiDenoise)
        bgEffectFor(ctx, whole, 0L)?.let { fx += it }
        depthEffectFor(ctx, whole, 0L)?.let { fx += it }
        val short = minOf(c.width, c.height).coerceAtLeast(2)
        if (short > 1080) fx += Presentation.createForHeight(((c.height * 1080f / short).toInt() / 2 * 2).coerceAtLeast(2))
        val transformer = Transformer.Builder(ctx)
            .also { b -> c.decoder?.let { b.setAssetLoaderFactory(Chroma.assetLoaders(ctx, listOf(it))) } }
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(DefaultEncoderFactory.Builder(ctx).setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(16_000_000).build()).setEnableFallback(true).build())
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (finished) return
                    finished = true
                    val ok = tmp.renameTo(target)
                    onDone(if (ok) Uri.fromFile(target) else null, if (ok) null else "Couldn't save the render cache")
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    if (finished) return
                    finished = true; tmp.delete()
                    onDone(null, "Render cache failed: ${exportException.errorCodeName}")
                }
            })
            .build()
        val item = EditedMediaItem.Builder(MediaItem.fromUri(c.uri)).setEffects(Effects(emptyList(), fx)).build()
        val comp = Composition.Builder(EditedMediaItemSequence.Builder(listOf(item)).build())
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
        try { transformer.start(comp, tmp.path) } catch (t: Throwable) { finished = true; onDone(null, "Render cache failed: ${t.message}"); return {} }
        fun poll() {
            if (finished) return
            val ph = ProgressHolder()
            if (transformer.getProgress(ph) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(ph.progress)
            handler.postDelayed({ poll() }, 500)
        }
        poll()
        return { if (!finished) { finished = true; transformer.cancel(); tmp.delete() } }
    }
}
