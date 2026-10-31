package com.dronedirector.veyrocutpro.editor

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lines up two recordings of the same moment (camera sound and an external recorder, or two
 * cameras) by matching their sound: the loudness envelopes are compared at every possible shift
 * (FFT cross-correlation) and the best match wins.
 */
object AudioSync {
    /** Result: [lagMs] = how much later the same sound happens in B than in A; [confidence] 0..1. */
    data class Match(val lagMs: Long, val confidence: Float)

    /** Uses waveform peaks ([Waveforms.BUCKET_MS] apart) of both recordings. */
    fun match(a: FloatArray, b: FloatArray): Match? {
        if (a.size < 50 || b.size < 50) return null
        val oa = onsets(a); val ob = onsets(b)
        val n = Integer.highestOneBit((oa.size + ob.size) * 2 - 1)
        val ar = DoubleArray(n); val ai = DoubleArray(n)
        val br = DoubleArray(n); val bi = DoubleArray(n)
        for (i in oa.indices) ar[i] = oa[i]
        for (i in ob.indices) br[i] = ob[i]
        fft(ar, ai, false); fft(br, bi, false)
        // corr = IFFT(conj(A) · B): peak at k means b[i + k] ≈ a[i].
        for (k in 0 until n) {
            val re = ar[k] * br[k] + ai[k] * bi[k]
            val im = ar[k] * bi[k] - ai[k] * br[k]
            ar[k] = re; ai[k] = im
        }
        fft(ar, ai, true)
        var best = 0; var bestV = -1e300; var sum = 0.0; var sum2 = 0.0
        for (k in 0 until n) {
            val v = ar[k]
            sum += v; sum2 += v * v
            if (v > bestV) { bestV = v; best = k }
        }
        val mean = sum / n
        val sd = sqrt(max(sum2 / n - mean * mean, 1e-18))
        // Sub-bucket peak position.
        val y0 = ar[(best - 1 + n) % n]; val y1 = ar[best]; val y2 = ar[(best + 1) % n]
        val den = y0 - 2 * y1 + y2
        val frac = if (den != 0.0) (0.5 * (y0 - y2) / den).coerceIn(-0.5, 0.5) else 0.0
        val lagBuckets = (if (best > n / 2) best - n else best) + frac
        val z = ((bestV - mean) / sd).toFloat()
        return Match((lagBuckets * Waveforms.BUCKET_MS).toLong(), ((z - 4f) / 12f).coerceIn(0f, 1f))
    }

    /** Rises in loudness (claps, words, hits): far more distinctive than the loudness itself. */
    private fun onsets(p: FloatArray): DoubleArray {
        val o = DoubleArray(p.size)
        for (i in 1 until p.size) o[i] = max(0.0, (p[i] - p[i - 1]).toDouble())
        val m = o.average()
        for (i in o.indices) o[i] -= m
        return o
    }

    /** In-place radix-2 FFT (inverse scales by 1/n). */
    private fun fft(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        val n = re.size
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
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val ncr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) for (i in 0 until n) { re[i] /= n; im[i] /= n }
    }
}
