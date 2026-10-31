package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build

/**
 * Motion tracking for layers (Layers → TRACK MOTION): follows the spot under the layer through the
 * clip by matching a small patch frame to frame, and turns the path into position keyframes —
 * so text, a sticker or a blur stays on a moving person or object. Runs on the phone.
 */
object Tracker {
    private const val W = 320
    private const val STEP_MS = 100L

    private class Gray(val w: Int, val h: Int, val px: FloatArray)

    private fun gray(b: Bitmap): Gray {
        val s = if (b.width == W) b else Bitmap.createScaledBitmap(b, W, (W.toFloat() * b.height / b.width).toInt().coerceAtLeast(2), true)
        val p = IntArray(s.width * s.height); s.getPixels(p, 0, s.width, 0, 0, s.width, s.height)
        val g = FloatArray(p.size) { val c = p[it]; (((c shr 16) and 0xFF) * 0.299f + ((c shr 8) and 0xFF) * 0.587f + (c and 0xFF) * 0.114f) }
        return Gray(s.width, s.height, g)
    }

    /**
     * Tracks from timeline [startMs] (inside clip [clipIndex]) to [endMs], starting at the layer's
     * position [x0], [y0] (0..1). Returns timeline ms → position; stops early if the spot is lost.
     */
    fun track(
        context: Context, clips: List<Clip>, clipIndex: Int, startMs: Long, endMs: Long, x0: Float, y0: Float,
        progress: (Int) -> Unit, cancelled: () -> Boolean,
    ): List<Triple<Long, Float, Float>> {
        val c = clips.getOrNull(clipIndex) ?: return emptyList()
        val clipStart = clipStarts(clips)[clipIndex]
        val until = minOf(endMs, clipStart + c.lengthMs - 1)
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, c.uri)
            fun frameAt(tl: Long): Gray? {
                val srcUs = c.srcAtOffset(tl - clipStart) * 1000
                val b = (if (Build.VERSION.SDK_INT >= 27) mmr.getScaledFrameAtTime(srcUs, MediaMetadataRetriever.OPTION_CLOSEST, W, W * 4)
                else mmr.getFrameAtTime(srcUs, MediaMetadataRetriever.OPTION_CLOSEST)) ?: return null
                return gray(b).also { b.recycle() }
            }
            var prev = frameAt(startMs) ?: return emptyList()
            val half = (prev.w * 0.05f).toInt().coerceIn(8, 24)
            val search = (prev.w * 0.08f).toInt().coerceIn(12, 32)
            var cx = x0 * prev.w; var cy = y0 * prev.h
            // The patch we look for (updated a little each step, so slow changes are followed).
            val size = 2 * half + 1
            var tpl = FloatArray(size * size)
            fun grab(g: Gray, x: Float, y: Float, into: FloatArray) {
                val ix = x.toInt(); val iy = y.toInt()
                for (j in -half..half) for (i in -half..half) {
                    val px = (ix + i).coerceIn(0, g.w - 1); val py = (iy + j).coerceIn(0, g.h - 1)
                    into[(j + half) * size + (i + half)] = g.px[py * g.w + px]
                }
            }
            grab(prev, cx, cy, tpl)
            val out = mutableListOf(Triple(startMs, x0, y0))
            var t = startMs + STEP_MS
            val cur = FloatArray(size * size)
            while (t <= until && !cancelled()) {
                val g = frameAt(t) ?: break
                var best = Float.MAX_VALUE; var bx = cx; var by = cy
                // Coarse then fine search for the best match (sum of squared differences, brightness-corrected).
                val tplMean = tpl.average().toFloat()
                for (step in listOf(3, 1)) {
                    val r = if (step == 3) search else 3
                    val ox = bx; val oy = by
                    var dy = -r
                    while (dy <= r) {
                        var dx = -r
                        while (dx <= r) {
                            val x = ox + dx; val y = oy + dy
                            grab(g, x, y, cur)
                            val m = cur.average().toFloat() - tplMean
                            var ssd = 0f
                            for (k in cur.indices) { val d = cur[k] - m - tpl[k]; ssd += d * d }
                            if (ssd < best) { best = ssd; bx = x; by = y }
                            dx += step
                        }
                        dy += step
                    }
                }
                // Lost it (big mismatch): stop rather than wander off.
                if (best / (size * size) > 1600f) break
                cx = bx.coerceIn(0f, (g.w - 1).toFloat()); cy = by.coerceIn(0f, (g.h - 1).toFloat())
                grab(g, cx, cy, cur)
                for (k in tpl.indices) tpl[k] = tpl[k] * 0.6f + cur[k] * 0.4f
                out += Triple(t, cx / g.w, cy / g.h)
                prev = g
                progress(((t - startMs) * 100 / (until - startMs).coerceAtLeast(1)).toInt())
                t += STEP_MS
            }
            return out
        } catch (_: Throwable) {
            return emptyList()
        } finally {
            try { mmr.release() } catch (_: Throwable) {}
        }
    }
}
