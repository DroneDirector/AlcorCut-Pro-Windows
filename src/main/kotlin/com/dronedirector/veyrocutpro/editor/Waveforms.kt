package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * Real audio waveforms for the timeline: the sound is decoded once and reduced to one peak
 * (0..1) per [BUCKET_MS]; results are kept in memory and on disk.
 */
object Waveforms {
    const val BUCKET_MS = 20L
    private val mem = HashMap<String, FloatArray>()

    fun cached(uri: Uri): FloatArray? = synchronized(mem) { mem[uri.toString()] }

    /** Decodes (or loads the saved copy). Slow: call off the main thread. */
    fun load(context: Context, uri: Uri): FloatArray? {
        cached(uri)?.let { return it }
        val file = File(MediaCatalog.sub(context, "waves"), uri.toString().hashCode().toUInt().toString(16) + ".wav1")
            .also { MediaCatalog.claim(context, uri, it) }
        if (file.exists()) {
            try {
                val bytes = file.readBytes()
                val out = FloatArray(bytes.size) { (bytes[it].toInt() and 0xFF) / 255f }
                synchronized(mem) { mem[uri.toString()] = out }
                return out
            } catch (_: Throwable) {}
        }
        val peaks = decode(context, uri) ?: return null
        try { file.writeBytes(ByteArray(peaks.size) { (peaks[it] * 255f).toInt().coerceIn(0, 255).toByte() }) } catch (_: Throwable) {}
        synchronized(mem) { mem[uri.toString()] = peaks }
        return peaks
    }

    private fun decode(context: Context, uri: Uri): FloatArray? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(context, uri, null)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            val fmt = ex.getTrackFormat(track)
            ex.selectTrack(track)
            val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
            val buckets = ArrayList<Float>(((durUs / 1000) / BUCKET_MS + 16).toInt().coerceIn(16, 2_000_000))
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0)
            codec.start()
            var sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var samplesPerBucket = (sampleRate * BUCKET_MS / 1000).toInt().coerceAtLeast(1)
            var count = 0; var peak = 0
            val info = MediaCodec.BufferInfo()
            var inDone = false; var outDone = false
            while (!outDone) {
                if (!inDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        samplesPerBucket = (sampleRate * BUCKET_MS / 1000).toInt().coerceAtLeast(1)
                    }
                    o >= 0 -> {
                        val buf = codec.getOutputBuffer(o)
                        if (buf != null && info.size > 0) {
                            val sb = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            sb.position(info.offset / 2); sb.limit((info.offset + info.size) / 2)
                            var ch = 0
                            while (sb.hasRemaining()) {
                                val v = abs(sb.get().toInt())
                                if (v > peak) peak = v
                                if (++ch == channels) {
                                    ch = 0
                                    if (++count == samplesPerBucket) { buckets += peak / 32768f; count = 0; peak = 0 }
                                }
                            }
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    }
                }
            }
            if (count > 0) buckets += peak / 32768f
            // Normalise so quiet recordings are still readable.
            var mx = 0.05f
            for (b in buckets) mx = max(mx, b)
            return FloatArray(buckets.size) { (buckets[it] / mx).coerceIn(0f, 1f) }
        } catch (_: Throwable) {
            return null
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            ex.release()
        }
    }
}

/** Simple beat finder working on the waveform peaks: tempo by autocorrelation, then the strongest phase. */
object Beats {
    /** Beat times in ms from the start of the file. */
    fun detect(peaks: FloatArray): List<Long> {
        val n = peaks.size
        if (n < 200) return emptyList()
        // Onset strength: rises in loudness, lightly smoothed.
        val o = FloatArray(n)
        for (i in 1 until n) o[i] = (peaks[i] - peaks[i - 1]).coerceAtLeast(0f)
        val sm = FloatArray(n)
        for (i in 1 until n - 1) sm[i] = (o[i - 1] + 2 * o[i] + o[i + 1]) / 4f
        // Tempo: 60–180 BPM → lags of 17..50 buckets of 20 ms.
        var bestLag = 25; var best = -1f
        for (lag in 17..50) {
            var acc = 0f
            for (i in lag until n) acc += sm[i] * sm[i - lag]
            // Gentle preference for 90–140 BPM.
            val bpm = 60_000f / (lag * Waveforms.BUCKET_MS)
            val w = 1f - kotlin.math.abs(bpm - 115f) / 400f
            if (acc * w > best) { best = acc * w; bestLag = lag }
        }
        var bestPhase = 0; var bestSum = -1f
        for (ph in 0 until bestLag) {
            var s = 0f; var i = ph
            while (i < n) { s += sm[i]; i += bestLag }
            if (s > bestSum) { bestSum = s; bestPhase = ph }
        }
        val out = ArrayList<Long>()
        var i = bestPhase
        while (i < n) {
            // Snap each beat to the local onset peak.
            var m = i
            for (j in (i - 2).coerceAtLeast(0)..(i + 2).coerceAtMost(n - 1)) if (sm[j] > sm[m]) m = j
            out += m * Waveforms.BUCKET_MS
            i += bestLag
        }
        return out
    }
}

/** Finds the pauses in a clip's sound so they can be cut out (Edit → Remove silences). */
object Silence {
    /**
     * Parts of [startMs]..[endMs] (source time) to keep. Quiet stretches (peaks below [thresholdDb]
     * relative to the loudest moment in the file) longer than [minSilenceMs] are dropped, leaving
     * [padMs] of quiet on each side so words aren't clipped.
     */
    fun keeps(peaks: FloatArray, startMs: Long, endMs: Long, thresholdDb: Float, minSilenceMs: Long, padMs: Long = 120L): List<LongRange> {
        val b = Waveforms.BUCKET_MS
        val thr = Math.pow(10.0, thresholdDb / 20.0).toFloat()
        val i0 = (startMs / b).toInt().coerceIn(0, peaks.size)
        val i1 = (endMs / b).toInt().coerceIn(i0, peaks.size)
        val quiet = mutableListOf<LongRange>()
        var runStart = -1
        for (i in i0..i1) {
            val q = i < i1 && peaks[i] < thr
            if (q && runStart < 0) runStart = i
            if (!q && runStart >= 0) {
                val a = runStart * b; val e = i * b
                if (e - a >= minSilenceMs) quiet += (a + padMs)..(e - padMs)
                runStart = -1
            }
        }
        if (quiet.isEmpty()) return listOf(startMs..endMs)
        val out = mutableListOf<LongRange>()
        var cur = startMs
        for (q in quiet) {
            val s = q.first.coerceIn(startMs, endMs); val e = q.last.coerceIn(startMs, endMs)
            if (s - cur >= 200) out += cur..s
            cur = maxOf(cur, e)
        }
        if (endMs - cur >= 200) out += cur..endMs
        return out
    }
}
