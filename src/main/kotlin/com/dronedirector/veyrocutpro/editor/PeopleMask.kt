package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * People masks: parts of every person in the photo — face skin, body skin, eye whites, irises,
 * eyebrows, lips, teeth, hair, clothes — on the phone (ML Kit face contours and person
 * segmentation, plus the colours of skin and hair measured in this photo).
 */
object PeopleMask {
    val PARTS = listOf("Whole person", "Face skin", "Body skin", "Eye whites", "Irises", "Eyebrows", "Lips", "Teeth", "Hair", "Clothes")
    /** Parts that need a face to be found. */
    private val FACE_PARTS = setOf(1, 3, 4, 5, 6, 7, 8)
    private const val WORK = 1600

    private class FaceGeo(val oval: List<PointF>, val eyes: List<List<PointF>>, val brows: List<List<PointF>>, val lipsOuter: List<PointF>, val mouthInner: List<PointF>, val box: Rect)

    @Volatile var lastFaces = 0; private set

    /** Grey mask (white = selected) at [src]'s size, or null when nothing could be found. Blocking; call off the main thread. */
    fun make(context: Context, src: Bitmap, parts: List<Int>): Bitmap? {
        val k = min(1f, WORK.toFloat() / max(src.width, src.height))
        val w = max(1, (src.width * k).toInt()); val h = max(1, (src.height * k).toInt())
        val b = if (k < 1f) Bitmap.createScaledBitmap(src, w, h, true) else src
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        val person = personMask(b, w, h)
        val faces = findFaces(b)
        lastFaces = faces.size
        if (person == null && faces.isEmpty()) return null
        val pm = person ?: FloatArray(w * h)
        if (parts.all { it in FACE_PARTS } && faces.isEmpty()) return null

        val oval = raster(w, h, faces.map { it.oval })
        val eyes = raster(w, h, faces.flatMap { it.eyes })
        val brows = raster(w, h, faces.flatMap { it.brows })
        val outer = raster(w, h, faces.map { it.lipsOuter })
        val inner = raster(w, h, faces.map { it.mouthInner })
        val lum = FloatArray(w * h) { val c = px[it]; (0.299f * ((c shr 16) and 255) + 0.587f * ((c shr 8) and 255) + 0.114f * (c and 255)) / 255f }

        // Skin colour of this photo (cheeks and forehead inside the face outline), else a general skin range.
        val skin = skinProb(px, w, h, oval, eyes, brows, outer)
        val skinFace = FloatArray(w * h) { oval[it] * (1f - eyes[it]) * (1f - brows[it]) * (1f - outer[it]) * skin[it] }
        val ovalWide = grow(oval, w, h, max(2, w / 120))

        val iris = irises(eyes, lum, w, h)
        val hair = if (8 in parts || 9 in parts) hair(px, w, h, pm, skin, ovalWide, faces) else FloatArray(w * h)

        val teethA = if (7 in parts) teeth(px, inner, lum, w, h) else FloatArray(0)
        val out = FloatArray(w * h)
        for (p in parts) for (i in out.indices) {
            val v = when (p) {
                0 -> pm[i]
                1 -> skinFace[i]
                2 -> pm[i] * skin[i] * (1f - ovalWide[i])
                3 -> eyes[i] * (1f - iris[i]) * (if (lum[i] > 0.35f) 1f else 0.3f)
                4 -> iris[i]
                5 -> brows[i]
                6 -> (outer[i] - inner[i]).coerceAtLeast(0f)
                7 -> teethA[i]
                8 -> hair[i]
                9 -> (pm[i] * (1f - skin[i]) * (1f - hair[i]) * (1f - ovalWide[i])).coerceAtLeast(0f)
                else -> 0f
            }
            if (v > out[i]) out[i] = v
        }
        val soft = blur(out, w, h, max(1, w / 800))
        if (soft.none { it > 0.1f }) return null
        val mp = IntArray(w * h) { val v = (soft[it] * 255).toInt().coerceIn(0, 255); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        val m = Bitmap.createBitmap(mp, w, h, Bitmap.Config.ARGB_8888)
        if (b !== src) b.recycle()
        return if (k < 1f) Bitmap.createScaledBitmap(m, src.width, src.height, true) else m
    }

    // ---------------------------------------------------------------- finding people and faces

    private fun personMask(b: Bitmap, w: Int, h: Int): FloatArray? = try {
        val seg = Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build())
        try {
            val m = Tasks.await(seg.process(InputImage.fromBitmap(b, 0)), 30, TimeUnit.SECONDS)
            val buf = m.buffer.order(ByteOrder.nativeOrder()); buf.rewind()
            val raw = FloatArray(m.width * m.height) { buf.float }
            val out = if (m.width == w && m.height == h) raw else FloatArray(w * h) { i -> raw[(i / w * m.height / h) * m.width + (i % w) * m.width / w] }
            if (out.count { it > 0.5f } < out.size * 0.005f) null else out
        } finally { seg.close() }
    } catch (_: Throwable) { null }

    private fun findFaces(b: Bitmap): List<FaceGeo> {
        val out = ArrayList<FaceGeo>()
        try {
            val boxes = FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).setMinFaceSize(0.04f).build())
            val faces = try { Tasks.await(boxes.process(InputImage.fromBitmap(b, 0)), 30, TimeUnit.SECONDS) } finally { boxes.close() }
            val contour = FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL).build())
            try {
                // Contours come for the biggest face in a picture, so each face is looked at on its own.
                for (f in faces.take(12)) {
                    val r = f.boundingBox
                    val side = (max(r.width(), r.height()) * 2.0f).toInt()
                    val x0 = (r.centerX() - side / 2).coerceIn(0, max(0, b.width - 1)); val y0 = (r.centerY() - side / 2).coerceIn(0, max(0, b.height - 1))
                    val cw = min(side, b.width - x0); val ch = min(side, b.height - y0)
                    if (cw < 16 || ch < 16) continue
                    val crop = Bitmap.createBitmap(b, x0, y0, cw, ch)
                    val s = min(1f, 720f / max(cw, ch))
                    val small = if (s < 1f) Bitmap.createScaledBitmap(crop, (cw * s).toInt(), (ch * s).toInt(), true) else crop
                    val got = try { Tasks.await(contour.process(InputImage.fromBitmap(small, 0)), 20, TimeUnit.SECONDS) } catch (_: Throwable) { null }
                    val face = got?.maxByOrNull { it.boundingBox.width() } ?: continue
                    fun c(t: Int) = face.getContour(t)?.points.orEmpty().map { PointF(x0 + it.x / s, y0 + it.y / s) }
                    val oval = c(FaceContour.FACE)
                    if (oval.size < 8) continue
                    val upTop = c(FaceContour.UPPER_LIP_TOP).sortedBy { it.x }; val upBot = c(FaceContour.UPPER_LIP_BOTTOM).sortedBy { it.x }
                    val loTop = c(FaceContour.LOWER_LIP_TOP).sortedBy { it.x }; val loBot = c(FaceContour.LOWER_LIP_BOTTOM).sortedBy { it.x }
                    fun brow(top: Int, bot: Int) = c(top).sortedBy { it.x } + c(bot).sortedByDescending { it.x }
                    out += FaceGeo(
                        oval = oval,
                        eyes = listOf(c(FaceContour.LEFT_EYE), c(FaceContour.RIGHT_EYE)).filter { it.size >= 6 },
                        brows = listOf(brow(FaceContour.LEFT_EYEBROW_TOP, FaceContour.LEFT_EYEBROW_BOTTOM), brow(FaceContour.RIGHT_EYEBROW_TOP, FaceContour.RIGHT_EYEBROW_BOTTOM)).filter { it.size >= 6 },
                        lipsOuter = upTop + loBot.reversed(),
                        mouthInner = upBot + loTop.reversed(),
                        box = Rect((x0 + face.boundingBox.left / s).toInt(), (y0 + face.boundingBox.top / s).toInt(), (x0 + face.boundingBox.right / s).toInt(), (y0 + face.boundingBox.bottom / s).toInt()),
                    )
                    if (small !== crop) small.recycle()
                    crop.recycle()
                }
            } finally { contour.close() }
        } catch (_: Throwable) {}
        return out
    }

    // ---------------------------------------------------------------- the parts

    /** Fills the outlines (anti-aliased), 0..1 per pixel. */
    private fun raster(w: Int, h: Int, polys: List<List<PointF>>): FloatArray {
        val out = FloatArray(w * h)
        val ok = polys.filter { it.size >= 3 }
        if (ok.isEmpty()) return out
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        for (p in ok) {
            val path = Path(); path.moveTo(p[0].x, p[0].y); for (q in p.drop(1)) path.lineTo(q.x, q.y); path.close()
            cv.drawPath(path, paint)
        }
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h); bmp.recycle()
        for (i in px.indices) out[i] = ((px[i] shr 16) and 255) / 255f
        return out
    }

    private fun ycc(c: Int): FloatArray {
        val r = ((c shr 16) and 255).toFloat(); val g = ((c shr 8) and 255).toFloat(); val b = (c and 255).toFloat()
        return floatArrayOf(0.299f * r + 0.587f * g + 0.114f * b, 128f - 0.1687f * r - 0.3313f * g + 0.5f * b, 128f + 0.5f * r - 0.4187f * g - 0.0813f * b)
    }

    /** How skin-like each pixel is (0..1), from the skin seen inside the faces. */
    private fun skinProb(px: IntArray, w: Int, h: Int, oval: FloatArray, eyes: FloatArray, brows: FloatArray, lips: FloatArray): FloatArray {
        var n = 0; var sb = 0.0; var sr = 0.0; var sbb = 0.0; var srr = 0.0; var sy = 0.0
        val step = max(1, (w * h) / 400_000)
        var i = 0
        while (i < px.size) {
            if (oval[i] > 0.9f && eyes[i] < 0.1f && brows[i] < 0.1f && lips[i] < 0.1f) {
                val v = ycc(px[i])
                if (v[0] > 30f && v[0] < 245f) { n++; sb += v[1]; sr += v[2]; sbb += v[1] * v[1]; srr += v[2] * v[2]; sy += v[0] }
            }
            i += step
        }
        val out = FloatArray(px.size)
        if (n > 40) {
            val mb = sb / n; val mr = sr / n
            val db = max(5.0, sqrt(max(0.0, sbb / n - mb * mb))) * 2.2; val dr = max(5.0, sqrt(max(0.0, srr / n - mr * mr))) * 2.2
            for (k in px.indices) {
                val v = ycc(px[k])
                if (v[0] < 18f) continue
                val zb = (v[1] - mb) / db; val zr = (v[2] - mr) / dr
                out[k] = exp(-0.5 * (zb * zb + zr * zr)).toFloat().let { ((it - 0.15f) / 0.6f).coerceIn(0f, 1f) }
            }
        } else for (k in px.indices) {
            val v = ycc(px[k])
            out[k] = if (v[0] > 30f && v[2] in 135f..175f && v[1] in 80f..130f) 1f else 0f
        }
        return out
    }

    /** Irises and pupils: the dark middle of each eye. */
    private fun irises(eyes: FloatArray, lum: FloatArray, w: Int, h: Int): FloatArray {
        val out = FloatArray(w * h)
        // Each eye on its own (they can differ in light): split by a coarse labelling of the eye areas.
        val seen = BooleanArray(w * h)
        val stack = IntArray(w * h)
        for (start in eyes.indices) {
            if (eyes[start] < 0.5f || seen[start]) continue
            val members = ArrayList<Int>()
            var sp = 0; stack[sp++] = start; seen[start] = true
            while (sp > 0) {
                val c = stack[--sp]; members += c
                val x = c % w; val y = c / w
                for ((dx, dy) in arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val q = ny * w + nx
                    if (!seen[q] && eyes[q] >= 0.5f) { seen[q] = true; stack[sp++] = q }
                }
            }
            if (members.size < 6) continue
            val ls = members.map { lum[it] }.sorted()
            // Otsu-like split between the white and the dark of the eye.
            val thr = (ls[ls.size / 5] + ls[ls.size * 3 / 4]) / 2f
            for (q in members) if (lum[q] < thr) out[q] = 1f
        }
        return blur(out, w, h, 1)
    }

    private fun teeth(px: IntArray, inner: FloatArray, lum: FloatArray, w: Int, h: Int): FloatArray {
        var n = 0; var s = 0f
        for (i in inner.indices) if (inner[i] > 0.5f) { n++; s += lum[i] }
        val mean = if (n > 0) s / n else 1f
        val t = FloatArray(w * h)
        for (i in inner.indices) {
            if (inner[i] < 0.2f) continue
            val c = px[i]; val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            val sat = if (mx == 0) 0f else (mx - mn).toFloat() / mx
            if (lum[i] > mean * 0.95f && sat < 0.42f) t[i] = inner[i]
        }
        return blur(t, w, h, 1)
    }

    /** Hair: what's on the person around the head that looks like the hair just above the face. */
    private fun hair(px: IntArray, w: Int, h: Int, person: FloatArray, skin: FloatArray, oval: FloatArray, faces: List<FaceGeo>): FloatArray {
        val out = FloatArray(w * h)
        for (f in faces) {
            val fw = max(1, f.box.width()); val fh = max(1, f.box.height())
            val top = f.oval.minOf { it.y }; val cx = f.oval.map { it.x }.average().toFloat()
            // Sample the hair colour in the band above the forehead.
            var n = 0; var sr = 0.0; var sg = 0.0; var sb = 0.0; var srr = 0.0; var sgg = 0.0; var sbb = 0.0
            val yA = (top - fh * 0.35f).toInt().coerceIn(0, h - 1); val yB = (top + fh * 0.02f).toInt().coerceIn(0, h - 1)
            val xA = (cx - fw * 0.35f).toInt().coerceIn(0, w - 1); val xB = (cx + fw * 0.35f).toInt().coerceIn(0, w - 1)
            for (y in yA..yB) for (x in xA..xB) {
                val i = y * w + x
                if (person[i] < 0.5f || skin[i] > 0.4f || oval[i] > 0.5f) continue
                val c = px[i]; val r = ((c shr 16) and 255).toDouble(); val g = ((c shr 8) and 255).toDouble(); val b = (c and 255).toDouble()
                n++; sr += r; sg += g; sb += b; srr += r * r; sgg += g * g; sbb += b * b
            }
            if (n < 30) continue
            val mr = sr / n; val mg = sg / n; val mb = sb / n
            val sd = max(16.0, sqrt(max(0.0, (srr / n - mr * mr + sgg / n - mg * mg + sbb / n - mb * mb) / 3))) * 2.0
            // Around the head: from well above it to below the shoulders (long hair).
            val y0 = (top - fh * 0.9f).toInt().coerceIn(0, h - 1); val y1 = (f.box.bottom + fh * 1.6f).toInt().coerceIn(0, h - 1)
            val x0 = (cx - fw * 1.4f).toInt().coerceIn(0, w - 1); val x1 = (cx + fw * 1.4f).toInt().coerceIn(0, w - 1)
            val ccy = (top + f.box.bottom) / 2f; val rx = fw * 1.4f; val ryUp = ccy - (top - fh * 0.9f); val ryDown = (f.box.bottom + fh * 1.6f) - ccy
            for (y in y0..y1) for (x in x0..x1) {
                val i = y * w + x
                if (person[i] < 0.3f || oval[i] > 0.6f) continue
                val ex = (x - cx) / rx; val ey = (y - ccy) / (if (y < ccy) ryUp else ryDown)
                val inside = 1f - ((ex * ex + ey * ey) - 0.8f).coerceIn(0f, 0.2f) / 0.2f
                if (inside <= 0f) continue
                val c = px[i]
                val dr = ((c shr 16) and 255) - mr; val dg = ((c shr 8) and 255) - mg; val db = (c and 255) - mb
                val sim = exp(-(dr * dr + dg * dg + db * db) / (2 * sd * sd * 3)).toFloat()
                val v = person[i] * inside * ((sim - 0.2f) / 0.5f).coerceIn(0f, 1f) * (1f - skin[i] * 0.8f)
                out[i] = max(out[i], v)
            }
        }
        return out
    }

    // ---------------------------------------------------------------- helpers

    private fun grow(a: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val b = blur(a, w, h, r)
        return FloatArray(a.size) { max(a[it], (b[it] * 3f).coerceAtMost(1f)) }
    }

    private fun blur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        if (r <= 0) return src.copyOf()
        val tmp = FloatArray(w * h); val out = FloatArray(w * h); val d = 2f * r + 1
        for (y in 0 until h) {
            var s = 0f
            for (x in -r..r) s += src[y * w + x.coerceIn(0, w - 1)]
            for (x in 0 until w) { tmp[y * w + x] = s / d; s += src[y * w + (x + r + 1).coerceAtMost(w - 1)] - src[y * w + (x - r).coerceAtLeast(0)] }
        }
        for (x in 0 until w) {
            var s = 0f
            for (y in -r..r) s += tmp[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) { out[y * w + x] = s / d; s += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x] }
        }
        return out
    }

}
