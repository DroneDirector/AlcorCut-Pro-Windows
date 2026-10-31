package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tan

/**
 * Integrated loudness (LUFS) as in ITU-R BS.1770 / EBU R128: K-weighting, 400 ms blocks with
 * 75 % overlap, absolute gate at −70 LUFS and relative gate 10 LU below. Used to bring an export
 * to a target loudness (Deliver → LOUDNESS).
 */
object Loudness {
    /** Common targets: YouTube / Spotify / Instagram ≈ −14, Apple ≈ −16, broadcast −23. */
    val TARGETS = listOf(-14f, -16f, -23f)

    private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        private var z1 = 0.0; private var z2 = 0.0
        fun step(x: Double): Double { val y = b0 * x + z1; z1 = b1 * x - a1 * y + z2; z2 = b2 * x - a2 * y; return y }
    }

    private fun kWeighting(fs: Double): Pair<Biquad, Biquad> {
        // Stage 1: high shelf (+4 dB above ~1.7 kHz).
        val f0 = 1681.974450955533; val g = 3.999843853973347; val q = 0.7071752369554196
        var k = tan(PI * f0 / fs)
        val vh = 10.0.pow(g / 20.0); val vb = vh.pow(0.4996667741545416)
        var a0 = 1.0 + k / q + k * k
        val shelf = Biquad((vh + vb * k / q + k * k) / a0, 2.0 * (k * k - vh) / a0, (vh - vb * k / q + k * k) / a0, 2.0 * (k * k - 1.0) / a0, (1.0 - k / q + k * k) / a0)
        // Stage 2: high pass at ~38 Hz.
        val f1 = 38.13547087602444; val q1 = 0.5003270373238773
        k = tan(PI * f1 / fs)
        a0 = 1.0 + k / q1 + k * k
        val hp = Biquad(1.0, -2.0, 1.0, 2.0 * (k * k - 1.0) / a0, (1.0 - k / q1 + k * k) / a0)
        return shelf to hp
    }

    /** Loudness of a sound file in LUFS, or null if it can't be read / is silent. Slow: call off the main thread. */
    fun measure(context: Context, uri: Uri): Float? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(context, uri, null)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            val fmt = ex.getTrackFormat(track)
            ex.selectTrack(track)
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0)
            codec.start()
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
            var filters = Array(channels) { kWeighting(rate.toDouble()) }
            // Sum of K-weighted squares per 100 ms step; blocks are 4 steps.
            val steps = ArrayList<Double>()
            var stepLen = rate / 10
            var acc = 0.0; var n = 0; var ch = 0
            val info = MediaCodec.BufferInfo()
            var inDone = false; var outDone = false
            while (!outDone) {
                if (!inDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val s = ex.readSampleData(buf, 0)
                        if (s < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { codec.queueInputBuffer(i, 0, s, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
                        filters = Array(channels) { kWeighting(rate.toDouble()) }
                        stepLen = rate / 10
                    }
                    o >= 0 -> {
                        val buf = codec.getOutputBuffer(o)
                        if (buf != null && info.size > 0) {
                            val sb = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            sb.position(info.offset / 2); sb.limit((info.offset + info.size) / 2)
                            while (sb.hasRemaining()) {
                                val x = sb.get() / 32768.0
                                // BS.1770 channel weights: 1.0 for L / R / C (surround channels are rare on phones).
                                val (f1, f2) = filters[ch]
                                val y = f2.step(f1.step(x))
                                acc += y * y
                                if (++ch == channels) {
                                    ch = 0
                                    if (++n >= stepLen) { steps += acc / n; acc = 0.0; n = 0 }
                                }
                            }
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    }
                }
            }
            if (steps.size < 4) return null
            val blocks = DoubleArray(steps.size - 3) { (steps[it] + steps[it + 1] + steps[it + 2] + steps[it + 3]) / 4.0 }
            fun lufs(z: Double) = -0.691 + 10.0 * log10(z.coerceAtLeast(1e-12))
            val abs = blocks.filter { lufs(it) > -70.0 }
            if (abs.isEmpty()) return null
            val rel = lufs(abs.average()) - 10.0
            val gated = abs.filter { lufs(it) > rel }
            if (gated.isEmpty()) return null
            return lufs(gated.average()).toFloat()
        } catch (_: Throwable) {
            return null
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            ex.release()
        }
    }

    /** Gain (linear) that brings [measured] to [target], limited to ±15 dB. */
    fun gainFor(measured: Float, target: Float): Float = 10f.pow(((target - measured).coerceIn(-15f, 15f)) / 20f)
}
