package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Shot match (Color → MATCH): makes a clip's colour and brightness match a reference clip, by
 * measuring both (three frames each) and adjusting the clip's exposure, white balance, contrast and
 * saturation until its statistics agree with the reference as you see it (with its grade).
 * Written for AlcorCut Pro (no third-party code).
 */
object ShotMatch {
    private const val W = 96
    private const val H = 54

    /** Pixels (r, g, b triples, 0..1) from three frames of [c] at 25 / 50 / 75 % of its in-to-out range. */
    private fun sample(context: Context, c: Clip): FloatArray? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, c.uri)
            val out = ArrayList<Float>(W * H * 9)
            for (f in listOf(0.25, 0.5, 0.75)) {
                val t = (c.startMs + (c.endMs - c.startMs) * f).toLong() * 1000
                val bmp: Bitmap = (if (android.os.Build.VERSION.SDK_INT >= 27) mmr.getScaledFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, W, H)
                    else mmr.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { Bitmap.createScaledBitmap(it, W, H, true) }) ?: continue
                val px = IntArray(bmp.width * bmp.height)
                bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                for (p in px) { out += ((p shr 16) and 255) / 255f; out += ((p shr 8) and 255) / 255f; out += (p and 255) / 255f }
            }
            if (out.isEmpty()) null else out.toFloatArray()
        } catch (_: Throwable) { null } finally { try { mmr.release() } catch (_: Throwable) {} }
    }

    /** Mean log-brightness, blue–yellow and green–magenta balance, contrast and colourfulness. */
    private class Stats(val lum: Double, val by: Double, val gm: Double, val con: Double, val sat: Double)

    private fun stats(px: FloatArray, g: ProGrade?): Stats {
        val n = px.size / 3
        var sl = 0.0; var sl2 = 0.0; var sby = 0.0; var sgm = 0.0; var ssat = 0.0
        val c = FloatArray(3)
        for (i in 0 until n) {
            c[0] = px[i * 3]; c[1] = px[i * 3 + 1]; c[2] = px[i * 3 + 2]
            if (g != null && !g.isIdentity) g.apply(c)
            val r = c[0].coerceIn(0f, 1f).toDouble(); val gg = c[1].coerceIn(0f, 1f).toDouble(); val b = c[2].coerceIn(0f, 1f).toDouble()
            val l = 0.2126 * r + 0.7152 * gg + 0.0722 * b
            sl += l; sl2 += l * l
            sby += b - r
            sgm += gg - (r + b) / 2
            ssat += max(r, max(gg, b)) - minOf(r, gg, b)
        }
        val m = sl / n
        return Stats(ln(max(m, 1e-3).pow(2.2)), sby / n, sgm / n, sqrt(max(sl2 / n - m * m, 1e-6)), ssat / n)
    }

    /**
     * The grade for [target] that matches [ref]. Returns null when the frames can't be read.
     * [onProgress] gets 0..100.
     */
    fun match(context: Context, ref: Clip, target: Clip, onProgress: (Int) -> Unit = {}): ProGrade? {
        val rp = sample(context, ref) ?: return null
        onProgress(25)
        val tp0 = sample(context, target) ?: return null
        onProgress(50)
        // Fewer pixels for the search (every 3rd), all of them for the reference.
        return matchPixels(rp, ref.grade, tp0, target.grade) { onProgress(50 + it / 2) }
    }

    /** Pixels (r, g, b triples, 0..1) of a picture, small. */
    fun pixels(b: Bitmap): FloatArray {
        val s = Bitmap.createScaledBitmap(b, W * 2, (W * 2 * b.height / b.width.coerceAtLeast(1)).coerceAtLeast(1), true)
        val px = IntArray(s.width * s.height); s.getPixels(px, 0, s.width, 0, 0, s.width, s.height)
        if (s !== b) s.recycle()
        val out = FloatArray(px.size * 3)
        for (i in px.indices) { val p = px[i]; out[i * 3] = ((p shr 16) and 255) / 255f; out[i * 3 + 1] = ((p shr 8) and 255) / 255f; out[i * 3 + 2] = (p and 255) / 255f }
        return out
    }

    /** The grade (starting from [start]) that makes pixels [tp0] look like [rp] (graded by [refGrade]). */
    fun matchPixels(rp: FloatArray, refGrade: ProGrade?, tp0: FloatArray, start: ProGrade, onProgress: (Int) -> Unit = {}): ProGrade {
        val tp = FloatArray((tp0.size / 9) * 3).also { for (i in 0 until it.size / 3) { it[i * 3] = tp0[i * 9]; it[i * 3 + 1] = tp0[i * 9 + 1]; it[i * 3 + 2] = tp0[i * 9 + 2] } }
        val want = stats(rp, refGrade)
        var g = start
        fun err(s: Stats, k: Int): Double = when (k) { 0 -> s.lum - want.lum; 1 -> s.by - want.by; 2 -> s.gm - want.gm; 3 -> ln(s.con / want.con); else -> ln(max(s.sat, 1e-3) / max(want.sat, 1e-3)) }
        fun get(x: ProGrade, k: Int) = when (k) { 0 -> x.exposure; 1 -> x.temperature; 2 -> x.tint; 3 -> x.contrast; else -> x.saturation }
        fun set(x: ProGrade, k: Int, v: Float) = when (k) {
            0 -> x.copy(exposure = v.coerceIn(-3f, 3f)); 1 -> x.copy(temperature = v.coerceIn(-1f, 1f)); 2 -> x.copy(tint = v.coerceIn(-1f, 1f))
            3 -> x.copy(contrast = v.coerceIn(-1f, 1f)); else -> x.copy(saturation = v.coerceIn(-1f, 1f))
        }
        // Each control mainly moves one statistic: a few rounds of secant steps converge quickly.
        val rounds = 4
        for (round in 0 until rounds) {
            for (k in 0..4) {
                val e0 = err(stats(tp, g), k)
                if (kotlin.math.abs(e0) < 0.004) continue
                val v0 = get(g, k)
                val d = if (k == 0) 0.1f else 0.05f
                val g1 = set(g, k, v0 + d)
                val e1 = err(stats(tp, g1), k)
                val slope = (e1 - e0) / d
                if (kotlin.math.abs(slope) < 1e-4) continue
                // Temperature: the slider makes the picture warmer (less blue) — the slope handles the sign.
                val step = (-e0 / slope).toFloat().coerceIn(-1f, 1f)
                g = set(g, k, v0 + step)
            }
            onProgress((round + 1) * 100 / rounds)
        }
        return g
    }
}
