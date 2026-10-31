package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Music analysis for the visualizer: the sound is decoded once and turned into a spectrum every [HOP_MS]
 * ([BANDS] bands from 30 Hz to 16 kHz, spaced like hearing), plus a beat strength from the bass kicks.
 * Kept in memory and on disk. Own code (own FFT).
 */
object Spectrum {
    const val HOP_MS = 40L
    const val BANDS = 64
    /** One frame = BANDS band levels + 1 beat value, each 0..1. */
    const val STRIDE = BANDS + 1
    private const val N = 2048
    private val mem = HashMap<String, FloatArray>()

    fun cached(uri: Uri): FloatArray? = synchronized(mem) { mem[uri.toString()] }

    /** Analyses (or loads the saved result). Slow the first time: call off the main thread. */
    @Synchronized fun load(context: Context, uri: Uri): FloatArray? {
        cached(uri)?.let { return it }
        val file = File(MediaCatalog.sub(context, "spectrum"), uri.toString().hashCode().toUInt().toString(16) + ".spc2")
            .also { MediaCatalog.claim(context, uri, it) }
        if (file.exists()) try {
            val b = file.readBytes()
            val out = FloatArray(b.size) { (b[it].toInt() and 0xFF) / 255f }
            synchronized(mem) { mem[uri.toString()] = out }
            return out
        } catch (_: Throwable) {}
        val res = analyse(context, uri) ?: return null
        try { file.writeBytes(ByteArray(res.size) { (res[it] * 255f).toInt().coerceIn(0, 255).toByte() }) } catch (_: Throwable) {}
        synchronized(mem) { mem[uri.toString()] = res }
        return res
    }

    /** Band levels and beat at song time [ms] (null outside the song or before the analysis is ready). */
    fun frame(data: FloatArray, ms: Long): Int? {
        if (ms < 0) return null
        val i = (ms / HOP_MS).toInt()
        return if ((i + 1) * STRIDE <= data.size) i * STRIDE else null
    }

    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val t = re[i]; re[i] = re[j]; re[j] = t; val u = im[i]; im[i] = im[j]; im[j] = u }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val a = i + k; val b = a + len / 2
                    val xr = re[b] * cr - im[b] * ci; val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr; im[b] = im[a] - xi; re[a] += xr; im[a] += xi
                    val ncr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun analyse(context: Context, uri: Uri): FloatArray? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(context, uri, null)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            val fmt = ex.getTrackFormat(track)
            ex.selectTrack(track)
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0); codec.start()
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            // Some decoders give 32-bit float samples instead of 16-bit.
            var floatPcm = false
            val ring = FloatArray(N); var w = 0; var total = 0L
            val win = FloatArray(N) { (0.5 - 0.5 * cos(2.0 * PI * it / (N - 1))).toFloat() }
            val re = FloatArray(N); val im = FloatArray(N)
            val frames = ArrayList<FloatArray>()
            var nextAt = 0L
            fun edges(): IntArray = IntArray(BANDS + 1) { b ->
                val f = 30.0 * (16000.0 / 30.0).pow(b.toDouble() / BANDS)
                (f / rate * N).toInt().coerceIn(1, N / 2 - 1)
            }
            var edge = edges()
            fun emit() {
                for (i in 0 until N) { re[i] = ring[(w + i) % N] * win[i]; im[i] = 0f }
                fft(re, im)
                val bands = FloatArray(BANDS + 1)
                for (b in 0 until BANDS) {
                    val a = edge[b]; val z = max(edge[b + 1], a + 1)
                    var s = 0f
                    for (k in a until z) s = max(s, sqrt(re[k] * re[k] + im[k] * im[k]))
                    bands[b] = s
                }
                frames += bands
            }
            var mix = 0f; var ch = 0
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
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT); edge = edges()
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
                    }
                    o >= 0 -> {
                        val buf = codec.getOutputBuffer(o)
                        if (buf != null && info.size > 0) {
                            fun take(x: Float) {
                                mix += x
                                if (++ch >= channels) {
                                    ring[w] = mix / channels; w = (w + 1) % N; mix = 0f; ch = 0; total++
                                    // A frame every HOP_MS, centred on its time.
                                    if (total >= nextAt * rate / 1000 + N / 2) { emit(); nextAt += HOP_MS }
                                }
                            }
                            if (floatPcm) {
                                val fb = buf.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                                fb.position(info.offset / 4); fb.limit((info.offset + info.size) / 4)
                                while (fb.hasRemaining()) take(fb.get())
                            } else {
                                val sb = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                sb.position(info.offset / 2); sb.limit((info.offset + info.size) / 2)
                                while (sb.hasRemaining()) take(sb.get() / 32768f)
                            }
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    }
                }
            }
            if (frames.isEmpty()) return null
            // Loudness in dB per band. Each band is spread over its own range in this song (quiet 15 % to loud 99 %),
            // and lifted when it is louder than a moment ago, so the bars really move with the music.
            val nF = frames.size
            val db = Array(nF) { t -> FloatArray(BANDS) { b -> (20.0 * kotlin.math.log10(frames[t][b] + 1e-6)).toFloat() } }
            val lo = FloatArray(BANDS); val hi = FloatArray(BANDS)
            val col = FloatArray(nF)
            for (b in 0 until BANDS) {
                for (t in 0 until nF) col[t] = db[t][b]
                val sorted = col.sortedArray()
                lo[b] = sorted[(nF * 0.15f).toInt().coerceIn(0, nF - 1)]
                hi[b] = max(sorted[(nF * 0.99f).toInt().coerceIn(0, nF - 1)], lo[b] + 12f)
            }
            val out = FloatArray(nF * STRIDE)
            val prev = FloatArray(BANDS)
            val avg = FloatArray(BANDS) { b -> db[0][b] }
            // Beat: rise in the bass (kick) energy, against its recent average.
            val bassHist = ArrayDeque<Float>()
            var beatPrev = 0f
            var bassPrev = 0f
            for (t in frames.indices) {
                for (b in 0 until BANDS) {
                    val base = ((db[t][b] - lo[b]) / (hi[b] - lo[b])).coerceIn(0f, 1f)
                    val rise = ((db[t][b] - avg[b]) / 9f).coerceIn(0f, 1f)
                    avg[b] = avg[b] * 0.9f + db[t][b] * 0.1f
                    val v = (base * 0.75f + rise * 0.45f).coerceIn(0f, 1f)
                    // Fast rise, slower fall: how professional meters move.
                    val s = if (v > prev[b]) v else prev[b] * 0.70f + v * 0.30f
                    prev[b] = s
                    out[t * STRIDE + b] = s
                }
                var bass = 0f
                for (k in 0 until 3 * BANDS / 16) bass += frames[t][k]
                val flux = max(0f, bass - bassPrev); bassPrev = bass
                bassHist.addLast(flux); if (bassHist.size > 25) bassHist.removeFirst()
                val bassAvg = bassHist.average().toFloat().coerceAtLeast(1e-6f)
                val hit = ((flux / bassAvg - 1.2f) / 2.5f).coerceIn(0f, 1f)
                val beat = max(hit, beatPrev * 0.72f)
                beatPrev = beat
                out[t * STRIDE + BANDS] = beat
            }
            return out
        } catch (_: Throwable) {
            return null
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            ex.release()
        }
    }
}
