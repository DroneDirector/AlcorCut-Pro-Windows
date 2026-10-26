package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * AI culling for the filmstrip, on the phone: finds blurry shots and closed eyes, groups
 * near-identical shots (bursts, the same scene again) and picks the best of each group — the
 * sharpest, with open eyes and smiles. The photos themselves are never changed; only their flags
 * and a short note on the thumbnail.
 */
object Cull {
    /** Progress while it runs (done / total). */
    var progress by mutableStateOf<Pair<Int, Int>?>(null)

    private class Info(val key: String, val sharp: Float, val faces: Int, val closed: Boolean, val smile: Float, val hash: Long, val hist: FloatArray)

    data class Result(val rejected: Int, val picked: Int, val groups: Int)

    /** Looks at the photos [keys] (SessionPhoto.key) and flags them. Blocking; call off the main thread. */
    fun run(context: Context, keys: List<String>, rejectBlurry: Boolean, rejectClosed: Boolean, pickBest: Boolean, stop: () -> Boolean): Result? {
        val list = keys.mapNotNull { k -> PhotoSession.items.firstOrNull { it.key == k } }
        if (list.isEmpty()) return null
        val det = FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).setMinFaceSize(0.06f).build())
        val infos = ArrayList<Info>()
        try {
            progress = 0 to list.size
            for ((n, p) in list.withIndex()) {
                if (stop()) return null
                val b = try { com.dronedirector.veyrocutpro.camera.GradeIO.decode(context, Uri.parse(p.uri), 1024) } catch (_: Throwable) { null }
                if (b != null) {
                    var faces = 0; var closed = false; var smile = 0f
                    try {
                        val fs = Tasks.await(det.process(InputImage.fromBitmap(b, 0)), 20, TimeUnit.SECONDS)
                        for (f in fs) {
                            if (f.boundingBox.width() < b.width * 0.05f) continue
                            faces++
                            val l = f.leftEyeOpenProbability; val r = f.rightEyeOpenProbability
                            if (l != null && r != null && l < 0.25f && r < 0.25f) closed = true
                            smile += f.smilingProbability ?: 0f
                        }
                    } catch (_: Throwable) {}
                    infos += Info(p.key, sharpness(b), faces, closed, if (faces > 0) smile / faces else 0f, dHash(b), hist(b))
                    b.recycle()
                }
                progress = n + 1 to list.size
            }
        } finally { try { det.close() } catch (_: Throwable) {}; progress = null }
        if (infos.isEmpty()) return null

        // How sharp counts as blurry: well below the others in this set, and soft in absolute terms too.
        val med = infos.map { it.sharp }.sorted()[infos.size / 2]
        fun blurry(i: Info) = i.sharp < 0.4f * med && i.sharp < 60f || i.sharp < 12f

        // Similar shots: next to each other in the filmstrip with a close picture fingerprint.
        val groups = ArrayList<MutableList<Info>>()
        for (i in infos) {
            val g = groups.lastOrNull()
            if (g != null && similar(g.last(), i)) g += i else groups += mutableListOf(i)
        }
        var rej = 0; var pick = 0; var multi = 0
        val notes = HashMap<String, Pair<Int, String>>()   // key → flag, note
        for (g in groups) {
            val best = g.maxByOrNull { score(it, med) }
            if (g.size > 1) multi++
            for (i in g) {
                val reasons = ArrayList<String>()
                if (rejectBlurry && blurry(i)) reasons += "Blurry"
                if (rejectClosed && i.closed) reasons += "Eyes closed"
                when {
                    reasons.isNotEmpty() -> { notes[i.key] = -1 to reasons.joinToString(", "); rej++ }
                    pickBest && g.size > 1 && i === best -> { notes[i.key] = 1 to "Best of ${g.size}"; pick++ }
                    g.size > 1 -> notes[i.key] = 0 to "Similar (${g.size})"
                    else -> notes[i.key] = 0 to "OK"
                }
            }
        }
        for ((k, v) in notes) {
            val idx = PhotoSession.items.indexOfFirst { it.key == k }
            if (idx >= 0) PhotoSession.update(context, idx) { it.copy(flag = if (v.first != 0) v.first else it.flag, cull = v.second) }
        }
        return Result(rej, pick, multi)
    }

    private fun score(i: Info, med: Float): Float = (i.sharp / max(1f, med)).coerceAtMost(2f) + (if (i.closed) -1.5f else 0f) + i.smile * 0.4f + (if (i.faces > 0) 0.1f else 0f)

    private fun similar(a: Info, b: Info): Boolean {
        val ham = java.lang.Long.bitCount(a.hash xor b.hash)
        var d = 0f; for (k in a.hist.indices) d += kotlin.math.abs(a.hist[k] - b.hist[k])
        return ham <= 14 && d < 0.6f
    }

    /** Sharpness: the sharpest part of the picture (so a sharp subject on a soft background counts as sharp). */
    private fun sharpness(b: Bitmap): Float {
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        val g = FloatArray(w * h) { val c = px[it]; 0.299f * ((c shr 16) and 255) + 0.587f * ((c shr 8) and 255) + 0.114f * (c and 255) }
        val tiles = 4
        var best = 0f
        for (ty in 0 until tiles) for (tx in 0 until tiles) {
            val x0 = max(1, tx * w / tiles); val x1 = min(w - 1, (tx + 1) * w / tiles)
            val y0 = max(1, ty * h / tiles); val y1 = min(h - 1, (ty + 1) * h / tiles)
            var s = 0.0; var s2 = 0.0; var n = 0
            for (y in y0 until y1 step 2) for (x in x0 until x1 step 2) {
                val i = y * w + x
                val lap = 4 * g[i] - g[i - 1] - g[i + 1] - g[i - w] - g[i + w]
                s += lap; s2 += lap * lap; n++
            }
            if (n > 0) { val m = s / n; best = max(best, (s2 / n - m * m).toFloat()) }
        }
        return best
    }

    /** 64-bit difference hash of the picture. */
    private fun dHash(b: Bitmap): Long {
        val s = Bitmap.createScaledBitmap(b, 9, 8, true)
        val px = IntArray(72); s.getPixels(px, 0, 9, 0, 0, 9, 8); s.recycle()
        fun l(c: Int) = ((c shr 16) and 255) * 3 + ((c shr 8) and 255) * 6 + (c and 255)
        var h = 0L
        for (y in 0 until 8) for (x in 0 until 8) { h = h shl 1; if (l(px[y * 9 + x]) > l(px[y * 9 + x + 1])) h = h or 1L }
        return h
    }

    /** Small colour histogram (4 × 4 × 4), adding to 1. */
    private fun hist(b: Bitmap): FloatArray {
        val s = Bitmap.createScaledBitmap(b, 64, 64, true)
        val px = IntArray(64 * 64); s.getPixels(px, 0, 64, 0, 0, 64, 64); s.recycle()
        val h = FloatArray(64)
        for (c in px) h[(((c shr 22) and 3) shl 4) or (((c shr 14) and 3) shl 2) or ((c shr 6) and 3)] += 1f / px.size
        return h
    }
}
