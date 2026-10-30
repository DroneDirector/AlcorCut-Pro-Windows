package com.google.mlkit.vision.face

import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage

/** Face finding on Windows with YuNet (MIT, from OpenCV), downloaded with the AI models. Own code. */
class Face(
    val boundingBox: android.graphics.Rect = android.graphics.Rect(),
    private val marks: Map<Int, FaceLandmark> = emptyMap(),
    val leftEyeOpenProbability: Float? = null,
    val rightEyeOpenProbability: Float? = null,
    val smilingProbability: Float? = null,
    val headEulerAngleY: Float = 0f,
    val headEulerAngleZ: Float = 0f,
    val trackingId: Int? = null,
    private val contours: Map<Int, FaceContour> = emptyMap(),
) {
    fun getLandmark(type: Int): FaceLandmark? = marks[type]
    fun getContour(type: Int): FaceContour? = contours[type]
    val allContours: List<FaceContour> get() = contours.values.toList()
    val allLandmarks: List<FaceLandmark> get() = marks.values.toList()
}

/** A point on a face (eye, nose, mouth corner…) in picture pixels. */
class FaceLandmark(val landmarkType: Int, val position: android.graphics.PointF) {
    companion object {
        const val MOUTH_BOTTOM = 0; const val LEFT_CHEEK = 1; const val LEFT_EAR = 3; const val LEFT_EYE = 4
        const val MOUTH_LEFT = 5; const val NOSE_BASE = 6; const val RIGHT_CHEEK = 7; const val RIGHT_EAR = 9
        const val RIGHT_EYE = 10; const val MOUTH_RIGHT = 11
    }
}

/** An outline on a face. On Windows these are drawn from the five points YuNet finds (eyes, nose, mouth corners). */
class FaceContour(val faceContourType: Int, val points: List<android.graphics.PointF>) {
    companion object {
        const val FACE = 1; const val LEFT_EYEBROW_TOP = 2; const val LEFT_EYEBROW_BOTTOM = 3; const val RIGHT_EYEBROW_TOP = 4
        const val RIGHT_EYEBROW_BOTTOM = 5; const val LEFT_EYE = 6; const val RIGHT_EYE = 7; const val UPPER_LIP_TOP = 8
        const val UPPER_LIP_BOTTOM = 9; const val LOWER_LIP_TOP = 10; const val LOWER_LIP_BOTTOM = 11; const val NOSE_BRIDGE = 12
        const val NOSE_BOTTOM = 13; const val LEFT_CHEEK = 14; const val RIGHT_CHEEK = 15
    }
}

class FaceDetectorOptions private constructor(internal val accurate: Boolean, internal val minFace: Float) {
    class Builder {
        private var accurate = false; private var minFace = 0.1f
        fun setPerformanceMode(m: Int) = apply { accurate = m == PERFORMANCE_MODE_ACCURATE }
        fun setClassificationMode(m: Int) = this
        fun setLandmarkMode(m: Int) = this
        fun setContourMode(m: Int) = this
        fun setMinFaceSize(f: Float) = apply { minFace = f }
        fun enableTracking() = this
        fun build() = FaceDetectorOptions(accurate, minFace)
    }
    companion object {
        const val PERFORMANCE_MODE_FAST = 1; const val PERFORMANCE_MODE_ACCURATE = 2
        const val CLASSIFICATION_MODE_NONE = 1; const val CLASSIFICATION_MODE_ALL = 2
        const val LANDMARK_MODE_NONE = 1; const val LANDMARK_MODE_ALL = 2
        const val CONTOUR_MODE_NONE = 1; const val CONTOUR_MODE_ALL = 2
    }
}

class FaceDetector internal constructor(private val o: FaceDetectorOptions) {
    fun process(i: InputImage): Task<List<Face>> = try {
        Task(YuNet.detect(i.bitmap, if (o.accurate) 0.6f else 0.7f, o.minFace), null)
    } catch (e: Exception) { Task(null, e) }
    fun close() {}
}

object FaceDetection {
    fun getClient(o: FaceDetectorOptions): FaceDetector = FaceDetector(o)
}

internal object YuNet {
    const val NAME = "face_detection_yunet_2023mar.onnx"
    private const val SIZE = 640
    private var session: ai.onnxruntime.OrtSession? = null
    private val lock = Any()

    private fun session(): ai.onnxruntime.OrtSession? = synchronized(lock) {
        session ?: run {
            val f = com.dronedirector.veyrocutpro.editor.ModelStore.file(android.content.Context.app, NAME)
            if (!f.exists() || java.io.File(f.path + ".part").exists()) return null
            val env = ai.onnxruntime.OrtEnvironment.getEnvironment()
            val so = ai.onnxruntime.OrtSession.SessionOptions().apply { setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 8)) }
            env.createSession(f.path, so).also { session = it }
        }
    }

    private class Hit(val score: Float, val x: Float, val y: Float, val w: Float, val h: Float, val kp: FloatArray)

    fun detect(b: android.graphics.Bitmap, threshold: Float, minFace: Float): List<Face> {
        val s = session() ?: return emptyList()
        val w = b.width; val h = b.height
        if (w < 8 || h < 8) return emptyList()
        // The picture fitted into 640 × 640 (top left, the rest black), as BGR 0–255.
        val k = SIZE.toFloat() / maxOf(w, h)
        val sw = (w * k).toInt().coerceIn(1, SIZE); val sh = (h * k).toInt().coerceIn(1, SIZE)
        val small = android.graphics.Bitmap.createScaledBitmap(b, sw, sh, true)
        val px = IntArray(sw * sh); small.getPixels(px, 0, sw, 0, 0, sw, sh)
        val plane = SIZE * SIZE
        val data = FloatArray(3 * plane)
        for (y in 0 until sh) for (x in 0 until sw) {
            val c = px[y * sw + x]; val o = y * SIZE + x
            data[o] = (c and 0xFF).toFloat(); data[plane + o] = ((c shr 8) and 0xFF).toFloat(); data[2 * plane + o] = ((c shr 16) and 0xFF).toFloat()
        }
        val env = ai.onnxruntime.OrtEnvironment.getEnvironment()
        val hits = ArrayList<Hit>()
        ai.onnxruntime.OnnxTensor.createTensor(env, java.nio.FloatBuffer.wrap(data), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { t ->
            s.run(mapOf(s.inputNames.first() to t)).use { r ->
                fun out(n: String): FloatArray? {
                    val v = r.get(n).orElse(null) as? ai.onnxruntime.OnnxTensor ?: return null
                    val fb = v.floatBuffer; return FloatArray(fb.remaining()).also { fb.get(it) }
                }
                for (st in intArrayOf(8, 16, 32)) {
                    val cls = out("cls_$st") ?: continue; val obj = out("obj_$st") ?: continue
                    val box = out("bbox_$st") ?: continue; val kps = out("kps_$st") ?: continue
                    val cols = SIZE / st
                    for (i in cls.indices) {
                        val sc = kotlin.math.sqrt(cls[i].coerceIn(0f, 1f) * obj[i].coerceIn(0f, 1f))
                        if (sc < threshold) continue
                        val r0 = i / cols; val c0 = i % cols
                        val cx = (c0 + box[i * 4]) * st; val cy = (r0 + box[i * 4 + 1]) * st
                        val bw = kotlin.math.exp(box[i * 4 + 2]) * st; val bh = kotlin.math.exp(box[i * 4 + 3]) * st
                        val kp = FloatArray(10) { j -> if (j % 2 == 0) (kps[i * 10 + j] + c0) * st else (kps[i * 10 + j] + r0) * st }
                        hits += Hit(sc, cx - bw / 2, cy - bh / 2, bw, bh, kp)
                    }
                }
            }
        }
        // Overlapping finds of the same face: keep the best.
        hits.sortByDescending { it.score }
        val kept = ArrayList<Hit>()
        for (a in hits) {
            if (kept.size >= 50) break
            if (kept.none { iou(it, a) > 0.3f }) kept += a
        }
        val inv = 1f / k; val long = maxOf(w, h)
        return kept.filter { it.w * inv >= minFace * long * 0.5f }.map { f ->
            fun p(j: Int) = android.graphics.PointF((f.kp[j * 2] * inv).coerceIn(0f, w - 1f), (f.kp[j * 2 + 1] * inv).coerceIn(0f, h - 1f))
            // YuNet's points: the person's right eye, left eye, nose tip, right and left mouth corners.
            val re = p(0); val le = p(1); val nose = p(2); val mr = p(3); val ml = p(4)
            val box = android.graphics.Rect((f.x * inv).toInt().coerceAtLeast(0), (f.y * inv).toInt().coerceAtLeast(0),
                ((f.x + f.w) * inv).toInt().coerceAtMost(w), ((f.y + f.h) * inv).toInt().coerceAtMost(h))
            val ex = le.x - re.x; val ey = le.y - re.y
            val eyeDist = kotlin.math.hypot(ex, ey).coerceAtLeast(1f)
            val roll = -Math.toDegrees(kotlin.math.atan2(ey, ex).toDouble()).toFloat()
            val yaw = ((nose.x - (le.x + re.x) / 2f) / eyeDist * 90f).coerceIn(-60f, 60f)
            val cheekY = (nose.y + (le.y + re.y) / 2f) / 2f
            val marks = mapOf(
                FaceLandmark.LEFT_EYE to FaceLandmark(FaceLandmark.LEFT_EYE, le),
                FaceLandmark.RIGHT_EYE to FaceLandmark(FaceLandmark.RIGHT_EYE, re),
                FaceLandmark.NOSE_BASE to FaceLandmark(FaceLandmark.NOSE_BASE, nose),
                FaceLandmark.MOUTH_LEFT to FaceLandmark(FaceLandmark.MOUTH_LEFT, ml),
                FaceLandmark.MOUTH_RIGHT to FaceLandmark(FaceLandmark.MOUTH_RIGHT, mr),
                FaceLandmark.MOUTH_BOTTOM to FaceLandmark(FaceLandmark.MOUTH_BOTTOM, android.graphics.PointF((ml.x + mr.x) / 2f, (ml.y + mr.y) / 2f + eyeDist * 0.15f)),
                FaceLandmark.LEFT_CHEEK to FaceLandmark(FaceLandmark.LEFT_CHEEK, android.graphics.PointF(le.x + ex * 0.25f, cheekY + eyeDist * 0.2f)),
                FaceLandmark.RIGHT_CHEEK to FaceLandmark(FaceLandmark.RIGHT_CHEEK, android.graphics.PointF(re.x - ex * 0.25f, cheekY + eyeDist * 0.2f)),
            )
            Face(box, marks, headEulerAngleY = yaw, headEulerAngleZ = roll, contours = contours(re, le, ml, mr))
        }
    }

    /**
     * Face outlines in proportion to the eyes and mouth: the face's own frame has its origin between
     * the eyes, x along the eyes (picture-left eye at -0.5, the other at +0.5) and y down the face.
     */
    private fun contours(re: android.graphics.PointF, le: android.graphics.PointF, ml: android.graphics.PointF, mr: android.graphics.PointF): Map<Int, FaceContour> {
        // Picture-left eye first.
        val (e1, e2) = if (re.x <= le.x) re to le else le to re
        val d = kotlin.math.hypot(e2.x - e1.x, e2.y - e1.y).coerceAtLeast(1f)
        val ux = (e2.x - e1.x) / d; val uy = (e2.y - e1.y) / d
        val mx = (e1.x + e2.x) / 2f; val my = (e1.y + e2.y) / 2f
        fun pt(u: Float, v: Float) = android.graphics.PointF(mx + (u * ux - v * uy) * d, my + (u * uy + v * ux) * d)
        fun frame(p: android.graphics.PointF): Pair<Float, Float> { val dx = (p.x - mx) / d; val dy = (p.y - my) / d; return (dx * ux + dy * uy) to (-dx * uy + dy * ux) }
        val (m1u, m1v) = frame(if (ml.x <= mr.x) ml else mr); val (m2u, m2v) = frame(if (ml.x <= mr.x) mr else ml)
        val mouthV = ((m1v + m2v) / 2f).coerceIn(0.6f, 1.6f)
        fun ellipse(cu: Float, cv: Float, a: Float, b: Float, n: Int) = List(n) { i -> val t = 2.0 * Math.PI * i / n; pt(cu + a * kotlin.math.cos(t).toFloat(), cv + b * kotlin.math.sin(t).toFloat()) }
        val top = -1.0f; val chin = mouthV + 0.75f
        val oval = ellipse(0f, (top + chin) / 2f, 1.02f, (chin - top) / 2f, 36)
        fun eye(cu: Float) = ellipse(cu, 0f, 0.23f, 0.085f, 16)
        fun browTop(cu: Float, out: Float) = List(5) { i -> val t = i / 4f * 2f - 1f; pt(cu + out * 0.03f + t * 0.28f, -0.33f - 0.07f * (1f - t * t)) }
        fun browBot(cu: Float, out: Float) = List(5) { i -> val t = i / 4f * 2f - 1f; pt(cu + out * 0.03f + t * 0.26f, -0.26f - 0.05f * (1f - t * t)) }
        fun lip(n: Int, h: Float, bowl: Float) = List(n) { i ->
            val t = i / (n - 1f); val u = m1u + (m2u - m1u) * t; val base = m1v + (m2v - m1v) * t
            pt(u, base + h * kotlin.math.sin(Math.PI * t).toFloat().let { s -> Math.pow(s.toDouble(), bowl.toDouble()).toFloat() })
        }
        // ML Kit's "left" is the person's own left: the eye on the picture's right.
        return mapOf(
            FaceContour.FACE to oval,
            FaceContour.LEFT_EYE to eye(0.5f), FaceContour.RIGHT_EYE to eye(-0.5f),
            FaceContour.LEFT_EYEBROW_TOP to browTop(0.5f, 1f), FaceContour.LEFT_EYEBROW_BOTTOM to browBot(0.5f, 1f),
            FaceContour.RIGHT_EYEBROW_TOP to browTop(-0.5f, -1f), FaceContour.RIGHT_EYEBROW_BOTTOM to browBot(-0.5f, -1f),
            FaceContour.UPPER_LIP_TOP to lip(11, -0.11f, 0.7f), FaceContour.UPPER_LIP_BOTTOM to lip(9, -0.015f, 1f),
            FaceContour.LOWER_LIP_TOP to lip(9, 0.015f, 1f), FaceContour.LOWER_LIP_BOTTOM to lip(9, 0.15f, 0.8f),
            FaceContour.NOSE_BRIDGE to listOf(pt(0f, 0f), pt(0f, mouthV * 0.6f)),
            FaceContour.NOSE_BOTTOM to listOf(pt(-0.15f, mouthV * 0.7f), pt(0f, mouthV * 0.72f), pt(0.15f, mouthV * 0.7f)),
            FaceContour.LEFT_CHEEK to listOf(pt(0.55f, mouthV * 0.6f)), FaceContour.RIGHT_CHEEK to listOf(pt(-0.55f, mouthV * 0.6f)),
        ).mapValues { (k, v) -> FaceContour(k, v) }
    }

    private fun iou(a: Hit, b: Hit): Float {
        val x0 = maxOf(a.x, b.x); val y0 = maxOf(a.y, b.y); val x1 = minOf(a.x + a.w, b.x + b.w); val y1 = minOf(a.y + a.h, b.y + b.h)
        val i = maxOf(0f, x1 - x0) * maxOf(0f, y1 - y0)
        return i / (a.w * a.h + b.w * b.h - i).coerceAtLeast(1e-6f)
    }
}
