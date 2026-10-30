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
) {
    fun getLandmark(type: Int): FaceLandmark? = marks[type]
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
            Face(box, marks, headEulerAngleY = yaw, headEulerAngleZ = roll)
        }
    }

    private fun iou(a: Hit, b: Hit): Float {
        val x0 = maxOf(a.x, b.x); val y0 = maxOf(a.y, b.y); val x1 = minOf(a.x + a.w, b.x + b.w); val y1 = minOf(a.y + a.h, b.y + b.h)
        val i = maxOf(0f, x1 - x0) * maxOf(0f, y1 - y0)
        return i / (a.w * a.h + b.w * b.h - i).coerceAtLeast(1e-6f)
    }
}
