package com.google.mlkit.vision.face

import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage

/** Face finding: comes with the AI models for Windows; until then no faces are reported. */
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

class FaceDetectorOptions private constructor() {
    class Builder {
        fun setPerformanceMode(m: Int) = this
        fun setClassificationMode(m: Int) = this
        fun setLandmarkMode(m: Int) = this
        fun setContourMode(m: Int) = this
        fun setMinFaceSize(f: Float) = this
        fun enableTracking() = this
        fun build() = FaceDetectorOptions()
    }
    companion object {
        const val PERFORMANCE_MODE_FAST = 1; const val PERFORMANCE_MODE_ACCURATE = 2
        const val CLASSIFICATION_MODE_NONE = 1; const val CLASSIFICATION_MODE_ALL = 2
        const val LANDMARK_MODE_NONE = 1; const val LANDMARK_MODE_ALL = 2
        const val CONTOUR_MODE_NONE = 1; const val CONTOUR_MODE_ALL = 2
    }
}

class FaceDetector {
    fun process(i: InputImage): Task<List<Face>> = Task(emptyList(), null)
    fun close() {}
}

object FaceDetection {
    fun getClient(o: FaceDetectorOptions): FaceDetector = FaceDetector()
}
