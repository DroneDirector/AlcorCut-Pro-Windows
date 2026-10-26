package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

/** Pro editing helpers written for AlcorCut Pro (no third-party code). */
object ProTools {

    // ------------------------------------------------------------------ scene cut detection

    /**
     * Finds the cuts (source ms) inside [c]: frames are sampled every [stepMs] at a small size and
     * compared by colour histogram and pixel difference; a cut is a sharp, isolated jump.
     */
    fun detectScenes(context: Context, c: Clip, uri: Uri = c.uri, stepMs: Long = 200, onProgress: (Int) -> Unit = {}, stop: () -> Boolean = { false }): List<Long> {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            val times = ArrayList<Long>()
            val diffs = ArrayList<Float>()
            var prevHist: FloatArray? = null
            var prevPix: IntArray? = null
            var t = c.startMs
            val total = (c.endMs - c.startMs).coerceAtLeast(1)
            while (t <= c.endMs && !stop()) {
                val bmp: Bitmap? = if (android.os.Build.VERSION.SDK_INT >= 27)
                    mmr.getScaledFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST, 64, 36)
                else mmr.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST)?.let { Bitmap.createScaledBitmap(it, 64, 36, true) }
                if (bmp != null) {
                    val px = IntArray(bmp.width * bmp.height)
                    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                    val h = FloatArray(64)
                    for (p in px) h[((p shr 22) and 3) * 16 + ((p shr 14) and 3) * 4 + ((p shr 6) and 3)] += 1f
                    for (i in h.indices) h[i] /= px.size
                    val ph = prevHist; val pp = prevPix
                    if (ph != null && pp != null && pp.size == px.size) {
                        var hd = 0f; for (i in h.indices) hd += abs(h[i] - ph[i])
                        var pd = 0f
                        for (i in px.indices) {
                            val a = px[i]; val b = pp[i]
                            pd += (abs(((a shr 16) and 255) - ((b shr 16) and 255)) + abs(((a shr 8) and 255) - ((b shr 8) and 255)) + abs((a and 255) - (b and 255))) / 765f
                        }
                        pd /= px.size
                        times += t; diffs += hd * 0.5f + pd
                    }
                    prevHist = h; prevPix = px
                }
                onProgress(((t - c.startMs) * 100 / total).toInt())
                t += stepMs
            }
            // A cut: well above the neighbourhood (mean + 3σ over ±2 s) and a clear absolute jump.
            val cuts = ArrayList<Long>()
            val w = (2000 / stepMs).toInt()
            for (i in diffs.indices) {
                val a = (i - w).coerceAtLeast(0); val b = (i + w).coerceAtMost(diffs.lastIndex)
                var s = 0f; var s2 = 0f; var n = 0
                for (j in a..b) if (j != i) { s += diffs[j]; s2 += diffs[j] * diffs[j]; n++ }
                val mean = if (n > 0) s / n else 0f
                val sd = if (n > 1) sqrt((s2 / n - mean * mean).coerceAtLeast(0f)) else 0f
                val d = diffs[i]
                val isPeak = (i == 0 || d >= diffs[i - 1]) && (i == diffs.lastIndex || d >= diffs[i + 1])
                if (isPeak && d > 0.28f && d > mean + 3f * sd + 0.08f) {
                    val at = times[i] - stepMs / 2
                    if (at - c.startMs > 400 && c.endMs - at > 400 && (cuts.isEmpty() || at - cuts.last() > 400)) cuts += at
                }
            }
            return cuts
        } catch (_: Throwable) {
            return emptyList()
        } finally { try { mmr.release() } catch (_: Throwable) {} }
    }

    /** Splits [c] at the source times [cuts] (each piece keeps the clip's settings). */
    fun splitAt(c: Clip, cuts: List<Long>): List<Clip> {
        val pts = (listOf(c.startMs) + cuts.filter { it > c.startMs + 100 && it < c.endMs - 100 }.sorted() + listOf(c.endMs)).distinct()
        return pts.zipWithNext().mapIndexed { k, (a, b) ->
            c.copy(
                id = if (k == 0) c.id else UUID.randomUUID().toString(), startMs = a, endMs = b,
                transition = if (k == pts.size - 2) c.transition else Transition(),
                volKeys = null, reframe = c.reframe.copy(keys = null),
            )
        }
    }

    // ------------------------------------------------------------------ auto-edit to the beat

    /** The music's beats on the timeline (ms), from the track's waveform. */
    fun beatsOnTimeline(m: MusicTrack, peaks: FloatArray): List<Long> =
        Beats.detect(peaks).filter { it >= m.offsetMs }.map { m.startMs + (it - m.offsetMs) }

    /**
     * Builds a new timeline from [sources]: a cut on every [every]-th beat, taking the next part of
     * each clip in turn (round robin), until the music ends or [maxMs].
     */
    fun autoEdit(sources: List<Clip>, beats: List<Long>, every: Int, maxMs: Long): List<Clip> {
        if (sources.isEmpty() || beats.size < 2) return emptyList()
        val cuts = ArrayList<Long>()
        cuts += 0L
        beats.filterIndexed { i, _ -> i % every.coerceAtLeast(1) == 0 }.filter { it > 300 && it < maxMs }.forEach { cuts += it }
        if (maxMs - cuts.last() > 300) cuts += maxMs
        val cursor = HashMap<String, Long>()
        val out = ArrayList<Clip>()
        var k = 0
        for ((a, b) in cuts.zipWithNext()) {
            val len = b - a
            if (len < 200) continue
            var tries = 0
            while (tries < sources.size) {
                val s = sources[k % sources.size]; k++; tries++
                val sp = s.speed.coerceAtLeast(0.1f)
                val need = (len * sp).toLong()
                if (s.endMs - s.startMs < need) continue
                var from = cursor[s.id] ?: s.startMs
                if (from + need > s.endMs) from = s.startMs
                cursor[s.id] = from + need
                out += s.copy(
                    id = UUID.randomUUID().toString(), startMs = from, endMs = from + need,
                    transition = Transition(), volKeys = null, ramp = null, rampName = null, reframe = s.reframe.copy(keys = null),
                )
                break
            }
        }
        return out
    }

    // ------------------------------------------------------------------ fit the music to the video

    /**
     * Makes the music last exactly [targetMs] and end on the song's real ending: a beat-aligned middle
     * part is taken out (song too long) or repeated (song too short). Returns the new tracks
     * (the first replaces the music track), or null if it already fits.
     */
    fun fitMusic(m: MusicTrack, peaks: FloatArray, targetMs: Long): List<MusicTrack>? {
        val beats = Beats.detect(peaks).filter { it > m.offsetMs }
        val rem = m.durationMs - m.offsetMs
        if (abs(rem - targetMs) < 300 || beats.size < 8 || targetMs < 3000) return null
        val fade = 80L
        fun nearestBeat(x: Long) = beats.minByOrNull { abs(it - x) } ?: x
        if (rem > targetMs) {
            val d = rem - targetMs
            // Cut point a (on a beat) in the middle of the edit; the jump lands on a beat too.
            var best = -1L; var bestErr = Long.MAX_VALUE
            for (a in beats) {
                val rel = a - m.offsetMs
                if (rel < targetMs * 0.3 || rel > targetMs * 0.7) continue
                val err = abs(nearestBeat(a + d) - (a + d))
                if (err < bestErr) { bestErr = err; best = a }
            }
            if (best < 0) best = nearestBeat(m.offsetMs + targetMs / 2)
            val jumpTo = best + d
            val p1 = m.copy(lengthMs = best - m.offsetMs, fadeOutMs = fade)
            val p2 = m.copy(id = UUID.randomUUID().toString(), offsetMs = jumpTo, startMs = m.startMs + (best - m.offsetMs), lengthMs = 0L, fadeInMs = fade)
            return listOf(p1, p2)
        }
        // Too short: repeat a beat-aligned section from the middle of the song.
        val mid = m.offsetMs + rem / 2
        val loopStart = nearestBeat(mid - rem / 4)
        val loopEnd = nearestBeat(mid + rem / 4).coerceAtLeast(loopStart + 2000)
        val loopLen = loopEnd - loopStart
        val short = targetMs - rem
        val reps = ((short + loopLen - 1) / loopLen).toInt().coerceIn(1, 20)
        var excess = rem + reps * loopLen - targetMs
        val tracks = ArrayList<MusicTrack>()
        var t = m.startMs
        // Intro up to the loop end, then the repeats (the first one trimmed by the excess), then the rest of the song.
        tracks += m.copy(lengthMs = loopEnd - m.offsetMs, fadeOutMs = fade); t += loopEnd - m.offsetMs
        repeat(reps) { r ->
            val from = if (r == 0) (loopStart + excess).coerceAtMost(loopEnd - 500) else loopStart
            if (r == 0) excess = 0
            tracks += m.copy(id = UUID.randomUUID().toString(), offsetMs = from, startMs = t, lengthMs = loopEnd - from, fadeInMs = fade, fadeOutMs = fade)
            t += loopEnd - from
        }
        tracks += m.copy(id = UUID.randomUUID().toString(), offsetMs = loopEnd, startMs = t, lengthMs = 0L, fadeInMs = fade)
        return tracks
    }

    // ------------------------------------------------------------------ YouTube chapters

    /** YouTube chapter list from the markers (first chapter at 0:00; YouTube needs ≥ 3 chapters of ≥ 10 s). */
    fun chapters(markers: List<Long>, notes: Map<Long, String>, totalMs: Long): String {
        val pts = ArrayList<Pair<Long, String>>()
        pts += 0L to (notes[0L]?.takeIf { it.isNotBlank() } ?: "Intro")
        for (m in markers.sorted()) {
            if (m < 10_000 || m > totalMs - 5_000) continue
            if (m - pts.last().first < 10_000) continue
            pts += m to (notes[m]?.takeIf { it.isNotBlank() } ?: "Chapter ${pts.size + 1}")
        }
        fun t(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }
        return pts.joinToString("\n") { "${t(it.first)} ${it.second}" }
    }

    // ------------------------------------------------------------------ project versions

    private fun versionDir(context: Context, id: String) = File(context.filesDir, "projects/video/versions/$id").apply { mkdirs() }

    /** Keeps a copy of the project as it is now (newest 30 kept). */
    fun saveVersion(context: Context, p: VideoProjectData, json: String? = null) {
        if (p.id.isBlank()) return
        try {
            val d = versionDir(context, p.id)
            File(d, "${System.currentTimeMillis()}.json").writeText(json ?: ProjectStore.gson.toJson(p))
            d.listFiles()?.sortedByDescending { it.name }?.drop(30)?.forEach { it.delete() }
        } catch (_: Throwable) {}
    }

    fun versions(context: Context, id: String): List<Pair<Long, File>> =
        versionDir(context, id).listFiles()?.mapNotNull { f -> f.name.removeSuffix(".json").toLongOrNull()?.let { it to f } }?.sortedByDescending { it.first } ?: emptyList()

    fun loadVersion(f: File): VideoProjectData? = try { ProjectStore.parseVideo(f.readText()) } catch (_: Throwable) { null }
}
