package com.dronedirector.veyrocutpro.editor

import kotlin.math.abs

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
