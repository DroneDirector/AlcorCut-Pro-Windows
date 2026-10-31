package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Capture → title overlay: burns a title (lower third, centre or top) into a finished recording,
 * with a fade / slide in and out, using the same text renderer as the video editor's layers.
 */
@OptIn(UnstableApi::class)
object TitleBurn {
    suspend fun apply(context: Context, uri: Uri, text: String, pos: Int, seconds: Int): Boolean {
        val tmp = File(context.cacheDir, "title_${System.currentTimeMillis()}.mp4")
        try {
            val m = MediaMetadataRetriever()
            val (w, h, bitrate) = try {
                m.setDataSource(context, uri)
                val vw = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
                val vh = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
                val rot = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val br = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
                if (rot == 90 || rot == 270) Triple(vh, vw, br) else Triple(vw, vh, br)
            } finally { m.release() }
            val layer = Layer(
                kind = LayerKind.TEXT, text = text.trim(), size = 0.06f,
                x = 0.5f, y = when (pos) { 1 -> 0.5f; 2 -> 0.12f; else -> 0.84f },
                box = pos == 0, shadow = true,
                startMs = 0L, endMs = if (seconds > 0) seconds * 1000L else 0L, wholeVideo = seconds <= 0,
                animIn = if (pos == 0) LayerAnim.SLIDE_UP else LayerAnim.FADE, animOut = LayerAnim.FADE, animMs = 450L,
            )
            val bmp = withContext(Dispatchers.Default) { LayerRenderer.render(context, layer, w, h) } ?: return false
            val overlay = LayerOverlay(bmp, layer) as androidx.media3.effect.TextureOverlay
            val item = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                .setEffects(Effects(emptyList(), listOf(androidx.media3.effect.OverlayEffect(listOf(overlay)))))
                .build()
            val ok = withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val enc = if (bitrate > 0) VideoEncoderSettings.Builder().setBitrate(bitrate).build() else VideoEncoderSettings.DEFAULT
                    val t = Transformer.Builder(context)
                        .setEncoderFactory(DefaultEncoderFactory.Builder(context).setRequestedVideoEncoderSettings(enc).setEnableFallback(true).build())
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) { if (cont.isActive) cont.resume(true) }
                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { if (cont.isActive) cont.resume(false) }
                        })
                        .build()
                    cont.invokeOnCancellation { try { t.cancel() } catch (_: Throwable) {} }
                    t.start(item, tmp.path)
                }
            }
            if (!ok || !tmp.exists() || tmp.length() < 1000) return false
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")?.use { o -> tmp.inputStream().use { it.copyTo(o, 1 shl 16) } }
            }
            return true
        } catch (_: Throwable) {
            return false
        } finally {
            tmp.delete()
        }
    }
}
