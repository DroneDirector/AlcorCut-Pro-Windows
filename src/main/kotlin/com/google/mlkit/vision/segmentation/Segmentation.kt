package com.google.mlkit.vision.segmentation

import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Person finding on Windows: the "person" class of DeepLab v3 (downloaded with the AI models). Own code. */
class SegmentationMask internal constructor(val buffer: ByteBuffer, val width: Int, val height: Int)

class Segmenter internal constructor(private val o: SelfieSegmenterOptions) {
    fun process(i: InputImage): Task<SegmentationMask> = try {
        val b = i.bitmap
        val p = com.dronedirector.veyrocutpro.editor.WinSegment.person(android.content.Context.app, b)
            ?: throw IllegalStateException("The AI models are still downloading")
        val (grid, n) = p
        // At the picture's own size when asked for (else the model's size); one float per pixel.
        val w = if (o.raw) n else b.width; val h = if (o.raw) n else b.height
        val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        for (y in 0 until h) {
            val gy = ((y + 0.5f) * n / h - 0.5f).coerceIn(0f, n - 1f)
            val y0 = gy.toInt(); val y1 = minOf(n - 1, y0 + 1); val fy = gy - y0
            for (x in 0 until w) {
                val gx = ((x + 0.5f) * n / w - 0.5f).coerceIn(0f, n - 1f)
                val x0 = gx.toInt(); val x1 = minOf(n - 1, x0 + 1); val fx = gx - x0
                val a = grid[y0 * n + x0] * (1 - fx) + grid[y0 * n + x1] * fx
                val c = grid[y1 * n + x0] * (1 - fx) + grid[y1 * n + x1] * fx
                buf.putFloat(a * (1 - fy) + c * fy)
            }
        }
        buf.rewind()
        Task(SegmentationMask(buf, w, h), null)
    } catch (e: Exception) { Task(null, e) }
    fun close() {}
}

object Segmentation {
    fun getClient(o: SelfieSegmenterOptions): Segmenter = Segmenter(o)
}
