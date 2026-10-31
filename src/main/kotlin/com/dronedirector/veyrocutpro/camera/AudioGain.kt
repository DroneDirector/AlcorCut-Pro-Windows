package com.dronedirector.veyrocutpro.camera



/**
 * Voice noise reduction for recorded sound: spectral subtraction with a noise floor that follows the
 * quietest moments (hiss, hum, room and traffic noise), 1024-point frames with 50 % overlap.
 * Output is delayed by [delay] samples per channel; the caller drops that much from the start.
 */
class NoiseReducer(private val channels: Int, strength: Int) {
    private val n = 1024
    private val hop = n / 2
    val delay = n - hop
    private val over = if (strength >= 2) 2.6f else 1.6f
    private val floor = if (strength >= 2) 0.12f else 0.25f
    private val win = FloatArray(n) { kotlin.math.sqrt(0.5f - 0.5f * kotlin.math.cos(2.0 * Math.PI * it / n).toFloat()) }
    private val hist = Array(channels) { FloatArray(n) }
    private val acc = Array(channels) { FloatArray(n) }
    private val noise = Array(channels) { FloatArray(n / 2 + 1) }
    private val gPrev = Array(channels) { FloatArray(n / 2 + 1) { 1f } }
    private var started = BooleanArray(channels)
    private val fresh = Array(channels) { FloatArray(hop) }
    private var fill = 0
    private val re = FloatArray(n); private val im = FloatArray(n)

    /** Interleaved samples in (−1..1) → interleaved samples out (same count, delayed). */
    fun process(input: FloatArray): FloatArray {
        val frames = input.size / channels
        val out = FloatArray(frames * channels)
        for (f in 0 until frames) {
            for (c in 0 until channels) fresh[c][fill] = input[f * channels + c]
            fill++
            if (fill == hop) {
                for (c in 0 until channels) frame(c)
                fill = 0
            }
            // Emit from the overlap-add buffer, one sample per channel per input frame.
            for (c in 0 until channels) out[f * channels + c] = acc[c][fill]
        }
        return out
    }

    /** The last [delay] samples per channel still inside the filter (call once at the end). */
    fun flush(): FloatArray = process(FloatArray(delay * channels))

    private fun frame(c: Int) {
        val h = hist[c]
        System.arraycopy(h, hop, h, 0, n - hop)
        System.arraycopy(fresh[c], 0, h, n - hop, hop)
        for (i in 0 until n) { re[i] = h[i] * win[i]; im[i] = 0f }
        fft(re, im, false)
        val nz = noise[c]; val gp = gPrev[c]
        for (k in 0..n / 2) {
            val p = re[k] * re[k] + im[k] * im[k] + 1e-12f
            if (!started[c]) nz[k] = p
            // Noise floor: falls quickly to quiet moments, rises slowly (≈ +3 dB/s).
            nz[k] = if (p < nz[k]) 0.7f * nz[k] + 0.3f * p else nz[k] * 1.007f
            var g = kotlin.math.sqrt((1f - over * nz[k] / p).coerceAtLeast(floor * floor))
            // Smooth over time (fast attack for speech, slower release) against "musical noise".
            g = if (g > gp[k]) 0.3f * gp[k] + 0.7f * g else 0.65f * gp[k] + 0.35f * g
            gp[k] = g
            re[k] *= g; im[k] *= g
            if (k in 1 until n / 2) { re[n - k] = re[k]; im[n - k] = -im[k] }
        }
        started[c] = true
        fft(re, im, true)
        val a = acc[c]
        // Shift out the samples already emitted, then overlap-add the new frame.
        System.arraycopy(a, hop, a, 0, n - hop)
        java.util.Arrays.fill(a, n - hop, n, 0f)
        for (i in 0 until n) a[i] += re[i] * win[i]
    }

    /** In-place radix-2 FFT; [inverse] scales by 1/n. */
    private fun fft(re: FloatArray, im: FloatArray, inverse: Boolean) {
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
            val ang = 2.0 * Math.PI / len * (if (inverse) 1 else -1)
            val wr = kotlin.math.cos(ang).toFloat(); val wi = kotlin.math.sin(ang).toFloat()
            var i = 0
            while (i < n) {
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
        if (inverse) for (i in 0 until n) { re[i] /= n; im[i] /= n }
    }
}
