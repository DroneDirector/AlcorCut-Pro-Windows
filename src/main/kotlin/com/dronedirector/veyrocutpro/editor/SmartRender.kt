package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Smart render (Deliver → FAST COPY): a cut-only edit is written by copying the original video and
 * sound straight from the clips, without decoding or re-encoding — no quality loss and very fast.
 * Own code on Android's MediaExtractor / MediaMuxer.
 *
 * Only possible when every clip has the same picture and sound format and nothing changes the
 * picture or sound. Each clip starts on the key frame at or just before its in point, because a
 * copied clip can only begin on a key frame (phones write one about every second).
 */
object SmartRender {
    class Probe(val ok: Boolean, val why: String? = null)

    private fun track(ex: MediaExtractor, prefix: String): Int {
        for (i in 0 until ex.trackCount) if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true) return i
        return -1
    }

    private fun csd(f: MediaFormat, k: String): ByteArray? = if (f.containsKey(k)) f.getByteBuffer(k)?.let { b -> ByteArray(b.remaining()).also { b.duplicate().get(it) } } else null

    private fun sameFormat(a: MediaFormat, b: MediaFormat, video: Boolean): Boolean {
        if (a.getString(MediaFormat.KEY_MIME) != b.getString(MediaFormat.KEY_MIME)) return false
        if (video) {
            if (a.getInteger(MediaFormat.KEY_WIDTH) != b.getInteger(MediaFormat.KEY_WIDTH) || a.getInteger(MediaFormat.KEY_HEIGHT) != b.getInteger(MediaFormat.KEY_HEIGHT)) return false
        } else {
            if (a.getInteger(MediaFormat.KEY_SAMPLE_RATE) != b.getInteger(MediaFormat.KEY_SAMPLE_RATE) || a.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != b.getInteger(MediaFormat.KEY_CHANNEL_COUNT)) return false
        }
        return csd(a, "csd-0").contentEquals(csd(b, "csd-0")) && csd(a, "csd-1").contentEquals(csd(b, "csd-1"))
    }

    private fun rotation(context: Context, c: Clip): Int = try {
        MediaMetadataRetriever().run {
            try { setDataSource(context, c.uri); extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0 } finally { release() }
        }
    } catch (_: Throwable) { 0 }

    /** True when frames come out of order (B-frames): those clips can't be cut by copying. */
    private fun reordered(ex: MediaExtractor, vt: Int, fromUs: Long): Boolean {
        ex.selectTrack(vt)
        ex.seekTo(fromUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        var last = Long.MIN_VALUE; var n = 0
        while (n < 90) {
            val t = ex.sampleTime
            if (t < 0) break
            if (t < last) { ex.unselectTrack(vt); return true }
            last = t; n++
            ex.advance()
        }
        ex.unselectTrack(vt)
        return false
    }

    /** Checks the media itself: same formats on every clip, same rotation, no B-frames. */
    fun probeMedia(context: Context, clips: List<Clip>, withAudio: Boolean): Probe {
        var vRef: MediaFormat? = null; var aRef: MediaFormat? = null; var rot: Int? = null
        for (c in clips) {
            val ex = MediaExtractor()
            try {
                ex.setDataSource(context, c.uri, null)
                val vt = track(ex, "video/")
                if (vt < 0) return Probe(false, "a clip has no video track")
                val vf = ex.getTrackFormat(vt)
                if (vRef == null) vRef = vf else if (!sameFormat(vRef, vf, true)) return Probe(false, "the clips have different picture formats")
                if (withAudio) {
                    val at = track(ex, "audio/")
                    if (at < 0) return Probe(false, "a clip has no sound")
                    val af = ex.getTrackFormat(at)
                    if (aRef == null) aRef = af else if (!sameFormat(aRef, af, false)) return Probe(false, "the clips have different sound formats")
                }
                val r = rotation(context, c)
                if (rot == null) rot = r else if (rot != r) return Probe(false, "the clips are rotated differently")
                if (reordered(ex, vt, c.startMs * 1000)) return Probe(false, "the clips use B-frames")
            } catch (t: Throwable) {
                return Probe(false, "a clip couldn't be read")
            } finally { ex.release() }
        }
        return Probe(true)
    }

    /**
     * Writes the clips one after another into [out]. Returns null when done, or why it failed.
     * [progress] gets 0..100; [cancelled] stops it.
     */
    fun copy(context: Context, clips: List<Clip>, withAudio: Boolean, out: File, progress: (Int) -> Unit, cancelled: () -> Boolean): String? {
        var muxer: MediaMuxer? = null
        try {
            muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer.setOrientationHint(rotation(context, clips.first()))
            var mv = -1; var ma = -1
            val buf = ByteBuffer.allocateDirect(8 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            var baseUs = 0L
            val totalUs = clips.sumOf { (it.endMs - it.startMs) * 1000 }.coerceAtLeast(1)
            var doneUs = 0L
            for (c in clips) {
                val ex = MediaExtractor()
                try {
                    ex.setDataSource(context, c.uri, null)
                    val vt = track(ex, "video/")
                    val at = if (withAudio) track(ex, "audio/") else -1
                    if (mv < 0) {
                        mv = muxer.addTrack(ex.getTrackFormat(vt))
                        if (at >= 0) ma = muxer.addTrack(ex.getTrackFormat(at))
                        muxer.start()
                    }
                    // Find the key frame at or before the in point: the copy starts there.
                    ex.selectTrack(vt)
                    ex.seekTo(c.startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    val startUs = ex.sampleTime.coerceAtLeast(0)
                    val endUs = c.endMs * 1000
                    var lastV = baseUs
                    while (true) {
                        if (cancelled()) return "Stopped"
                        val t = ex.sampleTime
                        if (t < 0 || t >= endUs) break
                        if (ex.sampleTrackIndex == vt) {
                            val size = ex.readSampleData(buf, 0)
                            if (size < 0) break
                            info.set(0, size, baseUs + (t - startUs), if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                            muxer.writeSampleData(mv, buf, info)
                            lastV = info.presentationTimeUs
                            progress(((doneUs + (t - startUs)) * 100 / totalUs).toInt().coerceIn(0, 99))
                        }
                        ex.advance()
                    }
                    ex.unselectTrack(vt)
                    var clipEndUs = lastV
                    if (at >= 0 && ma >= 0) {
                        ex.selectTrack(at)
                        ex.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                        while (true) {
                            if (cancelled()) return "Stopped"
                            val t = ex.sampleTime
                            if (t < 0 || t >= endUs) break
                            if (t >= startUs) {
                                val size = ex.readSampleData(buf, 0)
                                if (size < 0) break
                                info.set(0, size, baseUs + (t - startUs), MediaCodec.BUFFER_FLAG_KEY_FRAME)
                                muxer.writeSampleData(ma, buf, info)
                                clipEndUs = maxOf(clipEndUs, info.presentationTimeUs)
                            }
                            ex.advance()
                        }
                        ex.unselectTrack(at)
                    }
                    // Next clip starts one frame after this one's last frame.
                    val frameUs = try { 1_000_000L / ex.getTrackFormat(vt).getInteger(MediaFormat.KEY_FRAME_RATE).coerceAtLeast(1) } catch (_: Throwable) { 33_333L }
                    baseUs = maxOf(lastV + frameUs, clipEndUs + 1)
                    doneUs += endUs - c.startMs * 1000
                } finally { ex.release() }
            }
            muxer.stop()
            progress(100)
            return null
        } catch (t: Throwable) {
            return "Fast copy failed: ${t.message ?: t.javaClass.simpleName}"
        } finally {
            try { muxer?.release() } catch (_: Throwable) {}
        }
    }
}
