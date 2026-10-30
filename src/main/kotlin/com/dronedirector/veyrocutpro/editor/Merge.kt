package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import com.dronedirector.veyrocutpro.camera.Filter
import com.dronedirector.veyrocutpro.camera.GradeIO
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Ways to combine several photos into one. */
enum class MergeKind(val label: String, val hint: String) {
    HDR("HDR merge", "2–5 bracketed shots of the same scene (e.g. −2 / 0 / +2 EV) — things that moved are taken from the middle shot, so there are no ghosts"),
    PANORAMA("Panorama", "2–8 overlapping shots, picked left to right"),
    FOCUS("Focus stack", "2–8 shots focused at different distances"),
    NOISE("Noise stack", "2–8 shots of the same scene (tripod or hand-held): averaged for a clean, low-noise picture"),
    CLEAN("Remove passers-by", "3–8 shots from the same spot a few seconds apart: whatever moves through is taken out"),
    SUPERRES("Super resolution", "4–8 hand-held shots of the same scene: one picture with twice the width and height"),
    ASTRO("Star stack", "2–8 night-sky shots from a tripod: lined up on the stars and averaged for a clean, detailed sky"),
    HDR_PANO("HDR panorama", "Bracketed shots (e.g. 3 exposures) at each position, left to right: each position is merged to HDR, then stitched"),
    LONG_VIDEO("Long exposure from a video", "One video clip (hand-held or drone): silky water and clouds, or light trails — no ND filter needed"),
    TRAILS("Star trails", "10–100 night-sky shots in a row from a tripod (an interval timer): each star draws its arc across the sky"),
}

/** HDR (exposure fusion), panorama stitching and focus stacking, done on the phone. */
/** A set of photos the camera took for the photo editor to merge when it opens (focus bracketing). */
object PendingMerge {
    @Volatile var focus: List<android.net.Uri>? = null
    fun take(): List<android.net.Uri>? = focus.also { focus = null }
}

object Merge {
    fun run(context: Context, kind: MergeKind, uris: List<Uri>, onProgress: (String) -> Unit): Bitmap? {
        if (uris.size < 2) return null
        if (kind == MergeKind.PANORAMA || kind == MergeKind.HDR_PANO) {
            onProgress("Loading photos…")
            val frames = if (kind == MergeKind.HDR_PANO) {
                val groups = brackets(context, uris)
                if (groups.size < 2) return null
                groups.mapIndexedNotNull { i, g ->
                    onProgress("Merging exposures ${i + 1} of ${groups.size}…")
                    (if (g.size >= 2) run(context, MergeKind.HDR, g) {} else decodeAny(context, g[0], 2200))?.let { b ->
                        // Smaller before stitching, so many positions fit in memory.
                        val k = 1800f / maxOf(b.width, b.height)
                        val sm = if (k < 1f) Bitmap.createScaledBitmap(b, (b.width * k).toInt(), (b.height * k).toInt(), true).also { if (it !== b) b.recycle() } else b
                        com.dronedirector.veyrocutpro.camera.Panorama.prepareFrame(sm, 0)
                    }
                }
            } else uris.mapNotNull { decodeAny(context, it, 2400)?.let { b -> com.dronedirector.veyrocutpro.camera.Panorama.prepareFrame(b, 0) } }
            if (frames.size < 2) return null
            onProgress("Stitching…")
            // Field of view from the first photo's lens info (35 mm equivalent), else a typical phone lens.
            val f35 = try {
                context.contentResolver.openInputStream(uris[0])?.use { androidx.exifinterface.media.ExifInterface(it).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0) } ?: 0
            } catch (_: Throwable) { 0 }
            val fov = if (f35 in 8..400) Math.toDegrees(2 * kotlin.math.atan(36.0 / (2 * f35))).toFloat() else 65f
            val o = PanoOpts
            val edges = o.warp > 0f || o.fill
            val raw = com.dronedirector.veyrocutpro.camera.Panorama.stitch(frames, null, fov, 1, Filter.NONE, keepEdges = edges) ?: return null
            val f = (kotlin.math.max(frames[0].width, frames[0].height) / (2.0 * kotlin.math.tan(Math.toRadians(fov.toDouble()) / 2))).toFloat()
            if (o.projection == 0 && !edges) return raw
            onProgress("Finishing the panorama…")
            return PanoOpts.finish(raw, f)
        }
        if (kind == MergeKind.NOISE || kind == MergeKind.CLEAN || kind == MergeKind.SUPERRES) return multiFrame(context, kind, uris, onProgress)
        if (kind == MergeKind.ASTRO) return astro(context, uris, onProgress)
        if (kind == MergeKind.TRAILS) return trails(context, uris, onProgress)
        onProgress("Loading photos…")
        val maxPx = 2200
        val imgs = uris.take(if (kind == MergeKind.FOCUS) 6 else 5).mapNotNull { decodeAny(context, it, maxPx) }
        if (imgs.size < 2) return null
        val w = imgs.minOf { it.width }; val h = imgs.minOf { it.height }
        val px = imgs.map { b -> IntArray(w * h).also { (if (b.width == w && b.height == h) b else Bitmap.createScaledBitmap(b, w, h, true)).getPixels(it, 0, w, 0, 0, w, h) } }
        imgs.forEach { it.recycle() }
        // Line every shot up with the middle one (hand-held shots move a little).
        val ref = px.size / 2
        onProgress("Aligning…")
        val aligned = px.mapIndexed { i, p -> if (i == ref) p else shift(p, w, h, align(px[ref], p, w, h)) }
        onProgress(if (kind == MergeKind.HDR) "Merging exposures…" else "Merging sharp areas…")
        val out = if (kind == MergeKind.HDR) fuse(aligned, w, h, ref) else stack(aligned, w, h)
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Splits bracketed shots into one group per position: a new group starts when the exposure
     * compensation comes back to the first shot's value. Without exposure info, groups of 3.
     */
    fun brackets(context: Context, uris: List<Uri>): List<List<Uri>> {
        val ev = uris.map { u ->
            try {
                context.contentResolver.openInputStream(u)?.use { androidx.exifinterface.media.ExifInterface(it).getAttributeDouble(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_BIAS_VALUE, Double.NaN) } ?: Double.NaN
            } catch (_: Throwable) { Double.NaN }
        }
        val known = ev.none { it.isNaN() } && ev.distinct().size > 1
        if (!known) return uris.chunked(if (uris.size % 3 == 0) 3 else if (uris.size % 5 == 0) 5 else if (uris.size % 2 == 0) 2 else 3)
        val out = ArrayList<MutableList<Uri>>()
        uris.forEachIndexed { i, u -> if (out.isEmpty() || (kotlin.math.abs(ev[i] - ev[0]) < 0.05 && out.last().isNotEmpty())) out.add(mutableListOf(u)) else out.last().add(u) }
        return out
    }

    private fun luma(c: Int) = (((c shr 16) and 0xFF) * 54 + ((c shr 8) and 0xFF) * 183 + (c and 0xFF) * 19) shr 8

    /** Translation (dx, dy) that best matches [b] onto [a], found coarse-to-fine on edge maps. */
    private fun align(a: IntArray, b: IntArray, w: Int, h: Int): Pair<Int, Int> {
        var dx = 0; var dy = 0
        for (scale in listOf(8, 2, 1)) {
            val sw = w / scale; val sh = h / scale
            fun edges(p: IntArray): FloatArray {
                val l = FloatArray(sw * sh) { i -> luma(p[(i / sw) * scale * w + (i % sw) * scale]).toFloat() }
                // Normalise brightness so different exposures compare.
                val mean = l.average().toFloat().coerceAtLeast(1f)
                return FloatArray(sw * sh) { i -> val x = i % sw; if (x == 0) 0f else (l[i] - l[i - 1]) / mean }
            }
            val ea = edges(a); val eb = edges(b)
            val range = if (scale == 8) 12 else 3
            val cx = dx / scale; val cy = dy / scale
            var best = Float.MAX_VALUE; var bx = cx; var by = cy
            val step = if (scale == 1) 3 else 2
            for (oy in cy - range..cy + range) for (ox in cx - range..cx + range) {
                var err = 0f; var n = 0
                var y = sh / 8
                while (y < sh * 7 / 8) {
                    val yy = y + oy
                    if (yy in 0 until sh) {
                        var x = sw / 8
                        while (x < sw * 7 / 8) {
                            val xx = x + ox
                            if (xx in 0 until sw) { err += abs(ea[y * sw + x] - eb[yy * sw + xx]); n++ }
                            x += step
                        }
                    }
                    y += step
                }
                if (n > 0 && err / n < best) { best = err / n; bx = ox; by = oy }
            }
            dx = bx * scale; dy = by * scale
        }
        return dx to dy
    }

    private fun shift(p: IntArray, w: Int, h: Int, d: Pair<Int, Int>): IntArray {
        val (dx, dy) = d
        if (dx == 0 && dy == 0) return p
        return IntArray(w * h) { i ->
            val x = (i % w + dx).coerceIn(0, w - 1); val y = (i / w + dy).coerceIn(0, h - 1)
            p[y * w + x]
        }
    }

    /** Box blur (separable, a few passes ≈ gaussian) of a weight map. */
    private fun blur(m: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        var a = m.copyOf(); val t = FloatArray(m.size)
        repeat(2) {
            for (y in 0 until h) {
                var acc = 0f
                for (x in -r..r) acc += a[y * w + x.coerceIn(0, w - 1)]
                for (x in 0 until w) {
                    t[y * w + x] = acc / (2 * r + 1)
                    acc += a[y * w + (x + r + 1).coerceAtMost(w - 1)] - a[y * w + (x - r).coerceAtLeast(0)]
                }
            }
            for (x in 0 until w) {
                var acc = 0f
                for (y in -r..r) acc += t[y.coerceIn(0, h - 1) * w + x]
                for (y in 0 until h) {
                    a[y * w + x] = acc / (2 * r + 1)
                    acc += t[(y + r + 1).coerceAtMost(h - 1) * w + x] - t[(y - r).coerceAtLeast(0) * w + x]
                }
            }
        }
        return a
    }

    /** Exposure fusion: each pixel comes mostly from the shot where it is well exposed, saturated and detailed. */
    /**
     * Long exposure from a video: up to 60 frames from [seconds] of the clip (0 = all of it), lined up on the
     * first (hand-held shake) and averaged — or, for light trails, the brightest of each pixel kept.
     */
    fun fromVideo(context: Context, uri: Uri, seconds: Int, trails: Boolean, onProgress: (String) -> Unit): Bitmap? {
        val mmr = android.media.MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            val dur = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
            val span = if (seconds <= 0) dur else minOf(dur, seconds * 1000L)
            val n = (span / 100L).toInt().coerceIn(8, 60)
            var w = 0; var h = 0
            var acc: FloatArray? = null; var mx: IntArray? = null; var first: IntArray? = null
            var used = 0
            for (k in 0 until n) {
                onProgress("Reading frame ${k + 1} of $n…")
                val t = (span * (k + 0.5) / n).toLong() * 1000
                var f = (if (android.os.Build.VERSION.SDK_INT >= 27) mmr.getScaledFrameAtTime(t, android.media.MediaMetadataRetriever.OPTION_CLOSEST, 2400, 2400)
                    else mmr.getFrameAtTime(t, android.media.MediaMetadataRetriever.OPTION_CLOSEST)) ?: continue
                if (f.config != Bitmap.Config.ARGB_8888) f = f.copy(Bitmap.Config.ARGB_8888, false)
                if (w == 0) { w = f.width; h = f.height }
                val b = if (f.width != w || f.height != h) Bitmap.createScaledBitmap(f, w, h, true) else f
                val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
                if (b !== f) b.recycle(); f.recycle()
                val al = if (first == null) { first = px; px } else shift(px, w, h, align(first!!, px, w, h))
                if (trails) {
                    val m = mx ?: IntArray(w * h).also { mx = it }
                    for (i in al.indices) { val c = al[i]; if (luma(c) > luma(m[i])) m[i] = c }
                } else {
                    val a = acc ?: FloatArray(w * h * 3).also { acc = it }
                    for (i in al.indices) { val c = al[i]; a[i * 3] += ((c shr 16) and 255).toFloat(); a[i * 3 + 1] += ((c shr 8) and 255).toFloat(); a[i * 3 + 2] += (c and 255).toFloat() }
                }
                used++
            }
            if (used < 2) return null
            onProgress("Blending $used frames…")
            val out = if (trails) mx!! else { val a = acc!!; IntArray(w * h) { i -> (0xFF shl 24) or ((a[i * 3] / used).toInt().coerceIn(0, 255) shl 16) or ((a[i * 3 + 1] / used).toInt().coerceIn(0, 255) shl 8) or (a[i * 3 + 2] / used).toInt().coerceIn(0, 255) } }
            return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        } finally { try { mmr.release() } catch (_: Throwable) {} }
    }

    /**
     * Ghosts (people, branches, waves that moved between the shots): where a shot, brightened or darkened to
     * match the reference shot, still looks different, it is left out there — that area comes from the
     * reference shot only. Returns 0..1 per pixel for each shot (1 = moved).
     */
    private fun ghosts(imgs: List<IntArray>, w: Int, h: Int, ref: Int): List<FloatArray?> {
        val r = imgs[ref]
        return imgs.mapIndexed { k, p ->
            if (k == ref) return@mapIndexed null
            // How much brighter / darker this shot is (median ratio over mid-tones of both).
            val ratios = ArrayList<Float>()
            var i = 0
            while (i < p.size) { val a = luma(p[i]); val b = luma(r[i]); if (a in 25..230 && b in 25..230) ratios += a.toFloat() / b; i += 7 }
            if (ratios.size < 100) return@mapIndexed null
            ratios.sort(); val ratio = ratios[ratios.size / 2]
            val g = FloatArray(w * h)
            for (j in p.indices) {
                val a = luma(p[j]); val b = luma(r[j])
                // Only where both are well exposed can they be compared.
                if (a < 12 || a > 243 || b < 12 || b > 243) continue
                val d = abs(a / ratio - b) / max(b.toFloat(), 24f)
                g[j] = ((d - 0.18f) / 0.17f).coerceIn(0f, 1f)
            }
            val bl = blur(g, w, h, max(3, min(w, h) / 120))
            for (j in bl.indices) bl[j] = (bl[j] * 2.2f).coerceIn(0f, 1f)
            bl
        }
    }

    private fun fuse(imgs: List<IntArray>, w: Int, h: Int, ref: Int = imgs.size / 2): IntArray {
        val ghost = try { ghosts(imgs, w, h, ref) } catch (_: Throwable) { imgs.map { null } }
        val r = max(4, min(w, h) / 60)
        val weights = imgs.map { p ->
            val wt = FloatArray(w * h)
            for (i in p.indices) {
                val c = p[i]
                val rr = ((c shr 16) and 0xFF) / 255f; val gg = ((c shr 8) and 0xFF) / 255f; val bb = (c and 0xFF) / 255f
                val mean = (rr + gg + bb) / 3f
                val sat = kotlin.math.sqrt(((rr - mean) * (rr - mean) + (gg - mean) * (gg - mean) + (bb - mean) * (bb - mean)) / 3f)
                fun we(v: Float) = exp(-((v - 0.5f) * (v - 0.5f)) / (2 * 0.2f * 0.2f))
                val x = i % w
                val lap = if (x in 1 until w - 1 && i >= w && i < p.size - w)
                    abs(4 * luma(c) - luma(p[i - 1]) - luma(p[i + 1]) - luma(p[i - w]) - luma(p[i + w])) / 255f else 0f
                wt[i] = (lap + 0.05f) * (sat + 0.05f) * we(rr) * we(gg) * we(bb) + 1e-6f
            }
            blur(wt, w, h, r)
        }
        // Moved areas: only the reference shot counts there.
        for (k in imgs.indices) { val g = ghost[k] ?: continue; val wt = weights[k]; for (i in wt.indices) wt[i] *= (1f - g[i]) }
        val anyGhost = ghost.any { it != null }
        if (anyGhost) { val wr = weights[ref]; for (i in wr.indices) { var m = 0f; for (g in ghost) if (g != null && g[i] > m) m = g[i]; wr[i] += m * 4f } }
        val out = IntArray(w * h)
        for (i in out.indices) {
            var sw = 0f; var sr = 0f; var sg = 0f; var sb = 0f
            for (k in imgs.indices) {
                val wv = weights[k][i]; val c = imgs[k][i]
                sw += wv; sr += ((c shr 16) and 0xFF) * wv; sg += ((c shr 8) and 0xFF) * wv; sb += (c and 0xFF) * wv
            }
            out[i] = (0xFF shl 24) or ((sr / sw).toInt().coerceIn(0, 255) shl 16) or ((sg / sw).toInt().coerceIn(0, 255) shl 8) or (sb / sw).toInt().coerceIn(0, 255)
        }
        return out
    }

    /** Focus stacking: each area is taken from the shot where it is sharpest. */
    private fun stack(imgs: List<IntArray>, w: Int, h: Int): IntArray {
        val r = max(3, min(w, h) / 150)
        val weights = imgs.map { p ->
            val s = FloatArray(w * h)
            for (i in w until p.size - w) {
                val x = i % w
                if (x == 0 || x == w - 1) continue
                val l = abs(4 * luma(p[i]) - luma(p[i - 1]) - luma(p[i + 1]) - luma(p[i - w]) - luma(p[i + w])).toFloat()
                s[i] = l * l
            }
            blur(s, w, h, r)
        }
        val out = IntArray(w * h)
        for (i in out.indices) {
            // Sharpness-weighted, strongly favouring the sharpest shot.
            var sw = 0f; var sr = 0f; var sg = 0f; var sb = 0f
            var mx = 0f
            for (k in imgs.indices) mx = max(mx, weights[k][i])
            for (k in imgs.indices) {
                val v = weights[k][i] / (mx + 1e-3f)
                val wv = v * v * v * v + 1e-4f
                val c = imgs[k][i]
                sw += wv; sr += ((c shr 16) and 0xFF) * wv; sg += ((c shr 8) and 0xFF) * wv; sb += (c and 0xFF) * wv
            }
            out[i] = (0xFF shl 24) or ((sr / sw).toInt().coerceIn(0, 255) shl 16) or ((sg / sw).toInt().coerceIn(0, 255) shl 8) or (sb / sw).toInt().coerceIn(0, 255)
        }
        return out
    }

    /** Noise stack, passers-by removal and super resolution: every frame lined up with the middle one. */
    private fun multiFrame(context: Context, kind: MergeKind, uris: List<Uri>, onProgress: (String) -> Unit): Bitmap? {
        if (kind == MergeKind.CLEAN && uris.size < 3) return null
        onProgress("Loading photos…")
        val maxPx = if (kind == MergeKind.SUPERRES) 1500 else 2000
        val first = decodeAny(context, uris[uris.size / 2], maxPx) ?: return null
        val w = first.width; val h = first.height
        fun pixels(b: Bitmap): IntArray {
            val s = if (b.width == w && b.height == h) b else Bitmap.createScaledBitmap(b, w, h, true)
            return IntArray(w * h).also { s.getPixels(it, 0, w, 0, 0, w, h); if (s !== b) s.recycle() }
        }
        val ref = pixels(first)
        val frames = ArrayList<IntArray>()
        val offs = ArrayList<Pair<Float, Float>>()
        for ((i, u) in uris.take(8).withIndex()) {
            onProgress("Aligning ${i + 1} / ${minOf(8, uris.size)}…")
            if (i == uris.size / 2) { frames += ref; offs += 0f to 0f; continue }
            val b = decodeAny(context, u, maxPx) ?: continue
            val p = pixels(b); b.recycle()
            val d = align(ref, p, w, h)
            if (kind == MergeKind.SUPERRES) { frames += p; offs += subpixel(ref, p, w, h, d) }
            else frames += shift(p, w, h, d)
        }
        first.recycle()
        if (frames.size < 2) return null
        return when (kind) {
            MergeKind.SUPERRES -> { onProgress("Building the bigger picture…"); superRes(frames, offs, w, h) }
            MergeKind.CLEAN -> { onProgress("Taking out what moved…"); Bitmap.createBitmap(robust(frames, w, h, median = true), w, h, Bitmap.Config.ARGB_8888) }
            else -> { onProgress("Averaging…"); Bitmap.createBitmap(robust(frames, w, h, median = false), w, h, Bitmap.Config.ARGB_8888) }
        }
    }

    // ---------------- Star trails

    /**
     * Lighten blend: per pixel the brightest value over all the shots, so every star leaves the arc it moved
     * along. Tripod shots need no lining up; one photo at a time, so a hundred shots fit in memory.
     */
    private fun trails(context: Context, uris: List<Uri>, onProgress: (String) -> Unit): Bitmap? {
        val first = decodeAny(context, uris[0], 3000) ?: return null
        val w = first.width; val h = first.height
        val out = IntArray(w * h).also { first.getPixels(it, 0, w, 0, 0, w, h) }; first.recycle()
        val p = IntArray(w * h)
        var used = 1
        for ((i, u) in uris.drop(1).withIndex()) {
            onProgress("Adding shot ${i + 2} / ${uris.size}…")
            val b = decodeAny(context, u, 3000) ?: continue
            val s = if (b.width == w && b.height == h) b else Bitmap.createScaledBitmap(b, w, h, true)
            s.getPixels(p, 0, w, 0, 0, w, h)
            if (s !== b) s.recycle(); b.recycle()
            for (k in out.indices) {
                val a = out[k]; val c = p[k]
                if (a != c) out[k] = (0xFF shl 24) or (max((a shr 16) and 0xFF, (c shr 16) and 0xFF) shl 16) or
                    (max((a shr 8) and 0xFF, (c shr 8) and 0xFF) shl 8) or max(a and 0xFF, c and 0xFF)
            }
            used++
        }
        if (used < 2) return null
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    // ---------------- Star stacking

    private class Star(val x: Float, val y: Float, val b: Float)

    /** Bright point lights: local maxima well above the sky background, with a sub-pixel centre. */
    private fun stars(p: IntArray, w: Int, h: Int): List<Star> {
        val l = FloatArray(w * h) { luma(p[it]).toFloat() }
        var sum = 0.0; var sq = 0.0; var n = 0
        var i = 0
        while (i < l.size) { sum += l[i]; sq += l[i] * l[i]; n++; i += 97 }
        val mean = sum / n; val sd = kotlin.math.sqrt((sq / n - mean * mean).coerceAtLeast(1.0))
        val th = (mean + 5 * sd).toFloat().coerceAtMost(250f)
        val out = ArrayList<Star>()
        for (y in 3 until h - 3) for (x in 3 until w - 3) {
            val v = l[y * w + x]
            if (v < th) continue
            var peak = true
            loop@ for (dy in -2..2) for (dx in -2..2) if ((dx != 0 || dy != 0) && l[(y + dy) * w + x + dx] > v) { peak = false; break@loop }
            if (!peak) continue
            // Small: drop big bright areas (moon, lights on the ground).
            if (l[y * w + x - 3] > th && l[y * w + x + 3] > th && l[(y - 3) * w + x] > th) continue
            var sx = 0f; var sy = 0f; var sw = 0f
            for (dy in -1..1) for (dx in -1..1) { val q = (l[(y + dy) * w + x + dx] - mean.toFloat()).coerceAtLeast(0f); sx += (x + dx) * q; sy += (y + dy) * q; sw += q }
            if (sw > 0f) out += Star(sx / sw, sy / sw, v)
        }
        return out.sortedByDescending { it.b }.take(120)
    }

    /** Rotation + shift taking [f]'s stars onto [r]'s (pairs with matching spacing, best agreement wins). */
    private fun starFit(r: List<Star>, f: List<Star>): FloatArray? {
        val rt = r.take(24); val ft = f.take(24)
        var best: FloatArray? = null; var bestScore = 3
        for (a in rt.indices) for (b in a + 1 until rt.size) {
            val dr = kotlin.math.hypot(rt[b].x - rt[a].x, rt[b].y - rt[a].y)
            if (dr < 20f) continue
            val ar = kotlin.math.atan2(rt[b].y - rt[a].y, rt[b].x - rt[a].x)
            for (c in ft.indices) for (d in ft.indices) {
                if (c == d) continue
                val df = kotlin.math.hypot(ft[d].x - ft[c].x, ft[d].y - ft[c].y)
                if (abs(df - dr) > 1.5f) continue
                val th = ar - kotlin.math.atan2(ft[d].y - ft[c].y, ft[d].x - ft[c].x)
                if (abs(th) > 0.12f) continue    // at most ~7° between frames
                val co = kotlin.math.cos(th); val si = kotlin.math.sin(th)
                val tx = rt[a].x - (co * ft[c].x - si * ft[c].y); val ty = rt[a].y - (si * ft[c].x + co * ft[c].y)
                var score = 0
                for (s in f) {
                    val mx = co * s.x - si * s.y + tx; val my = si * s.x + co * s.y + ty
                    if (r.any { abs(it.x - mx) < 2f && abs(it.y - my) < 2f }) score++
                }
                if (score > bestScore) { bestScore = score; best = floatArrayOf(co, si, tx, ty) }
            }
        }
        return best
    }

    private fun astro(context: Context, uris: List<Uri>, onProgress: (String) -> Unit): Bitmap? {
        onProgress("Loading photos…")
        val first = decodeAny(context, uris[0], 2400) ?: return null
        val w = first.width; val h = first.height
        val ref = IntArray(w * h).also { first.getPixels(it, 0, w, 0, 0, w, h) }; first.recycle()
        val refStars = stars(ref, w, h)
        if (refStars.size < 4) return null
        val frames = arrayListOf(ref)
        for ((i, u) in uris.drop(1).take(7).withIndex()) {
            onProgress("Lining up the stars ${i + 2} / ${minOf(8, uris.size)}…")
            val b = decodeAny(context, u, 2400) ?: continue
            val s = if (b.width == w && b.height == h) b else Bitmap.createScaledBitmap(b, w, h, true)
            val p = IntArray(w * h).also { s.getPixels(it, 0, w, 0, 0, w, h) }
            if (s !== b) s.recycle(); b.recycle()
            val t = starFit(refStars, stars(p, w, h)) ?: continue
            // Each output pixel q comes from Rᵀ (q − t) in this frame.
            val co = t[0]; val si = t[1]; val tx = t[2]; val ty = t[3]
            val out = IntArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val qx = x - tx; val qy = y - ty
                val fx = co * qx + si * qy; val fy = -si * qx + co * qy
                val x0 = fx.toInt(); val y0 = fy.toInt()
                if (fx < 0 || fy < 0 || x0 >= w - 1 || y0 >= h - 1) { out[y * w + x] = ref[y * w + x]; continue }
                val ax = fx - x0; val ay = fy - y0
                var px = 0xFF shl 24
                for (sh in intArrayOf(16, 8, 0)) {
                    fun at(xx: Int, yy: Int) = (p[yy * w + xx] shr sh) and 0xFF
                    val v = (at(x0, y0) * (1 - ax) + at(x0 + 1, y0) * ax) * (1 - ay) + (at(x0, y0 + 1) * (1 - ax) + at(x0 + 1, y0 + 1) * ax) * ay
                    px = px or ((v + 0.5f).toInt().coerceIn(0, 255) shl sh)
                }
                out[y * w + x] = px
            }
            frames += out
        }
        if (frames.size < 2) return null
        onProgress("Stacking ${frames.size} frames…")
        return Bitmap.createBitmap(robust(frames, w, h, median = false), w, h, Bitmap.Config.ARGB_8888)
    }

    /** Per pixel: the median of the frames, or the mean of the frames close to the median (moving things left out). */
    private fun robust(frames: List<IntArray>, w: Int, h: Int, median: Boolean): IntArray {
        val n = frames.size
        val out = IntArray(w * h)
        val v = IntArray(n)
        for (i in out.indices) {
            var px = 0xFF shl 24
            for (sh in intArrayOf(16, 8, 0)) {
                for (k in 0 until n) v[k] = (frames[k][i] shr sh) and 0xFF
                // insertion sort (n ≤ 8)
                for (a in 1 until n) { val t = v[a]; var b = a - 1; while (b >= 0 && v[b] > t) { v[b + 1] = v[b]; b-- }; v[b + 1] = t }
                val med = if (n % 2 == 1) v[n / 2] else (v[n / 2 - 1] + v[n / 2]) / 2
                val c = if (median) med else {
                    val tol = max(10, med * 18 / 100)
                    var sum = 0; var cnt = 0
                    for (k in 0 until n) if (abs(v[k] - med) <= tol) { sum += v[k]; cnt++ }
                    if (cnt > 0) (sum + cnt / 2) / cnt else med
                }
                px = px or (c shl sh)
            }
            out[i] = px
        }
        return out
    }

    /** Refines a whole-pixel shift to a fraction of a pixel (parabola through the match errors). */
    private fun subpixel(a: IntArray, b: IntArray, w: Int, h: Int, d: Pair<Int, Int>): Pair<Float, Float> {
        fun err(ox: Int, oy: Int): Float {
            var e = 0f; var n = 0
            var y = h / 6
            while (y < h * 5 / 6) {
                val yy = y + oy
                var x = w / 6
                while (x < w * 5 / 6) {
                    val xx = x + ox
                    if (yy in 1 until h && xx in 1 until w) {
                        val ga = luma(a[y * w + x]) - luma(a[y * w + x - 1]); val gb = luma(b[yy * w + xx]) - luma(b[yy * w + xx - 1])
                        val ha = luma(a[y * w + x]) - luma(a[(y - 1) * w + x]); val hb = luma(b[yy * w + xx]) - luma(b[(yy - 1) * w + xx])
                        e += abs(ga - gb) + abs(ha - hb); n++
                    }
                    x += 2
                }
                y += 2
            }
            return if (n > 0) e / n else Float.MAX_VALUE
        }
        val (dx, dy) = d
        val c = err(dx, dy)
        fun para(m: Float, p: Float): Float { val den = m - 2 * c + p; return if (den > 1e-6f) (0.5f * (m - p) / den).coerceIn(-0.5f, 0.5f) else 0f }
        return (dx + para(err(dx - 1, dy), err(dx + 1, dy))) to (dy + para(err(dx, dy - 1), err(dx, dy + 1)))
    }

    /** Drizzle-style super resolution: each frame's pixels land at their sub-pixel place on a 2× grid. */
    private fun superRes(frames: List<IntArray>, offs: List<Pair<Float, Float>>, w: Int, h: Int): Bitmap {
        val W = w * 2; val H = h * 2
        val acc = FloatArray(W * H * 3); val wt = FloatArray(W * H)
        for ((k, p) in frames.withIndex()) {
            val (dx, dy) = offs[k]
            for (y in 0 until h) for (x in 0 until w) {
                // This frame's pixel (x, y) sits at (x − dx, y − dy) in the reference.
                val fx = (x - dx + 0.5f) * 2f - 0.5f; val fy = (y - dy + 0.5f) * 2f - 0.5f
                val x0 = kotlin.math.floor(fx).toInt(); val y0 = kotlin.math.floor(fy).toInt()
                val ax = fx - x0; val ay = fy - y0
                val c = p[y * w + x]
                val r = ((c shr 16) and 0xFF).toFloat(); val g = ((c shr 8) and 0xFF).toFloat(); val b = (c and 0xFF).toFloat()
                for (j in 0..1) for (i in 0..1) {
                    val xx = x0 + i; val yy = y0 + j
                    if (xx !in 0 until W || yy !in 0 until H) continue
                    val q = (if (i == 0) 1 - ax else ax) * (if (j == 0) 1 - ay else ay)
                    if (q <= 0f) continue
                    val o = yy * W + xx
                    wt[o] += q; acc[o * 3] += r * q; acc[o * 3 + 1] += g * q; acc[o * 3 + 2] += b * q
                }
            }
        }
        // Gaps (few frames) are filled from the reference scaled up.
        val refBmp = Bitmap.createBitmap(frames[offs.indexOfFirst { it.first == 0f && it.second == 0f }.coerceAtLeast(0)], w, h, Bitmap.Config.ARGB_8888)
        val up = Bitmap.createScaledBitmap(refBmp, W, H, true); refBmp.recycle()
        val base = IntArray(W * H).also { up.getPixels(it, 0, W, 0, 0, W, H) }; up.recycle()
        val out = IntArray(W * H)
        for (o in out.indices) {
            val bw = base[o]
            val q = wt[o]; val mix = (q / 0.6f).coerceIn(0f, 1f)
            fun ch(k: Int, sh: Int): Int { val bv = ((bw shr sh) and 0xFF).toFloat(); val v = if (q > 1e-4f) acc[o * 3 + k] / q else bv; return (v * mix + bv * (1 - mix)).toInt().coerceIn(0, 255) }
            out[o] = (0xFF shl 24) or (ch(0, 16) shl 16) or (ch(1, 8) shl 8) or ch(2, 0)
        }
        // Light sharpening brings out the recovered detail.
        val sharp = IntArray(W * H)
        for (y in 0 until H) for (x in 0 until W) {
            val o = y * W + x
            if (x == 0 || y == 0 || x == W - 1 || y == H - 1) { sharp[o] = out[o]; continue }
            var px = 0xFF shl 24
            for (sh in intArrayOf(16, 8, 0)) {
                fun at(i: Int) = (out[i] shr sh) and 0xFF
                val v = at(o) + (4 * at(o) - at(o - 1) - at(o + 1) - at(o - W) - at(o + W)) * 0.35f
                px = px or (v.toInt().coerceIn(0, 255) shl sh)
            }
            sharp[o] = px
        }
        return Bitmap.createBitmap(sharp, W, H, Bitmap.Config.ARGB_8888)
    }

    @Suppress("unused")
    private fun draw(b: Bitmap) = Canvas(b).drawColor(0, android.graphics.PorterDuff.Mode.CLEAR).also { Paint() }
}

/** A photo for merging; DNGs (e.g. drone AEB brackets) are developed from the RAW data, not their small preview. */
private fun decodeAny(context: android.content.Context, uri: android.net.Uri, maxPx: Int): android.graphics.Bitmap? =
    try {
        if (RawDng.isRaw(context, uri)) RawDng.export(context, RawDng.open(context, uri), RawDng.Dev(), 0f, 0f, 0f, 0f, 0f, 0f, 0f, maxPx)
        else GradeIO.decode(context, uri, maxPx)
    } catch (_: RawDng.UnsupportedRaw) { GradeIO.decode(context, uri, maxPx) } catch (_: OutOfMemoryError) { GradeIO.decode(context, uri, maxPx) }

/** Panorama options: projection, boundary warp and AI fill of the empty edges. */
object PanoOpts {
    val PROJECTIONS = listOf("Cylindrical", "Spherical", "Perspective")
    var projection = 0
    var warp = 0f
    var fill = false

    private fun empty(c: Int) = (c ushr 24) < 128

    /** Applies the projection, the boundary warp, then fills (AI) or trims whatever is still empty. */
    fun finish(src: Bitmap, f: Float): Bitmap {
        var b = project(src, f)
        if (warp > 0f) b = boundaryWarp(b, warp)
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        if (px.none { empty(it) }) return b
        if (fill) {
            val mask = Bitmap.createBitmap(IntArray(w * h) { if (empty(px[it])) -1 else 0xFF000000.toInt() }, w, h, Bitmap.Config.ARGB_8888)
            for (i in px.indices) if (empty(px[i])) px[i] = 0xFF000000.toInt()
            val solid = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
            return AiSegment.fill(solid, mask)
        }
        return trim(px, w, h) ?: b
    }

    private fun project(src: Bitmap, f: Float): Bitmap {
        if (projection == 0) return src
        val w = src.width; val h = src.height
        val sp = IntArray(w * h); src.getPixels(sp, 0, w, 0, 0, w, h)
        val cx = (w - 1) / 2f; val cy = (h - 1) / 2f
        val ow: Int; val oh: Int
        if (projection == 1) { ow = w; oh = (2 * f * kotlin.math.atan((h / 2f) / f)).toInt().coerceAtLeast(2) }
        else {
            val half = (w / 2f) / f
            // Straight lines only work below ~140° across; wider stays cylindrical.
            if (half > 1.2f) return src
            ow = (2 * f * kotlin.math.tan(half)).toInt().coerceIn(2, w * 4); oh = (h / kotlin.math.cos(half)).toInt().coerceIn(2, h * 3)
        }
        val ocx = (ow - 1) / 2f; val ocy = (oh - 1) / 2f
        val out = IntArray(ow * oh)
        for (y in 0 until oh) for (x in 0 until ow) {
            val xs: Float; val ys: Float
            if (projection == 1) { xs = x.toFloat(); ys = cy + f * kotlin.math.tan((y - ocy) / f) }
            else { val th = kotlin.math.atan((x - ocx) / f); xs = cx + f * th; ys = cy + (y - ocy) * kotlin.math.cos(th) }
            if (xs < 0f || ys < 0f || xs > w - 1f || ys > h - 1f) continue
            out[y * ow + x] = sp[ys.toInt() * w + xs.toInt()]
        }
        return Bitmap.createBitmap(out, ow, oh, Bitmap.Config.ARGB_8888)
    }

    /** Stretches each column so the picture reaches the top and bottom edges ([amount] 0..1). */
    private fun boundaryWarp(src: Bitmap, amount: Float): Bitmap {
        val w = src.width; val h = src.height
        val sp = IntArray(w * h); src.getPixels(sp, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (x in 0 until w) {
            var t = 0; while (t < h && empty(sp[t * w + x])) t++
            var b = h - 1; while (b > t && empty(sp[b * w + x])) b--
            if (b - t < 4) continue
            val tt = t * (1f - amount); val bb = b + (h - 1 - b) * amount
            for (y in 0 until h) {
                if (y < tt || y > bb) continue
                val ys = t + (y - tt) * (b - t) / (bb - tt).coerceAtLeast(1f)
                out[y * w + x] = sp[ys.toInt().coerceIn(0, h - 1) * w + x]
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Biggest band without empty corners (like the normal stitch). */
    private fun trim(px: IntArray, w: Int, h: Int): Bitmap? {
        val tops = IntArray(w); val bottoms = IntArray(w)
        for (x in 0 until w) {
            var y = 0; while (y < h && empty(px[y * w + x])) y++; tops[x] = y
            y = h - 1; while (y >= 0 && empty(px[y * w + x])) y--; bottoms[x] = y
        }
        val cov = (0 until w).filter { bottoms[it] - tops[it] > h / 3 }
        if (cov.isEmpty()) return null
        val ts = cov.map { tops[it] }.sorted(); val bs = cov.map { bottoms[it] }.sorted()
        val top = ts[(ts.size * 0.97).toInt().coerceAtMost(ts.size - 1)]; val bottom = bs[(bs.size * 0.03).toInt()]
        var l = cov.first(); var r = cov.last()
        while (l < r && (tops[l] > top + 2 || bottoms[l] < bottom - 2)) l++
        while (r > l && (tops[r] > top + 2 || bottoms[r] < bottom - 2)) r--
        val cw = r - l + 1; val ch = bottom - top + 1
        if (cw < 32 || ch < 32) return null
        val b = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        b.setPixels(px, top * w + l, w, 0, 0, cw, ch)
        return b
    }
}
