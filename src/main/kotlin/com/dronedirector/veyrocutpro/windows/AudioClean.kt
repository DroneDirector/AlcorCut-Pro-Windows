package com.dronedirector.veyrocutpro.camera

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Voice noise reduction while recording: the steady background (hiss, hum, fans, traffic) is measured
 * in each frequency band and turned down, so voices stand out. Spectral gating with a running noise
 * estimate; [strength] 1 = light (up to −12 dB), 2 = strong (up to −24 dB). One channel. Own code.
 */
class NoiseReducer(private val strength: Int) {
    private val n = 1024
    private val hop = n / 4
    private val win = FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / n)).toFloat() }
    private val inBuf = FloatArray(n)
    private val outBuf = FloatArray(n)
    private var inFill = n - hop
    private val pending = FloatArray(n); private var pr = 0; private var pw = 0; private var pn = 0
    private val re = FloatArray(n); private val im = FloatArray(n)
    private val bins = n / 2 + 1
    private val smooth = FloatArray(bins)
    private val noiseAvg = FloatArray(bins) { 1e-9f }
    private val gainPrev = FloatArray(bins) { 1f }
    private var frames = 0
    private val over = if (strength >= 2) 3.0f else 1.8f
    private val floor = if (strength >= 2) 0.063f else 0.25f

    /** Cleans [x] (in place, same length; the sound comes out 768 samples later, which the start absorbs). */
    fun process(x: FloatArray, off: Int, len: Int, stride: Int) {
        for (i in 0 until len) {
            val idx = off + i * stride
            inBuf[inFill++] = x[idx]
            if (inFill == n) { frame(); System.arraycopy(inBuf, hop, inBuf, 0, n - hop); inFill = n - hop }
            x[idx] = if (pn > 0) { val v = pending[pr]; pr = (pr + 1) % n; pn--; v } else 0f
        }
    }

    private fun frame() {
        for (i in 0 until n) { re[i] = inBuf[i] * win[i]; im[i] = 0f }
        fft(re, im, false)
        frames++
        for (k in 0 until bins) {
            val p = re[k] * re[k] + im[k] * im[k]
            smooth[k] = if (frames == 1) p else smooth[k] * 0.8f + p * 0.2f
            // The average noise: learned from the first moments, then only while the band sounds like
            // its background (no voice in it), and slowly upwards when the background itself gets louder.
            noiseAvg[k] = when {
                frames == 1 -> p
                frames <= 40 -> noiseAvg[k] + (p - noiseAvg[k]) / frames
                smooth[k] < 0.5f * noiseAvg[k] -> noiseAvg[k] * 0.85f + smooth[k] * 0.15f
                smooth[k] < 2.5f * noiseAvg[k] -> noiseAvg[k] * 0.98f + p * 0.02f
                else -> noiseAvg[k] * 1.0005f
            }
            var g = 1f - over * noiseAvg[k] / (p + 1e-12f)
            g = g.coerceIn(floor, 1f)
            g = 0.6f * gainPrev[k] + 0.4f * g
            gainPrev[k] = g
            re[k] *= g; im[k] *= g
            if (k in 1 until n / 2) { re[n - k] = re[k]; im[n - k] = -im[k] }
        }
        fft(re, im, true)
        // Overlap-add (Hann in and out at 75 % overlap sums to 1.5).
        for (i in 0 until n) outBuf[i] += re[i] * win[i] / 1.5f
        for (i in 0 until hop) { pending[pw] = outBuf[i]; pw = (pw + 1) % n; pn++ }
        System.arraycopy(outBuf, hop, outBuf, 0, n - hop)
        java.util.Arrays.fill(outBuf, n - hop, n, 0f)
    }

    private fun fft(re: FloatArray, im: FloatArray, inverse: Boolean) {
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = 2 * PI / len * (if (inverse) 1 else -1)
            val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val a = i + k; val b = a + len / 2
                    val tr = re[b] * cr - im[b] * ci; val ti = re[b] * ci + im[b] * cr
                    re[b] = re[a] - tr; im[b] = im[a] - ti; re[a] += tr; im[a] += ti
                    val ncr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) for (i in 0 until n) { re[i] /= n; im[i] /= n }
    }
}

/**
 * NTSC rates: the sound stretched by 1001 / 1000 so it stays with the picture at 23.976 / 29.97 /
 * 59.94 fps (0.1 % slower, like the phone does after recording). Stereo, interleaved. Own code.
 */
class NtscStretch {
    private var pos = 0.0
    private var lastL = 0f; private var lastR = 0f
    /** [n] stereo frames of [x] in, the stretched frames out. */
    fun run(x: FloatArray, n: Int): FloatArray {
        val out = ArrayList<Float>(n * 2 + 8)
        val step = 1000.0 / 1001.0
        while (pos < n) {
            val i = pos.toInt(); val f = (pos - i).toFloat()
            val l0 = if (i == 0) lastL else x[(i - 1) * 2]; val r0 = if (i == 0) lastR else x[(i - 1) * 2 + 1]
            val l1 = x[i * 2]; val r1 = x[i * 2 + 1]
            out += l0 + (l1 - l0) * f; out += r0 + (r1 - r0) * f
            pos += step
        }
        pos -= n
        if (n > 0) { lastL = x[(n - 1) * 2]; lastR = x[(n - 1) * 2 + 1] }
        return out.toFloatArray()
    }
}
