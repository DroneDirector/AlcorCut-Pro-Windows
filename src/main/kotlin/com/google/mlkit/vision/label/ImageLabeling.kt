package com.google.mlkit.vision.label

import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage

/** Picture labels (smart search): comes with the AI models for Windows; until then none. */
class ImageLabel(val text: String, val confidence: Float, val index: Int)

class ImageLabeler : AutoCloseable {
    fun process(i: InputImage): Task<List<ImageLabel>> = Task(emptyList(), null)
    override fun close() {}
}

object ImageLabeling {
    fun getClient(o: com.google.mlkit.vision.label.defaults.ImageLabelerOptions): ImageLabeler = ImageLabeler()
}
