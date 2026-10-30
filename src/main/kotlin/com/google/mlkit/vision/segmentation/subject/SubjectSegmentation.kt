package com.google.mlkit.vision.segmentation.subject

import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage

/** Subject finding on Windows: DeepLab v3's "anything but background", at the picture's size. Own code. */
class SubjectSegmentationResult internal constructor(val foregroundConfidenceMask: java.nio.FloatBuffer?)

class SubjectSegmenterOptions private constructor() {
    class Builder { fun enableForegroundConfidenceMask() = this; fun enableForegroundBitmap() = this; fun build() = SubjectSegmenterOptions() }
}

class SubjectSegmenter internal constructor() {
    fun process(i: InputImage): Task<SubjectSegmentationResult> = try {
        val b = i.bitmap
        val (grid, n) = com.dronedirector.veyrocutpro.editor.WinSegment.subject(android.content.Context.app, b) ?: throw IllegalStateException("The AI models are still downloading")
        val w = b.width; val h = b.height
        val fb = java.nio.FloatBuffer.allocate(w * h)
        for (y in 0 until h) for (x in 0 until w) fb.put(grid[(y * n / h).coerceAtMost(n - 1) * n + (x * n / w).coerceAtMost(n - 1)])
        fb.rewind()
        Task(SubjectSegmentationResult(fb), null)
    } catch (e: Exception) { Task(null, e) }
    fun close() {}
}

object SubjectSegmentation { fun getClient(o: SubjectSegmenterOptions) = SubjectSegmenter() }
