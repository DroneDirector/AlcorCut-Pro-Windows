package com.google.mlkit.vision.face

import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage

/** Face finding: comes with the AI models for Windows; until then no faces are reported. */
class Face {
    val leftEyeOpenProbability: Float? = null
    val rightEyeOpenProbability: Float? = null
    val smilingProbability: Float? = null
    val boundingBox: android.graphics.Rect = android.graphics.Rect()
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
