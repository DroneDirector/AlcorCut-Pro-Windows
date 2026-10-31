package com.dronedirector.veyrocutpro.editor

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Audio repair written for AlcorCut Pro (no third-party code): de-click and de-reverb.
 * Both take interleaved samples (−1..1) and return the same number of samples, [delay] frames late.
 */

/**
 * De-click: finds short clicks and crackles (a sudden jump far above the sound's normal "roughness")
 * and redraws those few milliseconds smoothly from the sound on either side.
 */
class DeClicker(private val channels: Int, private val rate: Int) {
    private val block = 4096
    private val pad = 256
    /** One block plus the look-ahead: output sample k is input sample k − delay. */
    val delay = block + pad

    private inner class Chan {
        val buf = FloatArray(block + 2 * pad)   // [pad history | block | pad look-ahead]
        var fill = pad
        val outQ = ArrayDeque<Float>()
        fun push(v: Float) {
            buf[fill++] = v
            if (fill == buf.size) {
                repair(buf)
                for (i in pad until pad + block) outQ.addLast(buf[i])
                // Slide: keep the last 2·pad samples as history + look-ahead.
                System.arraycopy(buf, block, buf, 0, 2 * pad)
                fill = 2 * pad
            }
        }
    }

    private fun repair(x: FloatArray) {
        val n = x.size
        val d = FloatArray(n)
        for (i in 2 until n) d[i] = x[i] - 2 * x[i - 1] + x[i - 2]
        // Local roughness: RMS of the 2nd difference over ~10 ms.
        val w = (rate / 100).coerceIn(64, 1024)
        val sq = DoubleArray(n + 1)
        for (i in 0 until n) sq[i + 1] = sq[i] + d[i].toDouble() * d[i]
        val maxLen = (rate * 0.002).toInt().coerceAtLeast(4)
        var i = pad
        while (i < n - pad) {
            val a = (i - w).coerceAtLeast(0); val b = (i + w).coerceAtMost(n)
            val rms = sqrt((sq[b] - sq[a]) / (b - a)).toFloat()
            if (abs(d[i]) > 9f * rms + 0.02f) {
                // The click's extent: until the roughness settles again.
                var e = i
                while (e < n - pad && e - i < maxLen && abs(d[e]) > 3f * rms) e++
                if (e - i < maxLen) {
                    val s0 = (i - 4).coerceAtLeast(2); val e0 = (e + 4).coerceAtMost(n - 3)
                    // Cubic Hermite between the clean samples on each side, matching their slopes.
                    val y0 = x[s0]; val y1 = x[e0]
                    val m0 = x[s0] - x[s0 - 1]; val m1 = x[e0 + 1] - x[e0]
                    val len = (e0 - s0).toFloat()
                    for (k in s0 + 1 until e0) {
                        val t = (k - s0) / len
                        val t2 = t * t; val t3 = t2 * t
                        x[k] = (2 * t3 - 3 * t2 + 1) * y0 + (t3 - 2 * t2 + t) * m0 * len + (-2 * t3 + 3 * t2) * y1 + (t3 - t2) * m1 * len
                    }
                }
                i = e + 1
            } else i++
        }
    }

    private val chans = Array(channels) { Chan() }
    private var primed = 0

    fun process(input: FloatArray): FloatArray {
        val frames = input.size / channels
        val out = FloatArray(frames * channels)
        for (f in 0 until frames) {
            for (c in 0 until channels) chans[c].push(input[f * channels + c])
            if (primed < delay) { primed++; continue }
            for (c in 0 until channels) out[f * channels + c] = chans[c].outQ.removeFirstOrNull() ?: 0f
        }
        return out
    }
}

/**
 * De-reverb: takes the room's echo tail down. The late reverb in each frequency is predicted from
 * what was heard a moment earlier, fading as a room with the given reverb time would (statistical
 * late-reverb model), and that part is subtracted.
 */
class DeReverb(private val channels: Int, private val rate: Int, private val amount: Float) {
    private val n = 1024
    private val hop = 256
    val delay = n
    private val win = FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / n)).toFloat() }
    // RT60 0.3 … 1.2 s from the amount; the late part starts 50 ms after the direct sound.
    private val rt60 = 0.3f + amount.coerceIn(0f, 1f) * 0.9f
    private val lateFrames = ((0.05f * rate) / hop).toInt().coerceAtLeast(1)
    private val decay = exp(-2f * (3f * ln(10f) / rt60) * lateFrames * hop / rate)
    private val bins = n / 2 + 1

    private inner class Chan {
        val inBuf = FloatArray(n); var count = 0
        val outAcc = FloatArray(n)
        val outQ = ArrayDeque<Float>()
        val hist = Array(lateFrames) { FloatArray(bins) }
        var hi = 0
        val gainS = FloatArray(bins) { 1f }
        val re = FloatArray(n); val im = FloatArray(n)

        fun push(v: Float) {
            // Shift-in buffer: process every hop samples.
            System.arraycopy(inBuf, 1, inBuf, 0, n - 1); inBuf[n - 1] = v
            count++
            // Output sample k is input sample k (the first window covers samples 0 … n−1).
            if (count % hop == 0 && count >= n) frame()
        }

        fun frame() {
            for (i in 0 until n) { re[i] = inBuf[i] * win[i]; im[i] = 0f }
            fft(re, im, false)
            val past = hist[hi]
            for (k in 0 until bins) {
                val p = re[k] * re[k] + im[k] * im[k]
                val late = decay * past[k]
                var g = 1f - (0.6f + amount) * late / (p + 1e-9f)
                g = g.coerceIn(0.12f + 0.25f * (1f - amount), 1f)
                // Smooth the gain a little over time (less "musical noise").
                gainS[k] = gainS[k] * 0.4f + g * 0.6f
                past[k] = p
                re[k] *= gainS[k]; im[k] *= gainS[k]
                if (k in 1 until n / 2) { re[n - k] = re[k]; im[n - k] = -im[k] }
            }
            hi = (hi + 1) % lateFrames
            fft(re, im, true)
            // Overlap-add (Hann² at 75 % overlap sums to 1.5).
            for (i in 0 until n) outAcc[i] += re[i] * win[i] / 1.5f
            for (i in 0 until hop) outQ.addLast(outAcc[i])
            System.arraycopy(outAcc, hop, outAcc, 0, n - hop)
            java.util.Arrays.fill(outAcc, n - hop, n, 0f)
        }
    }

    private fun fft(re: FloatArray, im: FloatArray, inverse: Boolean) {
        val m = re.size
        var j = 0
        for (i in 1 until m) {
            var bit = m shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= m) {
            val ang = 2 * PI / len * (if (inverse) 1 else -1)
            val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < m) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val ar = re[i + k]; val ai = im[i + k]
                    val br = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val bi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ar + br; im[i + k] = ai + bi
                    re[i + k + len / 2] = ar - br; im[i + k + len / 2] = ai - bi
                    val ncr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) for (i in 0 until m) { re[i] /= m; im[i] /= m }
    }

    private val chans = Array(channels) { Chan() }
    private var primed = 0

    fun process(input: FloatArray): FloatArray {
        val frames = input.size / channels
        val out = FloatArray(frames * channels)
        for (f in 0 until frames) {
            for (c in 0 until channels) chans[c].push(input[f * channels + c])
            if (primed < delay) { primed++; continue }
            for (c in 0 until channels) out[f * channels + c] = chans[c].outQ.removeFirstOrNull() ?: 0f
        }
        return out
    }
}
