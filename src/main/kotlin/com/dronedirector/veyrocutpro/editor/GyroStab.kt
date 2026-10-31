package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Gyro stabilisation inside the editor, from the motion file Capture records next to a clip
 * (Settings → Stabilization → Gyroflow, `<clip>.gcsv`). Own code.
 *
 * The gyroscope gives the camera's rotation about 200 times a second — sharper and steadier than
 * measuring it from the picture — but the file doesn't say how the phone was held or how wide the
 * lens is. So the editor first measures the motion from the picture as usual (a quick, coarse pass),
 * then matches the gyro's three rotation curves against it: which axis moves the picture sideways,
 * which up and down, which turns it, in which direction and by how much (and any small time offset).
 * The camera path is then rebuilt from the gyro alone at 60 points a second.
 */
object GyroStab {
    class Log(val tUs: LongArray, val gx: FloatArray, val gy: FloatArray, val gz: FloatArray)

    /** Finds `<clip name>.gcsv`: the file the user picked, else Documents/AlcorCutPro/Gyroflow (or anywhere on the phone). */
    fun find(context: Context, c: Clip): Uri? {
        c.stab.gyroFile?.let { return Uri.parse(it) }
        val base = c.name.substringBeforeLast('.')
        if (base.isBlank()) return null
        return try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"), arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf("$base.gcsv"), "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { cur -> if (cur.moveToFirst()) android.content.ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), cur.getLong(0)) else null }
        } catch (_: Throwable) { null }
    }

    /** Reads a Gyroflow .gcsv (t, gx, gy, gz …; tscale / gscale from the header). */
    fun read(context: Context, uri: Uri): Log? = try {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { r ->
            var tscale = 0.000001; var gscale = 1.0
            var cols: List<String>? = null
            val t = ArrayList<Long>(20000); val x = ArrayList<Float>(20000); val y = ArrayList<Float>(20000); val z = ArrayList<Float>(20000)
            r.forEachLine { line ->
                val p = line.split(',')
                if (cols == null) {
                    when (p.firstOrNull()?.trim()) {
                        "tscale" -> tscale = p.getOrNull(1)?.trim()?.toDoubleOrNull() ?: tscale
                        "gscale" -> gscale = p.getOrNull(1)?.trim()?.toDoubleOrNull() ?: gscale
                        "t" -> cols = p.map { it.trim() }
                    }
                } else {
                    val cl = cols!!
                    val ti = p.getOrNull(cl.indexOf("t"))?.toDoubleOrNull() ?: return@forEachLine
                    val a = p.getOrNull(cl.indexOf("gx"))?.toDoubleOrNull() ?: return@forEachLine
                    val b = p.getOrNull(cl.indexOf("gy"))?.toDoubleOrNull() ?: return@forEachLine
                    val d = p.getOrNull(cl.indexOf("gz"))?.toDoubleOrNull() ?: return@forEachLine
                    t += (ti * tscale * 1_000_000.0).toLong(); x += (a * gscale).toFloat(); y += (b * gscale).toFloat(); z += (d * gscale).toFloat()
                }
            }
            if (t.size < 50) null else Log(t.toLongArray(), x.toFloatArray(), y.toFloatArray(), z.toFloatArray())
        }
    } catch (_: Throwable) { null }

    /** Cumulative rotation (radians) of each axis at each gyro sample. */
    private fun integrate(l: Log): Array<FloatArray> {
        val n = l.tUs.size
        val out = Array(3) { FloatArray(n) }
        for (i in 1 until n) {
            val dt = ((l.tUs[i] - l.tUs[i - 1]) / 1e6f).coerceIn(0f, 0.05f)
            out[0][i] = out[0][i - 1] + (l.gx[i] + l.gx[i - 1]) * 0.5f * dt
            out[1][i] = out[1][i - 1] + (l.gy[i] + l.gy[i - 1]) * 0.5f * dt
            out[2][i] = out[2][i - 1] + (l.gz[i] + l.gz[i - 1]) * 0.5f * dt
        }
        return out
    }

    private fun interp(tUs: LongArray, v: FloatArray, at: Long): Float {
        var i = tUs.binarySearch(at).let { if (it < 0) -it - 2 else it }
        i = i.coerceIn(0, tUs.size - 1); val j = min(i + 1, tUs.size - 1)
        if (i == j || tUs[j] == tUs[i]) return v[i]
        val f = ((at - tUs[i]).toFloat() / (tUs[j] - tUs[i])).coerceIn(0f, 1f)
        return v[i] + (v[j] - v[i]) * f
    }

    /** Keeps the shake only: subtracts a ~1.5 s moving average (drift and intended moves differ between gyro and picture). */
    private fun highPass(v: FloatArray, win: Int): FloatArray {
        val n = v.size; val out = FloatArray(n)
        val pre = DoubleArray(n + 1); for (i in 0 until n) pre[i + 1] = pre[i] + v[i]
        for (i in 0 until n) { val a = max(0, i - win); val b = min(n, i + win + 1); out[i] = v[i] - ((pre[b] - pre[a]) / (b - a)).toFloat() }
        return out
    }

    private fun fit(target: FloatArray, src: FloatArray): Pair<Float, Float> { // (scale, correlation)
        var st = 0.0; var ss = 0.0; var tt = 0.0
        for (i in target.indices) { st += target[i] * src[i]; ss += src[i] * src[i]; tt += target[i] * target[i] }
        if (ss < 1e-12 || tt < 1e-12) return 0f to 0f
        return (st / ss).toFloat() to (st / sqrt(ss * tt)).toFloat()
    }

    /**
     * The camera path from the gyro, lined up with [optical] (the picture-based path of the same clip).
     * Null when the file is missing or doesn't match this clip.
     */
    fun fuse(context: Context, c: Clip, optical: Motion): Motion? {
        val uri = find(context, c) ?: return null
        val log = read(context, uri) ?: return null
        val ang = integrate(log)
        val tOpt = optical.timesMs
        if (tOpt.size < 20) return null
        val step = (tOpt.last() - tOpt.first()).toFloat() / (tOpt.size - 1)
        val win = max(2, (750f / step).toInt())
        val hx = highPass(optical.x, win); val hy = highPass(optical.y, win); val ha = highPass(optical.a, win)

        // Best time offset (gyro clock vs video), −200…+200 ms, judged on the strongest axis.
        var bestLag = 0L; var bestScore = -1f
        for (lag in -200L..200L step 10L) {
            val gs = Array(3) { k -> highPass(FloatArray(tOpt.size) { interp(log.tUs, ang[k], (tOpt[it] + lag) * 1000) }, win) }
            val s = gs.maxOf { g -> max(abs(fit(hx, g).second), abs(fit(hy, g).second)) }
            if (s > bestScore) { bestScore = s; bestLag = lag }
        }
        val g = Array(3) { k -> highPass(FloatArray(tOpt.size) { interp(log.tUs, ang[k], (tOpt[it] + bestLag) * 1000) }, win) }
        // Which gyro axis drives sideways, up/down and turning in this clip.
        val fx = g.map { fit(hx, it) }; val fy = g.map { fit(hy, it) }
        val ax = fx.indices.maxByOrNull { abs(fx[it].second) } ?: return null
        val ay = fy.indices.filter { it != ax }.maxByOrNull { abs(fy[it].second) } ?: return null
        val ar = (0..2).first { it != ax && it != ay }
        if (abs(fx[ax].second) < 0.3f && abs(fy[ay].second) < 0.3f) return null   // the file doesn't fit this clip
        val kx = fx[ax].first; val ky = fy[ay].first
        val fr = fit(ha, g[ar])
        // Rotation: the measured scale when the picture shows it clearly, else radians → degrees with the measured direction.
        val ka = if (abs(fr.second) > 0.3f) fr.first else (if (fr.first < 0f) -57.29578f else 57.29578f)

        // Rebuild the path from the gyro at 60 points a second over the clip.
        val dur = max(c.durationMs, tOpt.last())
        val n = (dur * 60 / 1000).toInt().coerceIn(2, 200_000)
        val times = LongArray(n) { it * dur / (n - 1) }
        val xs = FloatArray(n); val ys = FloatArray(n); val aa = FloatArray(n)
        for (i in 0 until n) {
            val us = (times[i] + bestLag) * 1000
            xs[i] = kx * interp(log.tUs, ang[ax], us)
            ys[i] = ky * interp(log.tUs, ang[ay], us)
            aa[i] = ka * interp(log.tUs, ang[ar], us)
        }
        return Motion(times, xs, ys, aa)
    }
}
