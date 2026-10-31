package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
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

/** What a clip's video stream is (used to decide whether it needs a preview proxy). */
data class StreamInfo(val mime: String, val hdr: Boolean, val tenBit: Boolean, val chroma: ChromaInfo? = null)

object StreamProbe {
    fun probe(context: Context, uri: Uri): StreamInfo = try {
        val ex = MediaExtractor()
        ex.setDataSource(context, uri, null)
        var info = StreamInfo("", false, false)
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (!mime.startsWith("video/")) continue
            val transfer = if (f.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) f.getInteger(MediaFormat.KEY_COLOR_TRANSFER) else 0
            val profile = if (f.containsKey(MediaFormat.KEY_PROFILE)) f.getInteger(MediaFormat.KEY_PROFILE) else 0
            val hevc10 = mime == MediaFormat.MIMETYPE_VIDEO_HEVC && profile in setOf(
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10,
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus,
            )
            val hdr = transfer == MediaFormat.COLOR_TRANSFER_ST2084 || transfer == MediaFormat.COLOR_TRANSFER_HLG ||
                mime == MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION
            info = StreamInfo(mime, hdr, hevc10 || hdr, Chroma.of(f))
            break
        }
        ex.release()
        info
    } catch (_: Throwable) {
        StreamInfo("", false, false)
    }
}

/**
 * Stable ID for a piece of media, used to link a proxy to its original. Made from the source
 * itself (address, length and picture size), so it stays the same between sessions, projects
 * and app restarts, and every clip cut from the same file shares it.
 */
object MediaId {
    fun of(c: Clip): String = of(c.uri, c.durationMs, c.width, c.height)
    fun of(uri: Uri, durationMs: Long, width: Int, height: Int): String {
        val d = java.security.MessageDigest.getInstance("SHA-1").digest("$uri|$durationMs|${width}x$height".toByteArray())
        return d.take(8).joinToString("") { "%02x".format(it) }
    }
}

/** Proxy (preview media) quality: the short side of the proxy picture. */
enum class ProxyQuality(val height: Int, val label: String, val bitrate: Int) {
    P360(360, "360p", 1_500_000), P540(540, "540p", 3_000_000), P720(720, "720p", 5_000_000), P1080(1080, "1080p", 10_000_000);
    companion object { fun of(h: Int) = entries.firstOrNull { it.height == h } ?: P720 }
}

/**
 * Proxy settings. [enabled] is the on / off switch: on = media in the Proxy section plays from its
 * proxy; off = everything plays from the original files. Export always uses the originals.
 */
data class ProxySettings(val enabled: Boolean = true, val quality: ProxyQuality = ProxyQuality.P720, val hevc: Boolean = false) {
    companion object {
        fun load(context: Context): ProxySettings {
            val p = context.getSharedPreferences("procam", Context.MODE_PRIVATE)
            return ProxySettings(
                enabled = p.getBoolean("proxy_on", true),
                quality = ProxyQuality.of(p.getInt("proxy_q", 720)),
                hevc = p.getBoolean("proxy_hevc", false),
            )
        }

        fun save(context: Context, s: ProxySettings) {
            context.getSharedPreferences("procam", Context.MODE_PRIVATE).edit()
                .putBoolean("proxy_on", s.enabled).putInt("proxy_q", s.quality.height).putBoolean("proxy_hevc", s.hevc).apply()
        }
    }
}

/**
 * Proxy media: a lighter (lower-resolution, SDR) copy of a clip used only for playback and the
 * timeline in the editor. Each proxy is linked to its original by [MediaId] and is checked to
 * match the original's length, frame rate, orientation and audio before it is used; anything
 * unfinished or mismatched is never used (the original plays instead). Export always uses the
 * original file. Proxies are cached and reused between sessions.
 */
@UnstableApi
object ProxyMaker {
    // Kept in the open video catalog, with the clip (see MediaCatalog).
    private fun dir(context: Context) = MediaCatalog.sub(context, "proxies")

    fun fileFor(context: Context, c: Clip, q: ProxyQuality, hevc: Boolean): File =
        File(dir(context), "proxy_${MediaId.of(c)}_${q.height}${if (hevc) "_hevc" else ""}.mp4").also {
            MediaCatalog.claim(context, c.uri, it); MediaCatalog.claim(context, c.uri, File(it.parentFile, it.name + ".json"))
        }
    private fun sideFor(f: File) = File(f.parentFile, f.name + ".json")

    /** What a proxy must match in its original. */
    private data class Facts(val durationMs: Long, val fps: Float, val rotation: Int, val audio: Boolean, val portrait: Boolean)

    private fun facts(context: Context, uri: Uri): Facts? = try {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val audio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val portrait = if (rot == 90 || rot == 270) w > h else h > w
            Facts(dur, frameRate(context, uri), rot, audio, portrait)
        } finally { r.release() }
    } catch (_: Throwable) { null }

    private fun frameRate(context: Context, uri: Uri): Float = try {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, uri, null)
            var fps = 0f
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") != true) continue
                if (f.containsKey(MediaFormat.KEY_FRAME_RATE)) fps = try { f.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() } catch (_: Throwable) { f.getFloat(MediaFormat.KEY_FRAME_RATE) }
                break
            }
            fps
        } finally { ex.release() }
    } catch (_: Throwable) { 0f }

    /** Null if the proxy matches its original, else why not. */
    private fun mismatch(o: Facts, p: Facts): String? {
        val tol = maxOf(250L, (o.durationMs * 0.002).toLong())
        if (kotlin.math.abs(o.durationMs - p.durationMs) > tol) return "length differs (${p.durationMs} vs ${o.durationMs} ms)"
        if (o.audio && !p.audio) return "audio missing"
        if (o.portrait != p.portrait) return "orientation differs"
        if (o.fps > 1f && p.fps > 1f && kotlin.math.abs(o.fps - p.fps) > maxOf(0.6f, o.fps * 0.02f)) return "frame rate differs (${p.fps} vs ${o.fps} fps)"
        return null
    }

    /** A finished, checked proxy at this quality, or null. */
    fun existing(context: Context, c: Clip, q: ProxyQuality, hevc: Boolean): Uri? {
        val f = fileFor(context, c, q, hevc)
        if (!f.exists() || f.length() < 1024) return null
        val side = sideFor(f)
        val ok = try { org.json.JSONObject(side.readText()).let { it.optString("mediaId") == MediaId.of(c) && it.optBoolean("ok") } } catch (_: Throwable) { false }
        return if (ok) Uri.fromFile(f) else null
    }

    /** Any finished proxy of this media (another quality), to use while the new one is made. */
    fun anyExisting(context: Context, c: Clip, hevc: Boolean): Pair<ProxyQuality, Uri>? {
        for (q in ProxyQuality.entries.reversed()) for (hv in listOf(hevc, !hevc)) existing(context, c, q, hv)?.let { return q to it }
        return null
    }

    /** Which quality a ready proxy file is (for display), from its name. */
    fun qualityOf(uri: Uri): ProxyQuality? = uri.lastPathSegment?.let { n -> ProxyQuality.entries.firstOrNull { n.contains("_${it.height}") } }

    fun clearAll(context: Context): Long {
        var freed = 0L
        dir(context).listFiles()?.forEach { freed += it.length(); it.delete() }
        return freed
    }

    fun sizeOnDisk(context: Context): Long = dir(context).listFiles()?.sumOf { it.length() } ?: 0L

    /** Makes a checked proxy in the background; start on the main thread. Returns a cancel function. */
    fun make(
        context: Context,
        c: Clip,
        q: ProxyQuality,
        hevc: Boolean,
        onProgress: (Int) -> Unit,
        onDone: (Uri?, String?) -> Unit,
    ): () -> Unit {
        val target = fileFor(context, c, q, hevc)
        val tmp = File(target.parentFile, target.name + ".part")
        tmp.delete(); target.delete(); sideFor(target).delete()
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        // Keep the shape; scale so the short side is the proxy quality (never up).
        val short = minOf(c.width, c.height).coerceAtLeast(2)
        val scale = minOf(1f, q.height.toFloat() / short)
        val h = ((c.height * scale).toInt() / 2 * 2).coerceAtLeast(2)
        val transformer = Transformer.Builder(context)
            .also { b -> c.decoder?.let { b.setAssetLoaderFactory(Chroma.assetLoaders(context, listOf(it))) } }
            .setVideoMimeType(if (hevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(q.bitrate).build())
                    .setEnableFallback(true)
                    .build()
            )
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (finished) return
                    finished = true
                    // Check the proxy against its original off the main thread, then publish it.
                    Thread {
                        val err = try {
                            val o = facts(context, c.uri); val p = facts(context, Uri.fromFile(tmp))
                            when {
                                p == null -> "the proxy can't be read"
                                o == null -> null
                                else -> mismatch(o, p)
                            }?.also { tmp.delete() } ?: run {
                                if (!tmp.renameTo(target)) "couldn't save the proxy" else {
                                    sideFor(target).writeText(org.json.JSONObject().apply {
                                        put("mediaId", MediaId.of(c)); put("source", c.uri.toString()); put("quality", q.height)
                                        put("durationMs", o?.durationMs ?: c.durationMs); put("fps", (o?.fps ?: 0f).toDouble())
                                        put("rotation", o?.rotation ?: 0); put("audio", o?.audio ?: false); put("ok", true)
                                    }.toString())
                                    null
                                }
                            }
                        } catch (t: Throwable) { tmp.delete(); t.message ?: "check failed" }
                        handler.post { if (err == null) onDone(Uri.fromFile(target), null) else onDone(null, "Proxy not used: $err") }
                    }.start()
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    if (finished) return
                    finished = true
                    tmp.delete()
                    onDone(null, "Proxy failed: ${exportException.errorCodeName}")
                }
            })
            .build()
        // Every frame is kept (same timestamps and frame rate as the original); only the size changes.
        val item = EditedMediaItem.Builder(MediaItem.fromUri(c.uri))
            .setEffects(Effects(emptyList(), if (scale < 1f) listOf(Presentation.createForHeight(h)) else emptyList()))
            .build()
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(listOf(item)).build())
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
        try {
            transformer.start(composition, tmp.path)
        } catch (t: Throwable) {
            finished = true
            onDone(null, "Proxy failed: ${t.message}")
            return {}
        }
        fun poll() {
            if (finished) return
            val ph = ProgressHolder()
            if (transformer.getProgress(ph) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(ph.progress)
            handler.postDelayed({ poll() }, 400)
        }
        poll()
        return {
            if (!finished) {
                finished = true
                transformer.cancel()
                tmp.delete()
            }
        }
    }
}
