package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Long exports in parts: the timeline is rendered in parts of a couple of minutes (cut only where two
 * clips simply cut, never inside a transition), each part on its own, then the parts are joined into
 * one video without re-encoding. If a part fails it's done again on its own — not the whole film. Own code.
 */
@UnstableApi
object ChunkedExport {
    /** Long enough to be worth splitting. */
    const val MIN_TOTAL_MS = 4 * 60_000L
    private const val TARGET_MS = 150_000L
    private const val MIN_PART_MS = 60_000L

    /** Where to split [clips] (timeline ms, from 0 to the end), or just [0, end] when it can't or needn't be. */
    fun bounds(clips: List<Clip>): List<Long> {
        val total = timelineLength(clips)
        if (total < MIN_TOTAL_MS) return listOf(0L, total)
        val st = clipStarts(clips)
        // Only at plain cuts: no picture or sound transition across them.
        val cuts = (1 until clips.size).filter { i ->
            clips[i - 1].transition.type == TransitionType.NONE &&
                AudioXfade.forCut(clips, i - 1).let { it.first == null && it.second == null }
        }.map { st[it] }
        val out = mutableListOf(0L)
        var last = 0L
        for (c in cuts) {
            if (c - last >= TARGET_MS && total - c >= MIN_PART_MS) { out += c; last = c }
        }
        out += total
        return out
    }

    /** Joins [parts] (same size and format) into [dest] without re-encoding; re-encodes only if they don't match. */
    suspend fun join(context: Context, parts: List<File>, dest: File, bitrate: Int): Boolean {
        if (run(context, parts, dest, transmux = true, bitrate)) return true
        return run(context, parts, dest, transmux = false, bitrate)
    }

    private suspend fun run(context: Context, parts: List<File>, dest: File, transmux: Boolean, bitrate: Int): Boolean = suspendCancellableCoroutine { cont ->
        dest.delete()
        var done = false
        fun finish(ok: Boolean) { if (done) return; done = true; if (cont.isActive) cont.resume(ok) }
        val items = parts.map { EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(it))).build() }
        val comp = Composition.Builder(EditedMediaItemSequence.Builder(items).build())
            .setTransmuxAudio(transmux).setTransmuxVideo(transmux).build()
        val t = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(if (bitrate > 0) VideoEncoderSettings.Builder().setBitrate(bitrate).build() else VideoEncoderSettings.DEFAULT)
                .setEnableFallback(true).build())
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) = finish(dest.length() > 0)
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { dest.delete(); finish(false) }
            })
            .build()
        try { t.start(comp, dest.path) } catch (_: Throwable) { finish(false) }
        cont.invokeOnCancellation { android.os.Handler(android.os.Looper.getMainLooper()).post { try { t.cancel() } catch (_: Throwable) {}; dest.delete() } }
    }
}
