package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Smart crop: crops a photo to a shape (1:1, 4:5, 9:16…) with the main subject kept in the picture.
 * Faces first (placed a little above the middle, with headroom), then the AI subject, then the busiest
 * (most detailed) part of the photo. Own code.
 */
object SmartCrop {
    val SHAPES = listOf("1:1" to 1f, "4:5" to 0.8f, "9:16" to 9f / 16f, "16:9" to 16f / 9f, "3:2" to 1.5f)

    class Result(val geometry: Geometry, val found: String)

    fun solve(context: Context, src: Bitmap, g: Geometry, aspect: Float): Result {
        // The photo as shown before cropping: quarter turns and flips (small, for finding the subject).
        val turns = ((g.rotate90 % 4) + 4) % 4
        val k = min(1f, 640f / maxOf(src.width, src.height))
        val m = Matrix().apply { postScale(k * (if (g.flipH) -1f else 1f), k * (if (g.flipV) -1f else 1f)); postRotate(turns * 90f) }
        val small = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        val w = small.width.toFloat(); val h = small.height.toFloat()
        // Crop size (as a share of the picture) at this shape, like ImageOps.geometry does.
        var cw: Float; var ch: Float
        if (aspect > w / h) { cw = w; ch = cw / aspect } else { ch = h; cw = ch * aspect }
        val a = Math.toRadians(abs(g.straighten).toDouble())
        val cosA = cos(a).toFloat(); val sinA = sin(a).toFloat()
        val fit = min(w / (cw * cosA + ch * sinA), h / (cw * sinA + ch * cosA)).coerceAtMost(1f)
        cw *= fit; ch *= fit

        var cx: Float; var cy: Float; val found: String
        val faces = faces(small)
        val subject = if (faces == null) try { AiSegment.subjectBox(context, small) } catch (_: Throwable) { null } else null
        when {
            faces != null -> {
                cx = (faces[0] + faces[2]) / 2f * w
                // Eyes about a third from the top: the crop's centre a bit below the faces.
                cy = (faces[1] + faces[3]) / 2f * h + ch * 0.14f
                found = "face" + if (faces[4] > 1f) "s" else ""
            }
            subject != null -> {
                cx = (subject[0] + subject[2]) / 2f * w
                cy = (subject[1] + subject[3]) / 2f * h
                // A tall subject (a person standing, a tower) keeps its top in the crop.
                if ((subject[3] - subject[1]) * h > ch) cy = subject[1] * h + ch * 0.45f
                found = "subject"
            }
            else -> {
                val p = busiest(small); cx = p[0] * w; cy = p[1] * h; found = "most detailed part"
            }
        }
        if (small !== src) small.recycle()
        val px = (w - cw) / 2f; val py = (h - ch) / 2f
        val ox = if (px > 0.5f) ((cx - w / 2f) / px).coerceIn(-1f, 1f) else 0f
        val oy = if (py > 0.5f) ((cy - h / 2f) / py).coerceIn(-1f, 1f) else 0f
        return Result(g.copy(aspect = aspect, zoom = 1f, offsetX = ox, offsetY = oy), found)
    }

    /** Box round all faces (x0, y0, x1, y1 as 0..1, then the count), or null. Small faces in a crowd count less. */
    private fun faces(b: Bitmap): FloatArray? {
        val det = com.google.mlkit.vision.face.FaceDetection.getClient(
            com.google.mlkit.vision.face.FaceDetectorOptions.Builder()
                .setPerformanceMode(com.google.mlkit.vision.face.FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).build(),
        )
        return try {
            val all = com.google.android.gms.tasks.Tasks.await(det.process(com.google.mlkit.vision.common.InputImage.fromBitmap(b, 0)))
            if (all.isEmpty()) return null
            val big = all.maxOf { it.boundingBox.width() }
            val fs = all.filter { it.boundingBox.width() >= big * 0.4f }
            val x0 = fs.minOf { it.boundingBox.left }.coerceAtLeast(0); val y0 = fs.minOf { it.boundingBox.top }.coerceAtLeast(0)
            val x1 = fs.maxOf { it.boundingBox.right }.coerceAtMost(b.width); val y1 = fs.maxOf { it.boundingBox.bottom }.coerceAtMost(b.height)
            floatArrayOf(x0 / b.width.toFloat(), y0 / b.height.toFloat(), x1 / b.width.toFloat(), y1 / b.height.toFloat(), fs.size.toFloat())
        } catch (_: Throwable) { null } finally { try { det.close() } catch (_: Throwable) {} }
    }

    /** Centre (0..1) of the most detailed part: where edges and colour contrast gather. */
    private fun busiest(b: Bitmap): FloatArray {
        val n = 48
        val s = Bitmap.createScaledBitmap(b, n, n, true)
        val px = IntArray(n * n); s.getPixels(px, 0, n, 0, 0, n, n)
        if (s !== b) s.recycle()
        fun lum(i: Int) = ((px[i] shr 16 and 0xFF) * 0.3f + (px[i] shr 8 and 0xFF) * 0.59f + (px[i] and 0xFF) * 0.11f)
        fun sat(i: Int): Float { val r = px[i] shr 16 and 0xFF; val g = px[i] shr 8 and 0xFF; val bb = px[i] and 0xFF; return (maxOf(r, g, bb) - minOf(r, g, bb)).toFloat() }
        var sw = 0f; var sx = 0f; var sy = 0f
        for (y in 1 until n - 1) for (x in 1 until n - 1) {
            val i = y * n + x
            val e = abs(lum(i + 1) - lum(i - 1)) + abs(lum(i + n) - lum(i - n)) + sat(i) * 0.3f
            // A little pull to the middle, so a busy edge alone doesn't win.
            val dx = x / (n - 1f) - 0.5f; val dy = y / (n - 1f) - 0.5f
            val v = e * e * (1f - (dx * dx + dy * dy))
            sw += v; sx += v * x; sy += v * y
        }
        return if (sw <= 0f) floatArrayOf(0.5f, 0.5f) else floatArrayOf(sx / sw / (n - 1), sy / sw / (n - 1))
    }
}
