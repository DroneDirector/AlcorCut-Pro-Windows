package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.util.UUID
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Auto highlights: finds the liveliest moments of long clips — bursts of sound (laughter, cheering,
 * a raised voice), movement in the picture, and lively speech — and builds a short reel from them,
 * cut on word boundaries or pauses so nobody is cut off mid-word. Own code, all on the phone.
 */
object Highlights {
    private const val BIN_MS = 500L

    private class Bin(val clip: Int, val srcMs: Long, var motion: Float = 0f, var loud: Float = 0f, var burst: Float = 0f, var speech: Float = 0f, var score: Float = 0f, var sharp: Float = 0f, var face: Float = 0f)

    /**
     * A highlight reel about [targetMs] long from [clips] (in time order). [uriFor] = the file to read
     * pictures from (a proxy is faster). [words] (optional) = the transcript, for clean cut points.
     */
    fun build(
        context: Context, clips: List<Clip>, targetMs: Long, words: List<TWord>, uriFor: (Clip) -> Uri,
        onProgress: (Int) -> Unit, stop: () -> Boolean,
    ): List<Clip> {
        val bins = ArrayList<Bin>()
        val totalMs = clips.sumOf { (it.endMs - it.startMs).coerceAtLeast(0) }.coerceAtLeast(1)
        var doneMs = 0L
        for ((ci, c) in clips.withIndex()) {
            if (stop()) return emptyList()
            val peaks = Waveforms.load(context, c.uri)
            val first = bins.size
            // Sound per bin.
            var t = c.startMs
            while (t + BIN_MS <= c.endMs) {
                val b = Bin(ci, t)
                if (peaks != null) {
                    val i0 = (t / Waveforms.BUCKET_MS).toInt(); val i1 = ((t + BIN_MS) / Waveforms.BUCKET_MS).toInt()
                    var m = 0f; var s = 0f; var n = 0
                    for (i in i0 until i1) if (i in peaks.indices) { m = max(m, peaks[i]); s += peaks[i]; n++ }
                    b.loud = 20f * log10(max(if (n > 0) s / n else 0f, 1f / 255f))
                    b.burst = 20f * log10(max(m, 1f / 255f))
                }
                bins += b
                t += BIN_MS
            }
            // Speech per bin: words per second.
            for (w in words) if (w.clipId == c.id && !w.deleted) {
                val k = first + ((w.startMs - c.startMs) / BIN_MS).toInt()
                if (k in first until bins.size) bins[k].speech += 1f
            }
            // Movement per bin: picture difference between bins (small frames).
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(context, uriFor(c))
                var prev: IntArray? = null
                for (k in first until bins.size) {
                    if (stop()) return emptyList()
                    val at = bins[k].srcMs + BIN_MS / 2
                    val big: Bitmap? = if (android.os.Build.VERSION.SDK_INT >= 27)
                        mmr.getScaledFrameAtTime(at * 1000, MediaMetadataRetriever.OPTION_CLOSEST, 320, 180)
                    else mmr.getFrameAtTime(at * 1000, MediaMetadataRetriever.OPTION_CLOSEST)?.let { Bitmap.createScaledBitmap(it, 320, 180, true) }
                    val bmp = big?.let { Bitmap.createScaledBitmap(it, 48, 27, true) }
                    if (big != null) {
                        // Sharpness: how much fine detail (Laplacian energy) — blurry, shaky moments score low.
                        val sm = Bitmap.createScaledBitmap(big, 96, 54, true)
                        val q = IntArray(96 * 54); sm.getPixels(q, 0, 96, 0, 0, 96, 54)
                        var e = 0f
                        for (y in 1 until 53) for (x in 1 until 95) {
                            fun g(i: Int) = ((q[i] shr 8) and 255)
                            val i = y * 96 + x
                            e += abs(4 * g(i) - g(i - 1) - g(i + 1) - g(i - 96) - g(i + 96)).toFloat()
                        }
                        bins[k].sharp = e / (94 * 52)
                        // People: faces in the picture (every other bin; the phone's own face finder).
                        if ((k - first) % 2 == 0) try {
                            val f565 = big.copy(Bitmap.Config.RGB_565, false)
                            val found = arrayOfNulls<android.media.FaceDetector.Face>(4)
                            val n = android.media.FaceDetector(f565.width, f565.height, 4).findFaces(f565, found)
                            bins[k].face = n.coerceAtMost(3).toFloat()
                            if (k + 1 < bins.size) bins[k + 1].face = bins[k].face
                        } catch (_: Throwable) {}
                    }
                    if (bmp != null) {
                        val px = IntArray(bmp.width * bmp.height)
                        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                        val p = prev
                        if (p != null && p.size == px.size) {
                            var d = 0f
                            for (i in px.indices) { val a = px[i]; val q = p[i]; d += abs(((a shr 8) and 255) - ((q shr 8) and 255)) / 255f }
                            bins[k].motion = d / px.size
                        }
                        prev = px
                    }
                    onProgress(((doneMs + (bins[k].srcMs - c.startMs)) * 100 / totalMs).toInt().coerceIn(0, 99))
                }
            } catch (_: Throwable) {
            } finally { try { mmr.release() } catch (_: Throwable) {} }
            // A hard cut isn't "movement": clip the biggest jumps.
            for (k in first until bins.size) if (bins[k].motion > 0.25f) bins[k].motion = 0.25f
            doneMs += c.endMs - c.startMs
        }
        if (bins.size < 4) return emptyList()
        // Bursts: loudness above the surroundings (±10 s), so a loud clip doesn't win everywhere.
        val excite = FloatArray(bins.size)
        val w = (10_000 / BIN_MS).toInt()
        for (k in bins.indices) {
            val ls = ArrayList<Float>()
            for (j in (k - w).coerceAtLeast(0)..(k + w).coerceAtMost(bins.lastIndex)) if (bins[j].clip == bins[k].clip) ls += bins[j].loud
            ls.sort()
            excite[k] = bins[k].burst - ls[ls.size / 2]
        }
        fun z(v: FloatArray): FloatArray {
            val m = v.average().toFloat(); val sd = sqrt(v.map { (it - m) * (it - m) }.average().toFloat()).coerceAtLeast(1e-4f)
            return FloatArray(v.size) { ((v[it] - m) / sd).coerceIn(-3f, 3f) }
        }
        val zE = z(excite); val zM = z(FloatArray(bins.size) { bins[it].motion }); val zL = z(FloatArray(bins.size) { bins[it].loud })
        val hasWords = words.isNotEmpty()
        val zS = z(FloatArray(bins.size) { bins[it].speech })
        val zSh = z(FloatArray(bins.size) { bins[it].sharp }); val zF = z(FloatArray(bins.size) { bins[it].face })
        for (k in bins.indices) bins[k].score = 0.4f * zE[k] + 0.25f * zM[k] + 0.1f * zL[k] + 0.15f * zSh[k] + 0.15f * zF[k] + (if (hasWords) 0.2f * zS[k] else 0f)

        // Pick the best non-overlapping windows until the reel is long enough.
        val segMs = when { targetMs <= 15_000 -> 3000L; targetMs <= 30_000 -> 4000L; targetMs <= 60_000 -> 5000L; else -> 6000L }
        val segBins = (segMs / BIN_MS).toInt()
        val used = BooleanArray(bins.size)
        val picks = ArrayList<Pair<Int, Int>>()   // bin range
        var total = 0L
        while (total < targetMs) {
            var best = -1; var bv = -1e9f
            for (k in 0..bins.size - segBins) {
                if (bins[k].clip != bins[k + segBins - 1].clip) continue
                // Keep a little space round picks.
                var ok = true
                for (j in (k - 2).coerceAtLeast(0)..(k + segBins + 1).coerceAtMost(bins.lastIndex)) if (used[j]) { ok = false; break }
                if (!ok) continue
                var s = 0f; for (j in k until k + segBins) s += bins[j].score
                if (s > bv) { bv = s; best = k }
            }
            if (best < 0) break
            for (j in best until best + segBins) used[j] = true
            picks += best to best + segBins - 1
            total += segMs
        }
        onProgress(100)
        // In story order, with clean cut points.
        return picks.sortedBy { it.first }.mapNotNull { (a, b) ->
            val c = clips[bins[a].clip]
            var s = bins[a].srcMs; var e = bins[b].srcMs + BIN_MS
            val ws = words.filter { it.clipId == c.id }
            if (ws.isNotEmpty()) {
                // Don't start or end inside a word.
                ws.firstOrNull { s > it.startMs && s < it.endMs }?.let { s = it.startMs - 60 }
                ws.firstOrNull { e > it.startMs && e < it.endMs }?.let { e = it.endMs + 60 }
            } else {
                // Snap to the quietest moment nearby.
                val peaks = Waveforms.cached(c.uri)
                if (peaks != null) {
                    fun quiet(at: Long): Long {
                        var bt = at; var bq = 2f
                        var x = at - 400
                        while (x <= at + 400) { val i = (x / Waveforms.BUCKET_MS).toInt(); if (i in peaks.indices && peaks[i] < bq) { bq = peaks[i]; bt = x }; x += Waveforms.BUCKET_MS }
                        return bt
                    }
                    s = quiet(s); e = quiet(e)
                }
            }
            s = s.coerceIn(c.startMs, c.endMs); e = e.coerceIn(s, c.endMs)
            if (e - s < 800) null
            else c.copy(id = UUID.randomUUID().toString(), startMs = s, endMs = e, transition = Transition(), volKeys = null, reframe = c.reframe.copy(keys = null))
        }
    }

    /**
     * Lines the reel's cuts up with the music: each moment is lengthened or shortened (within its
     * clip) so the next cut lands on a beat. [beats] are timeline times (ms); [every] = beats per cut at least.
     */
    fun snapToBeats(reel: List<Clip>, beats: List<Long>, every: Int = 2): List<Clip> {
        if (beats.size < 4 || reel.isEmpty()) return reel
        val out = ArrayList<Clip>()
        var t = 0L
        var bi = beats.indexOfFirst { it > 0 }.coerceAtLeast(0)
        for (c in reel) {
            val want = t + c.lengthMs
            // The beat nearest to where this moment would end, at least [every] beats on.
            var j = bi + every
            while (j + 1 < beats.size && abs(beats[j + 1] - want) < abs(beats[j] - want)) j++
            if (j >= beats.size) { out += c; t += c.lengthMs; continue }
            val len = beats[j] - t
            val newEnd = (c.startMs + (len * c.speed).toLong()).coerceIn(c.startMs + 400, c.durationMs)
            val nc = c.copy(endMs = newEnd)
            out += nc
            t += nc.lengthMs
            bi = j
        }
        return out
    }
}
