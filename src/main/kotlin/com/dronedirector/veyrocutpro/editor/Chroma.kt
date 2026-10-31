package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import java.nio.ByteBuffer

/** Colour sampling and bit depth of a video stream, read from its headers (SPS). */
data class ChromaInfo(
    /** 1 = 4:2:0, 2 = 4:2:2, 3 = 4:4:4, 0 = monochrome. */
    val chroma: Int,
    val bitDepth: Int,
) {
    val label: String get() = when (chroma) { 2 -> "4:2:2"; 3 -> "4:4:4"; 0 -> "4:0:0"; else -> "4:2:0" } + " ${bitDepth}-bit"
}

/**
 * 4:2:2 (and 4:4:4) footage — e.g. from Sony, Panasonic, Canon, Fujifilm, DJI and Blackmagic
 * cameras in 10-bit 4:2:2 H.264 / H.265. Many phone decoders only handle 4:2:0, so the editor
 * finds a decoder on this phone that can read the clip (hardware first, then Android's own
 * software decoder) and uses it for the preview copy and the export.
 */
object Chroma {
    private class Bits(private val b: ByteArray) {
        private var pos = 0
        fun bit(): Int { val v = (b[pos ushr 3].toInt() shr (7 - (pos and 7))) and 1; pos++; return v }
        fun bits(n: Int): Int { var v = 0; repeat(n) { v = (v shl 1) or bit() }; return v }
        fun skip(n: Int) { pos += n }
        fun ue(): Int { var z = 0; while (bit() == 0 && z < 31) z++; return ((1 shl z) - 1) + bits(z) }
    }

    /** NAL payload without emulation-prevention bytes (00 00 03 → 00 00). */
    private fun unescape(b: ByteArray, from: Int, to: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(to - from)
        var zeros = 0
        for (i in from until to) {
            val v = b[i].toInt() and 0xFF
            if (zeros >= 2 && v == 3) { zeros = 0; continue }
            out.write(v)
            zeros = if (v == 0) zeros + 1 else 0
        }
        return out.toByteArray()
    }

    /** NAL units in Annex-B data (start codes), or the whole buffer if there are none. */
    private fun nals(b: ByteArray): List<Pair<Int, Int>> {
        val starts = ArrayList<Int>()
        var i = 0
        while (i + 3 <= b.size) {
            if (b[i].toInt() == 0 && b[i + 1].toInt() == 0 && (b[i + 2].toInt() == 1 || (i + 3 < b.size && b[i + 2].toInt() == 0 && b[i + 3].toInt() == 1))) {
                val s = if (b[i + 2].toInt() == 1) i + 3 else i + 4
                starts += s; i = s
            } else i++
        }
        if (starts.isEmpty()) return listOf(0 to b.size)
        return starts.mapIndexed { k, s ->
            var e = if (k + 1 < starts.size) starts[k + 1] - 3 else b.size
            while (e > s && b[e - 1].toInt() == 0) e--
            s to e
        }
    }

    private fun avcSps(raw: ByteArray): ChromaInfo? {
        val r = Bits(raw)
        r.skip(8)                    // NAL header
        val profile = r.bits(8)
        r.skip(16)                   // constraint flags + level
        r.ue()                       // seq_parameter_set_id
        if (profile in setOf(100, 110, 122, 244, 44, 83, 86, 118, 128, 138, 139, 134, 135)) {
            val cf = r.ue()
            if (cf == 3) r.skip(1)
            val bd = r.ue() + 8
            return ChromaInfo(cf, bd)
        }
        return ChromaInfo(1, 8)
    }

    private fun hevcSps(raw: ByteArray): ChromaInfo? {
        val r = Bits(raw)
        r.skip(16)                   // NAL header
        r.skip(4)                    // vps id
        val maxSub = r.bits(3)
        r.skip(1)
        r.skip(88)                   // general profile / tier / level
        val subProfile = IntArray(maxSub); val subLevel = IntArray(maxSub)
        for (i in 0 until maxSub) { subProfile[i] = r.bit(); subLevel[i] = r.bit() }
        if (maxSub > 0) for (i in maxSub until 8) r.skip(2)
        for (i in 0 until maxSub) { if (subProfile[i] == 1) r.skip(88); if (subLevel[i] == 1) r.skip(8) }
        r.ue()                       // sps id
        val cf = r.ue()
        if (cf == 3) r.skip(1)
        r.ue(); r.ue()               // width, height
        if (r.bit() == 1) { r.ue(); r.ue(); r.ue(); r.ue() }   // conformance window
        val bd = r.ue() + 8
        return ChromaInfo(cf, bd)
    }

    /** Reads the sampling from a video track's codec headers; null if it can't tell. */
    fun of(format: MediaFormat): ChromaInfo? = try {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        val csd = format.getByteBuffer("csd-0")?.let { bb -> ByteArray(bb.remaining()).also { bb.duplicate().get(it) } }
        if (csd == null) null else {
            var found: ChromaInfo? = null
            for ((s, e) in nals(csd)) {
                if (e - s < 4) continue
                val raw = unescape(csd, s, e)
                when {
                    mime == MediaFormat.MIMETYPE_VIDEO_AVC && (raw[0].toInt() and 0x1F) == 7 -> { found = avcSps(raw); break }
                    mime == MediaFormat.MIMETYPE_VIDEO_HEVC && ((raw[0].toInt() shr 1) and 0x3F) == 33 -> { found = hevcSps(raw); break }
                }
            }
            found
        }
    } catch (_: Throwable) { null }

    /**
     * Finds a decoder on this phone that really decodes this clip (a few frames are tried).
     * Returns its name, or null if none can.
     */
    fun workingDecoder(context: Context, uri: Uri): String? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, uri, null)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true } ?: return null
            val fmt = ex.getTrackFormat(track)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return null
            val names = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
                .filter { !it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, true) } }
                // Hardware first; Android's software decoders (c2.android / OMX.google) last.
                .sortedBy { if (it.name.startsWith("c2.android") || it.name.startsWith("OMX.google")) 1 else 0 }
                .map { it.name }
            for (n in names) {
                ex.unselectTrack(track); ex.selectTrack(track); ex.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                if (tryDecode(ex, fmt, n)) return n
            }
            return null
        } catch (_: Throwable) {
            return null
        } finally {
            ex.release()
        }
    }

    private fun tryDecode(ex: MediaExtractor, fmt: MediaFormat, name: String): Boolean {
        var codec: MediaCodec? = null
        try {
            codec = MediaCodec.createByCodecName(name)
            codec.configure(fmt, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            val t0 = System.currentTimeMillis()
            var fed = 0
            while (System.currentTimeMillis() - t0 < 4000) {
                if (fed < 60) {
                    val i = codec.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val buf: ByteBuffer = codec.getInputBuffer(i) ?: return false
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); fed = 60 }
                        else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance(); fed++ }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 5_000)
                if (o >= 0) {
                    val ok = info.size > 0
                    codec.releaseOutputBuffer(o, false)
                    if (ok) return true
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return false
                }
            }
            return false
        } catch (_: Throwable) {
            return false
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
        }
    }

    /** Export / preview-copy loaders that decode with the named decoders. */
    @OptIn(UnstableApi::class)
    fun assetLoaders(context: Context, preferred: Collection<String>): androidx.media3.transformer.AssetLoader.Factory =
        androidx.media3.transformer.DefaultAssetLoaderFactory(
            context,
            androidx.media3.transformer.DefaultDecoderFactory.Builder(context)
                .setMediaCodecSelector(selector(preferred))
                .setEnableDecoderFallback(true)
                .build(),
            androidx.media3.common.util.Clock.DEFAULT,
            null,
        )

    /** Puts the named decoders first, so the player / export uses the one that works for 4:2:2. */
    @OptIn(UnstableApi::class)
    fun selector(preferred: Collection<String>): MediaCodecSelector = MediaCodecSelector { mime, secure, tunneling ->
        val all = MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling)
        all.sortedBy { if (it.name in preferred) 0 else 1 }
    }
}
