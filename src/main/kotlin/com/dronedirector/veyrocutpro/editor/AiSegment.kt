package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.dronedirector.veyrocutpro.camera.CloudAi
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Selections made by AI: the main subject (people, animals, vehicles… — DeepLab v3 on the
 * phone, or BiRefNet in the cloud) and the sky (EVF-SAM in the cloud, or a colour / position
 * estimate on the phone). Also an on-phone fill for AI erase when the cloud isn't used.
 */
object AiSegment {
    private const val ASSET = "deeplab.tflite"
    private const val N = 257
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "ai-segment") }
    private var model: CompiledModel? = null
    private var failed = false

    private fun ensure(context: Context): CompiledModel? {
        model?.let { return it }
        if (failed) return null
        if (!hasModel(context)) return null
        for (acc in listOf(Accelerator.GPU, Accelerator.CPU)) {
            try { return CompiledModel.create(AppData.file(context, ASSET).path, CompiledModel.Options(acc), null).also { model = it } } catch (_: Throwable) {}
        }
        failed = true
        return null
    }

    /** Probability (0..1) that each of N×N cells belongs to a subject (anything but background). */
    private fun subjectGrid(context: Context, bmp: Bitmap): FloatArray? = worker.submit(Callable {
        val m = ensure(context) ?: return@Callable null
        val small = Bitmap.createScaledBitmap(bmp, N, N, true)
        val px = IntArray(N * N); small.getPixels(px, 0, N, 0, 0, N, N)
        if (small !== bmp) small.recycle()
        val inp = FloatArray(N * N * 3)
        for (i in px.indices) {
            val c = px[i]
            inp[i * 3] = (((c shr 16) and 0xFF) - 127.5f) / 127.5f
            inp[i * 3 + 1] = (((c shr 8) and 0xFF) - 127.5f) / 127.5f
            inp[i * 3 + 2] = ((c and 0xFF) - 127.5f) / 127.5f
        }
        val ins = m.createInputBuffers(); val outs = m.createOutputBuffers()
        try {
            ins[0].writeFloat(inp)
            m.run(ins, outs)
            val o = outs[0].readFloat()
            val classes = o.size / (N * N)
            FloatArray(N * N) { i ->
                var mx = -1e9f
                for (k in 0 until classes) mx = max(mx, o[i * classes + k])
                var sum = 0f
                for (k in 0 until classes) sum += exp(o[i * classes + k] - mx)
                1f - exp(o[i * classes] - mx) / sum
            }
        } finally {
            ins.forEach { try { it.close() } catch (_: Throwable) {} }
            outs.forEach { try { it.close() } catch (_: Throwable) {} }
        }
    }).get()

    /** Grey mask picture (white = subject) at the size of [bmp]; cloud first when chosen. */
    fun subject(context: Context, bmp: Bitmap, useCloud: Boolean): Bitmap? {
        if (useCloud) try {
            val res = CloudAi.cutout(context, jpeg(bmp))
            val cut = BitmapFactory.decodeByteArray(res, 0, res.size)
            if (cut != null) return alphaToMask(cut, bmp.width, bmp.height)
        } catch (s: CloudAi.Stopped) { throw s } catch (_: Throwable) {}
        val g = subjectGrid(context, bmp) ?: return null
        return gridToMask(g, N, N, bmp.width, bmp.height, sharpen = true)
    }

    /** Grey sky mask at the size of [bmp]. */
    fun sky(context: Context, bmp: Bitmap, useCloud: Boolean): Bitmap? {
        if (useCloud) try {
            val res = CloudAi.segment(context, jpeg(bmp), "sky")
            BitmapFactory.decodeByteArray(res, 0, res.size)?.let { return Bitmap.createScaledBitmap(it, bmp.width, bmp.height, true) }
        } catch (s: CloudAi.Stopped) { throw s } catch (_: Throwable) {}
        return skyEstimate(bmp)
    }

    /** Horizontal centre (0..1) of the subject in a frame, or null when nothing stands out. */
    fun subjectCenter(context: Context, bmp: Bitmap): Float? {
        val g = subjectGrid(context, bmp) ?: return null
        var sw = 0f; var sx = 0f
        for (y in 0 until N) for (x in 0 until N) { val v = g[y * N + x]; if (v > 0.5f) { sw += v; sx += v * x } }
        return if (sw < N * N * 0.004f) null else sx / sw / (N - 1)
    }

    fun hasModel(context: Context) = AppData.has(context, ASSET)

    private fun jpeg(b: Bitmap): ByteArray = java.io.ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()

    private fun alphaToMask(cut: Bitmap, w: Int, h: Int): Bitmap {
        val s = if (cut.width == w && cut.height == h) cut else Bitmap.createScaledBitmap(cut, w, h, true)
        val px = IntArray(w * h); s.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) { val a = (px[i] ushr 24) and 0xFF; px[i] = (0xFF shl 24) or (a shl 16) or (a shl 8) or a }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun gridToMask(g: FloatArray, gw: Int, gh: Int, w: Int, h: Int, sharpen: Boolean): Bitmap {
        val px = IntArray(gw * gh) { i ->
            var v = g[i]
            if (sharpen) v = ((v - 0.35f) / 0.3f).coerceIn(0f, 1f)
            val l = (v * 255).toInt()
            (0xFF shl 24) or (l shl 16) or (l shl 8) or l
        }
        val small = Bitmap.createBitmap(px, gw, gh, Bitmap.Config.ARGB_8888)
        return Bitmap.createScaledBitmap(small, w, h, true).also { if (it !== small) small.recycle() }
    }

    /**
     * Sky on the phone: bright, smooth, blue-ish or grey areas connected to the top edge,
     * grown downwards row by row, then softened.
     */
    private fun skyEstimate(bmp: Bitmap): Bitmap {
        val gw = 160; val gh = max(20, (160f * bmp.height / bmp.width).toInt())
        val s = Bitmap.createScaledBitmap(bmp, gw, gh, true)
        val px = IntArray(gw * gh); s.getPixels(px, 0, gw, 0, 0, gw, gh)
        fun lum(c: Int) = (((c shr 16) and 0xFF) * 54 + ((c shr 8) and 0xFF) * 183 + (c and 0xFF) * 19) shr 8
        val skyish = BooleanArray(px.size) { i ->
            val c = px[i]
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            val l = lum(c)
            val x = i % gw; val y = i / gw
            val tex = if (x in 1 until gw - 1 && y in 1 until gh - 1) kotlin.math.abs(4 * l - lum(px[i - 1]) - lum(px[i + 1]) - lum(px[i - gw]) - lum(px[i + gw])) else 0
            l > 70 && tex < 40 && (b >= r - 10 || (kotlin.math.abs(r - g) < 25 && kotlin.math.abs(g - b) < 25 && l > 150))
        }
        // Flood fill from the top row through sky-like cells.
        val sky = BooleanArray(px.size)
        val stack = ArrayDeque<Int>()
        for (x in 0 until gw) if (skyish[x]) { sky[x] = true; stack.add(x) }
        while (stack.isNotEmpty()) {
            val i = stack.removeLast(); val x = i % gw; val y = i / gw
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val nx = x + dx; val ny = y + dy
                if (nx !in 0 until gw || ny !in 0 until gh) continue
                val j = ny * gw + nx
                if (!sky[j] && skyish[j]) { sky[j] = true; stack.add(j) }
            }
        }
        val g = FloatArray(px.size) { if (sky[it]) 1f else 0f }
        if (s !== bmp) s.recycle()
        return gridToMask(g, gw, gh, bmp.width, bmp.height, sharpen = false)
    }

    /**
     * AI erase on the phone (no cloud): fills the masked area from its edges inwards
     * (onion-peel), then adds back a little texture. Works best on small and medium objects.
     */
    fun fill(src: Bitmap, mask: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val m = if (mask.width == w && mask.height == h) mask else Bitmap.createScaledBitmap(mask, w, h, true)
        val mp = IntArray(w * h); m.getPixels(mp, 0, w, 0, 0, w, h)
        val hole = BooleanArray(w * h) { ((mp[it] shr 16) and 0xFF) > 100 }
        val r = FloatArray(w * h); val g = FloatArray(w * h); val b = FloatArray(w * h)
        for (i in px.indices) { val c = px[i]; r[i] = ((c shr 16) and 0xFF).toFloat(); g[i] = ((c shr 8) and 0xFF).toFloat(); b[i] = (c and 0xFF).toFloat() }
        var remaining = hole.count { it }
        // Only work inside the box around the masked area.
        var bx0 = w; var by0 = h; var bx1 = 0; var by1 = 0
        for (i in hole.indices) if (hole[i]) { val x = i % w; val y = i / w; bx0 = min(bx0, x); by0 = min(by0, y); bx1 = max(bx1, x); by1 = max(by1, y) }
        var guard = 0
        while (remaining > 0 && guard++ < 4000) {
            val fill = ArrayList<Int>()
            for (yy in by0..by1) for (xx in bx0..bx1) {
                val i = yy * w + xx
                if (!hole[i]) continue
                val x = i % w; val y = i / w
                var n = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx in 0 until w && ny in 0 until h && !hole[ny * w + nx]) n++
                }
                if (n > 0) fill += i
            }
            if (fill.isEmpty()) break
            for (i in fill) {
                val x = i % w; val y = i / w
                var sr = 0f; var sg = 0f; var sb = 0f; var sw = 0f
                for (dy in -2..2) for (dx in -2..2) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val j = ny * w + nx
                    if (hole[j]) continue
                    val wt = 1f / (1 + dx * dx + dy * dy)
                    sr += r[j] * wt; sg += g[j] * wt; sb += b[j] * wt; sw += wt
                }
                if (sw > 0) { r[i] = sr / sw; g[i] = sg / sw; b[i] = sb / sw }
            }
            for (i in fill) hole[i] = false
            remaining -= fill.size
        }
        val rnd = java.util.Random(7)
        for (i in px.indices) {
            if (((mp[i] shr 16) and 0xFF) <= 100) continue
            val n = (rnd.nextGaussian() * 3).toFloat()
            px[i] = (0xFF shl 24) or (min(255f, max(0f, r[i] + n)).toInt() shl 16) or (min(255f, max(0f, g[i] + n)).toInt() shl 8) or min(255f, max(0f, b[i] + n)).toInt()
        }
        if (m !== mask) m.recycle()
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Saves a mask picture in the app's files; returns its path. */
    fun saveMask(context: Context, mask: Bitmap): String {
        val dir = File(context.filesDir, "masks").apply { mkdirs() }
        val f = File(dir, "mask_${System.currentTimeMillis()}.png")
        f.outputStream().use { mask.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.path
    }
}
