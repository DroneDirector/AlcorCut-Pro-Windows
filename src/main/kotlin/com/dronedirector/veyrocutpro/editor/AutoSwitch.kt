package com.dronedirector.veyrocutpro.editor

import kotlin.math.log10
import kotlin.math.max

/**
 * Automatic podcast / interview switching: cuts to the angle whose own microphone hears its
 * speaker loudest, like an operator following the conversation. Each angle's level is measured
 * against its own range (so a louder mic doesn't win all the time), a cut needs the new speaker to
 * lead for a moment (no flicker on "mm-hm"), shots last at least [minShotMs], and an optional
 * wide angle covers cross-talk, pauses and very long answers.
 */
object AutoSwitch {
    data class Options(val wide: Int = -1, val minShotMs: Long = 2000, val reactMs: Long = 600, val maxShotMs: Long = 20_000, val wideBreakMs: Long = 4000)

    private const val STEP_MS = 100L

    /**
     * [peaks] = each angle's waveform peaks ([Waveforms.BUCKET_MS] apart, in its own time); angle
     * time = master time + [off]. Returns the cuts (master time, angle), the first at 0.
     */
    fun cuts(peaks: List<FloatArray>, off: LongArray, masterLenMs: Long, o: Options): List<Pair<Long, Int>> {
        val n = peaks.size
        val steps = (masterLenMs / STEP_MS).toInt().coerceAtLeast(1)
        val b = Waveforms.BUCKET_MS
        // Level per angle per step: loudest peak in a 300 ms window, in dB.
        val lv = Array(n) { k ->
            val p = peaks[k]
            FloatArray(steps) { s ->
                val t = s * STEP_MS + off[k]
                val i0 = ((t - 150) / b).toInt(); val i1 = ((t + 150) / b).toInt()
                var m = 0f
                var any = false
                for (i in i0..i1) if (i in p.indices) { any = true; if (p[i] > m) m = p[i] }
                if (!any) Float.NaN else (20f * log10(max(m, 1f / 255f)))
            }
        }
        // Each angle's own range: noise floor (20th percentile) to speech (90th percentile).
        val norm = Array(n) { k ->
            val v = lv[k].filter { !it.isNaN() }.sorted()
            if (v.isEmpty()) return@Array FloatArray(steps) { Float.NaN }
            val floor = v[(v.size * 0.2).toInt().coerceIn(0, v.lastIndex)]
            val top = v[(v.size * 0.9).toInt().coerceIn(0, v.lastIndex)]
            val span = (top - floor).coerceAtLeast(6f)
            FloatArray(steps) { s -> val x = lv[k][s]; if (x.isNaN()) Float.NaN else (x - floor) / span }
        }
        val speakers = (0 until n).filter { it != o.wide }
        // Who leads each step (-1 = nobody clearly: silence or cross-talk).
        val lead = IntArray(steps) { s ->
            var best = -1; var bv = -9f; var second = -9f
            for (k in speakers) {
                val x = norm[k][s]; if (x.isNaN()) continue
                if (x > bv) { second = bv; bv = x; best = k } else if (x > second) second = x
            }
            if (best < 0 || bv < 0.45f) -1 else if (speakers.size > 1 && bv - second < 0.12f) -1 else best
        }
        val fallback = if (o.wide in 0 until n) o.wide else speakers.firstOrNull() ?: 0
        val out = ArrayList<Pair<Long, Int>>()
        // Start on the first clear speaker (or the wide).
        var cur = lead.firstOrNull { it >= 0 }?.takeIf { o.wide !in 0 until n } ?: fallback
        out += 0L to cur
        var shotStart = 0L
        var cand = -2; var candSince = 0L
        val reactSteps = (o.reactMs / STEP_MS).toInt().coerceAtLeast(1)
        var s = 0
        while (s < steps) {
            val t = s * STEP_MS
            val l = lead[s]
            val want = if (l >= 0) l else if (o.wide in 0 until n) o.wide else cur
            if (want != cand) { cand = want; candSince = t }
            val held = (t - candSince) / STEP_MS >= (if (want == o.wide) reactSteps * 3 else reactSteps)
            if (want != cur && held && t - shotStart >= o.minShotMs) {
                // Cut a little before the new speaker started (as an editor would), never before the last cut.
                val at = (candSince - 200).coerceAtLeast(shotStart + o.minShotMs / 2)
                out += at to want; cur = want; shotStart = at
            } else if (o.wide in 0 until n && cur != o.wide && t - shotStart >= o.maxShotMs) {
                // A long answer: a short look at the wide, then back.
                out += t to o.wide
                val back = t + o.wideBreakMs
                if (back < masterLenMs - 1000) out += back to cur
                shotStart = back
                s = (back / STEP_MS).toInt(); continue
            }
            s++
        }
        return out.sortedBy { it.first }.fold(ArrayList()) { acc, c -> if (acc.isEmpty() || acc.last().second != c.second) acc.add(c); acc }
    }
}
