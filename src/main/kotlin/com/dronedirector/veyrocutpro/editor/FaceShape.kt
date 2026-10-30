package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

/**
 * Face-aware liquify (Retouch → Face): sliders for the eyes, face width, chin, nose and smile. The faces are
 * found once (ML Kit, on the phone); each slider becomes liquify strokes at the right places, so the result is
 * the same warp as the liquify brush and can be changed any time.
 *
 * [faces]: per face, 0..1 of the uncropped photo — left eye x,y, right eye x,y, nose x,y, mouth left x,y,
 * mouth right x,y, chin x,y, left cheek x,y, right cheek x,y, then the face's width and height
 * (share of the photo's long side). [aspect] = photo width / height.
 */
data class FaceShape(
    val faces: List<List<Float>> = emptyList(),
    val aspect: Float = 1f,
    val eyes: Float = 0f,
    val width: Float = 0f,
    val chin: Float = 0f,
    val nose: Float = 0f,
    val smile: Float = 0f,
) {
    val active get() = faces.isNotEmpty() && (eyes != 0f || width != 0f || chin != 0f || nose != 0f || smile != 0f)

    companion object {
        /** Finds the faces in [b] (the uncropped photo); empty when there are none. */
        fun find(context: Context, b: Bitmap): List<List<Float>> {
            val det = FaceDetection.getClient(FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).setMinFaceSize(0.05f).build())
            val faces = try { Tasks.await(det.process(InputImage.fromBitmap(b, 0)), 30, TimeUnit.SECONDS) } finally { det.close() }
            val w = b.width.toFloat(); val h = b.height.toFloat(); val long = max(w, h)
            return faces.mapNotNull { f ->
                fun lm(t: Int) = f.getLandmark(t)?.position
                val le = lm(FaceLandmark.LEFT_EYE) ?: return@mapNotNull null
                val re = lm(FaceLandmark.RIGHT_EYE) ?: return@mapNotNull null
                val box = f.boundingBox
                val nose = lm(FaceLandmark.NOSE_BASE)
                val ml = lm(FaceLandmark.MOUTH_LEFT); val mr = lm(FaceLandmark.MOUTH_RIGHT)
                val lc = lm(FaceLandmark.LEFT_CHEEK); val rc = lm(FaceLandmark.RIGHT_CHEEK)
                val cx = box.exactCenterX(); val by = box.bottom.toFloat()
                listOf(
                    le.x / w, le.y / h, re.x / w, re.y / h,
                    (nose?.x ?: cx) / w, (nose?.y ?: box.exactCenterY()) / h,
                    (ml?.x ?: (cx - box.width() * 0.18f)) / w, (ml?.y ?: (by - box.height() * 0.25f)) / h,
                    (mr?.x ?: (cx + box.width() * 0.18f)) / w, (mr?.y ?: (by - box.height() * 0.25f)) / h,
                    cx / w, by / h,
                    (lc?.x ?: (box.left + box.width() * 0.2f)) / w, (lc?.y ?: box.exactCenterY()) / h,
                    (rc?.x ?: (box.right - box.width() * 0.2f)) / w, (rc?.y ?: box.exactCenterY()) / h,
                    box.width() / long, box.height() / long,
                )
            }
        }
    }

    /** The liquify strokes the sliders stand for. */
    fun strokes(): List<LiqStroke> {
        if (!active) return emptyList()
        // Long-side units to 0..1 of the width / height.
        val ux = if (aspect >= 1f) 1f else 1f / aspect
        val uy = if (aspect >= 1f) aspect else 1f
        val out = ArrayList<LiqStroke>()
        fun dab(tool: Int, x: Float, y: Float, r: Float, times: Int) {
            if (times <= 0) return
            out += LiqStroke(tool, List(times) { listOf(x, y) }.flatten(), r, 1f)
        }
        fun push(x: Float, y: Float, dx: Float, dy: Float, r: Float) {
            if (abs(dx) < 1e-5f && abs(dy) < 1e-5f) return
            out += LiqStroke(0, listOf(x, y, x + dx * ux, y + dy * uy), r, 1f)
        }
        for (f in faces) {
            if (f.size < 18) continue
            val fw = f[16]; val fh = f[17]
            // Eyes: enlarge (bloat) or make smaller (pinch), a few dabs for the strength.
            if (eyes != 0f) {
                val r = fw * 0.16f; val n = (abs(eyes) * 6).toInt().coerceAtLeast(1)
                dab(if (eyes > 0) 2 else 1, f[0], f[1], r, n); dab(if (eyes > 0) 2 else 1, f[2], f[3], r, n)
            }
            // Face width: the cheeks and jaw pushed in (−) or out (+).
            if (width != 0f) {
                val d = width * fw * 0.06f; val r = fw * 0.32f
                val midX = (f[0] + f[2]) / 2f
                val jawY = (f[7] + f[11]) / 2f
                val lx = f[12]; val rx = f[14]
                val sl = if (lx < midX) -1f else 1f; val sr = -sl
                push(lx, f[13], sl * d, 0f, r); push(rx, f[15], sr * d, 0f, r)
                push(lx + (f[6] - lx) * 0.1f, jawY, sl * d, 0f, r * 0.9f); push(rx + (f[8] - rx) * 0.1f, jawY, sr * d, 0f, r * 0.9f)
            }
            // Chin: longer (+) or shorter (−).
            if (chin != 0f) push(f[10], f[11] - fh * 0.06f / uy, 0f, chin * fh * 0.05f, fw * 0.28f)
            // Nose: smaller (−) or bigger (+).
            if (nose != 0f) dab(if (nose > 0) 2 else 1, f[4], f[5] - fh * 0.04f / uy, fw * 0.14f, (abs(nose) * 5).toInt().coerceAtLeast(1))
            // Smile: the mouth corners lifted (+) or lowered (−).
            if (smile != 0f) {
                val d = -smile * fh * 0.025f; val r = fw * 0.1f
                push(f[6], f[7], 0f, d, r); push(f[8], f[9], 0f, d, r)
            }
        }
        return out
    }
}
