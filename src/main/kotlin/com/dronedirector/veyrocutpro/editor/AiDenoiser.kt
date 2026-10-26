package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device AI noise reduction (NAFNet, trained on real smartphone noise — SIDD) run in
 * 256×256 tiles with overlap, on the GPU when possible. Built into the app; no internet.
 */
object AiDenoiser {
    private const val ASSET = "nafnet.tflite"
    private const val T = 256
    private const val PAD = 16

    var running by mutableStateOf(false)
        private set
    var progress by mutableIntStateOf(0)
        private set

    class Stopped : RuntimeException("stopped")

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "ai-denoise") }
    private val main = Handler(Looper.getMainLooper())
    private val cancel = AtomicBoolean(false)
    private var model: CompiledModel? = null
    private var failed = false

    /** Set while an export runs in the export process: Stop is passed on to it. */
    @Volatile var onStop: (() -> Unit)? = null
    fun stop() { cancel.set(true); onStop?.invoke() }
    /** Shows the export process's AI progress here (its own state lives in that process). */
    fun showRemote(on: Boolean, p: Int) { running = on; progress = p }

    /** The model is downloaded on first launch (app data). */
    fun available(context: Context): Boolean = AppData.has(context, ASSET)

    private fun ensure(context: Context): CompiledModel? {
        model?.let { return it }
        if (failed) return null
        if (!available(context)) return null
        for (acc in listOf(Accelerator.GPU, Accelerator.CPU)) {
            try {
                val m = CompiledModel.create(AppData.file(context, ASSET).path, CompiledModel.Options(acc), null)
                model = m
                return m
            } catch (_: Throwable) {}
        }
        failed = true
        return null
    }

    /**
     * Fully denoised copy of ARGB pixels. Blocks the caller; the work runs on the AI thread.
     * Throws [Stopped] if [stop] is called; returns null when the model can't run on this phone.
     */
    fun denoise(context: Context, px: IntArray, w: Int, h: Int, showProgress: Boolean = true): IntArray? {
        val app = context.applicationContext
        return worker.submit(Callable { run(app, px, w, h, showProgress) }).get()
    }

    private fun run(context: Context, src: IntArray, w: Int, h: Int, showProgress: Boolean): IntArray? {
        val m = ensure(context) ?: return null
        cancel.set(false)
        if (showProgress) main.post { running = true; progress = 0 }
        try {
            val inputs = m.createInputBuffers()
            val outputs = m.createOutputBuffers()
            val out = IntArray(w * h)
            val step = T - 2 * PAD
            val cols = (w + step - 1) / step; val rows = (h + step - 1) / step
            val total = cols * rows
            val chw = FloatArray(3 * T * T)
            val plane = T * T
            var done = 0
            for (ty in 0 until rows) for (tx in 0 until cols) {
                if (cancel.get()) throw Stopped()
                val x0 = tx * step; val y0 = ty * step
                for (yy in 0 until T) {
                    val sy = (y0 - PAD + yy).coerceIn(0, h - 1)
                    for (xx in 0 until T) {
                        val sx = (x0 - PAD + xx).coerceIn(0, w - 1)
                        val c = src[sy * w + sx]
                        val i = yy * T + xx
                        chw[i] = ((c shr 16) and 0xFF) / 255f
                        chw[plane + i] = ((c shr 8) and 0xFF) / 255f
                        chw[2 * plane + i] = (c and 0xFF) / 255f
                    }
                }
                inputs[0].writeFloat(chw)
                m.run(inputs, outputs)
                val res = outputs[0].readFloat()
                val vw = minOf(step, w - x0); val vh = minOf(step, h - y0)
                for (yy in 0 until vh) for (xx in 0 until vw) {
                    val i = (yy + PAD) * T + (xx + PAD)
                    val r = (res[i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    val g = (res[plane + i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    val b = (res[2 * plane + i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    val o = (y0 + yy) * w + (x0 + xx)
                    out[o] = (src[o] and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
                }
                done++
                if (showProgress && done % 2 == 0) { val p = done * 100 / total; main.post { progress = p } }
            }
            inputs.forEach { try { it.close() } catch (_: Throwable) {} }
            outputs.forEach { try { it.close() } catch (_: Throwable) {} }
            return out
        } finally {
            if (showProgress) main.post { running = false; progress = 0 }
        }
    }

    /**
     * Export path: denoises [src] tile by tile straight into a new Bitmap (blended with the original at
     * [strength]). Only small tile buffers are used, so even very large photos fit in memory. If the
     * graphics chip fails part-way, the rest is done on the processor. Null when the model can't run.
     */
    fun denoiseBitmap(context: Context, src: Bitmap, strength: Float, dropped: () -> Boolean = { false }): Bitmap? {
        val app = context.applicationContext
        return worker.submit(Callable { runBitmap(app, src, strength, dropped) }).get()
    }

    private fun runBitmap(context: Context, src: Bitmap, strength: Float, dropped: () -> Boolean = { false }): Bitmap? {
        var m = ensure(context) ?: return null
        cancel.set(false)
        main.post { running = true; progress = 0 }
        var inputs = m.createInputBuffers(); var outputs = m.createOutputBuffers()
        try {
            val w = src.width; val h = src.height
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val step = T - 2 * PAD
            val cols = (w + step - 1) / step; val rows = (h + step - 1) / step
            val total = cols * rows
            val chw = FloatArray(3 * T * T); val plane = T * T
            val region = IntArray(T * T); val tileOut = IntArray(step * step)
            val k = (strength.coerceIn(0f, 1f) * 256).toInt(); val ik = 256 - k
            var done = 0
            for (ty in 0 until rows) for (tx in 0 until cols) {
                if (cancel.get() || dropped()) throw Stopped()
                val x0 = tx * step; val y0 = ty * step
                // The tile with its padding, clamped to the photo (edges repeated).
                val rx0 = (x0 - PAD).coerceAtLeast(0); val ry0 = (y0 - PAD).coerceAtLeast(0)
                val rx1 = (x0 - PAD + T).coerceAtMost(w); val ry1 = (y0 - PAD + T).coerceAtMost(h)
                val rw = rx1 - rx0; val rh = ry1 - ry0
                src.getPixels(region, 0, rw, rx0, ry0, rw, rh)
                for (yy in 0 until T) {
                    val sy = (y0 - PAD + yy).coerceIn(ry0, ry1 - 1) - ry0
                    for (xx in 0 until T) {
                        val sx = (x0 - PAD + xx).coerceIn(rx0, rx1 - 1) - rx0
                        val c = region[sy * rw + sx]
                        val i = yy * T + xx
                        chw[i] = ((c shr 16) and 0xFF) / 255f
                        chw[plane + i] = ((c shr 8) and 0xFF) / 255f
                        chw[2 * plane + i] = (c and 0xFF) / 255f
                    }
                }
                val res = try {
                    inputs[0].writeFloat(chw); m.run(inputs, outputs); outputs[0].readFloat()
                } catch (t: Throwable) {
                    if (t is Stopped) throw t
                    // Graphics chip gave up: carry on with the processor.
                    inputs.forEach { try { it.close() } catch (_: Throwable) {} }; outputs.forEach { try { it.close() } catch (_: Throwable) {} }
                    model = null
                    m = CompiledModel.create(AppData.file(context, ASSET).path, CompiledModel.Options(Accelerator.CPU), null).also { model = it }
                    inputs = m.createInputBuffers(); outputs = m.createOutputBuffers()
                    inputs[0].writeFloat(chw); m.run(inputs, outputs); outputs[0].readFloat()
                }
                val vw = minOf(step, w - x0); val vh = minOf(step, h - y0)
                for (yy in 0 until vh) for (xx in 0 until vw) {
                    val i = (yy + PAD) * T + (xx + PAD)
                    val o = region[(y0 + yy - ry0) * rw + (x0 + xx - rx0)]
                    val r = (res[i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    val g = (res[plane + i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    val b = (res[2 * plane + i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    val br = (((o shr 16) and 0xFF) * ik + r * k) shr 8
                    val bg = (((o shr 8) and 0xFF) * ik + g * k) shr 8
                    val bb = ((o and 0xFF) * ik + b * k) shr 8
                    tileOut[yy * vw + xx] = (o and 0xFF000000.toInt()) or (br shl 16) or (bg shl 8) or bb
                }
                out.setPixels(tileOut, 0, vw, x0, y0, vw, vh)
                done++
                if (done % 2 == 0) { val p = done * 100 / total; main.post { progress = p } }
            }
            return out
        } finally {
            inputs.forEach { try { it.close() } catch (_: Throwable) {} }
            outputs.forEach { try { it.close() } catch (_: Throwable) {} }
            main.post { running = false; progress = 0 }
        }
    }

    /** a + (b - a) * t per pixel. */
    fun blend(a: IntArray, b: IntArray, t: Float): IntArray {
        if (t >= 0.999f) return b
        val k = (t.coerceIn(0f, 1f) * 256).toInt(); val ik = 256 - k
        return IntArray(a.size) { i ->
            val x = a[i]; val y = b[i]
            val r = (((x shr 16) and 0xFF) * ik + ((y shr 16) and 0xFF) * k) shr 8
            val g = (((x shr 8) and 0xFF) * ik + ((y shr 8) and 0xFF) * k) shr 8
            val bl = ((x and 0xFF) * ik + (y and 0xFF) * k) shr 8
            (x and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or bl
        }
    }
}
