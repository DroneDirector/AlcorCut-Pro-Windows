package com.google.mlkit.vision.label.defaults

class ImageLabelerOptions private constructor() {
    class Builder { fun setConfidenceThreshold(f: Float) = this; fun build() = ImageLabelerOptions() }
    companion object { val DEFAULT_OPTIONS = ImageLabelerOptions() }
}
