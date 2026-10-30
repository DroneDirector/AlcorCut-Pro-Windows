package com.google.mlkit.vision.segmentation.selfie

class SelfieSegmenterOptions private constructor(internal val raw: Boolean) {
    class Builder {
        private var raw = false
        fun setDetectorMode(m: Int) = this
        fun enableRawSizeMask() = apply { raw = true }
        fun build() = SelfieSegmenterOptions(raw)
    }
    companion object { const val SINGLE_IMAGE_MODE = 2; const val STREAM_MODE = 1 }
}
