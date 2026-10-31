package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Freeze frames and reversed clips, made as new video files kept with the app. */
object VideoTools {
    class Stopped : Exception()
    @Volatile var stop = false

    private fun dir(context: Context) = File(context.filesDir, "edits").apply { mkdirs() }

    /** The exact frame at [srcMs] (not just the nearest keyframe), at most [maxPx] on the long side. */
    fun exactFrame(context: Context, uri: Uri, srcMs: Long, maxPx: Int = 1920): Bitmap? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            frameFrom(mmr, srcMs, maxPx)
        } catch (_: Throwable) { null } finally { mmr.release() }
    }

    private fun frameFrom(mmr: MediaMetadataRetriever, srcMs: Long, maxPx: Int): Bitmap? {
        val opt = MediaMetadataRetriever.OPTION_CLOSEST
        val b = if (android.os.Build.VERSION.SDK_INT >= 27) {
            var w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: maxPx
            var h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: maxPx
            val rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            val f = min(1f, maxPx.toFloat() / max(w, h))
            mmr.getScaledFrameAtTime(srcMs * 1000, opt, (w * f).toInt().coerceAtLeast(2), (h * f).toInt().coerceAtLeast(2))
        } else mmr.getFrameAtTime(srcMs * 1000, opt)
        return b?.let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false) }
    }

    /** A still clip of [durMs] showing the frame at [srcMs] of [c]. */
    fun freeze(context: Context, c: Clip, srcMs: Long, durMs: Long): Clip? {
        val bmp = exactFrame(context, c.uri, srcMs) ?: return null
        val w = bmp.width / 2 * 2; val h = bmp.height / 2 * 2
        val out = File(dir(context), "freeze_${System.currentTimeMillis()}.mp4")
        val fps = 30
        val count = (durMs * fps / 1000).toInt().coerceAtLeast(2)
        encode(w, h, fps, count, out, { bmp }, {})
        return c.copy(
            id = java.util.UUID.randomUUID().toString(), uri = Uri.fromFile(out), name = "Freeze · ${c.name}",
            durationMs = count * 1000L / fps, width = w, height = h, startMs = 0, endMs = count * 1000L / fps,
            speed = 1f, ramp = null, rampName = null, volKeys = null, muted = true, stab = Stabilize(), reframe = c.reframe.copy(animate = false),
            mime = "video/avc", hdr = false, tenBit = false,
        )
    }

    /** The trimmed part of [c] played backwards (no sound), at up to 1920 px. Slow: runs a few frames per second. */
    fun reverse(context: Context, c: Clip, onProgress: (Int) -> Unit): Clip? {
        stop = false
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, c.uri)
            val first = frameFrom(mmr, c.endMs - 1, 1920) ?: return null
            val w = first.width / 2 * 2; val h = first.height / 2 * 2
            val fps = 30
            val len = (c.endMs - c.startMs).coerceAtMost(60_000)
            val count = (len * fps / 1000).toInt().coerceAtLeast(2)
            val out = File(dir(context), "reverse_${System.currentTimeMillis()}.mp4")
            var last: Bitmap = first
            encode(w, h, fps, count, out, { i ->
                if (stop) throw Stopped()
                val t = c.endMs - 1 - i * 1000L / fps
                frameFrom(mmr, t.coerceAtLeast(c.startMs), 1920)?.let { last = it }
                last
            }, onProgress)
            val dur = count * 1000L / fps
            return c.copy(
                id = java.util.UUID.randomUUID().toString(), uri = Uri.fromFile(out), name = "Reverse · ${c.name}",
                durationMs = dur, width = w, height = h, startMs = 0, endMs = dur, ramp = null, rampName = null, volKeys = null,
                muted = true, stab = Stabilize(), mime = "video/avc", hdr = false, tenBit = false,
            )
        } finally { mmr.release() }
    }

    /** H.264 file from [count] frames supplied by [frame] (ARGB bitmaps of any size are scaled to w×h). */
    private fun encode(w: Int, h: Int, fps: Int, count: Int, out: File, frame: (Int) -> Bitmap, onProgress: (Int) -> Unit) {
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, (w.toLong() * h * fps / 6).toInt().coerceIn(2_000_000, 40_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        val info = MediaCodec.BufferInfo()
        val argb = IntArray(w * h)
        var sent = 0
        var done = false
        try {
            while (!done) {
                if (sent <= count) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        if (sent == count) {
                            codec.queueInputBuffer(i, 0, 0, sent * 1_000_000L / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            var b = frame(sent)
                            if (b.width != w || b.height != h) b = Bitmap.createScaledBitmap(b, w, h, true)
                            b.getPixels(argb, 0, w, 0, 0, w, h)
                            val img = codec.getInputImage(i)!!
                            writeYuv(img, argb, w, h)
                            codec.queueInputBuffer(i, 0, w * h * 3 / 2, sent * 1_000_000L / fps, 0)
                            onProgress(sent * 100 / count)
                        }
                        sent++
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(codec.outputFormat); muxer.start() }
                    o >= 0 -> {
                        val buf = codec.getOutputBuffer(o)
                        if (buf != null && info.size > 0 && track >= 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buf, info)
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done = true
                    }
                }
            }
        } catch (t: Throwable) {
            out.delete()
            throw t
        } finally {
            try { codec.stop() } catch (_: Throwable) {}
            codec.release()
            try { if (track >= 0) muxer.stop() } catch (_: Throwable) {}
            muxer.release()
        }
    }

    /** ARGB → the encoder's YUV 4:2:0 layout (planar or semi-planar), BT.601 limited range. */
    private fun writeYuv(img: android.media.Image, argb: IntArray, w: Int, h: Int) {
        val yP = img.planes[0]; val uP = img.planes[1]; val vP = img.planes[2]
        val yb = yP.buffer; val ub = uP.buffer; val vb = vP.buffer
        val yRow = yP.rowStride; val yPix = yP.pixelStride
        val uRow = uP.rowStride; val uPix = uP.pixelStride
        val vRow = vP.rowStride; val vPix = vP.pixelStride
        for (y in 0 until h) {
            val ro = y * w
            for (x in 0 until w) {
                val c = argb[ro + x]
                val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
                yb.put(y * yRow + x * yPix, (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255).toByte())
                if (y and 1 == 0 && x and 1 == 0) {
                    val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    ub.put((y / 2) * uRow + (x / 2) * uPix, u.toByte())
                    vb.put((y / 2) * vRow + (x / 2) * vPix, v.toByte())
                }
            }
        }
    }
}
