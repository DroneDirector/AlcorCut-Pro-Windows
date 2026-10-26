package com.dronedirector.veyrocutpro.editor

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.FloatBuffer
import java.util.UUID
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A prompt: x, y in 0..1 of the picture; label 1 = object, 0 = not the object, 2 / 3 = box corners. */
data class MaskPoint(val x: Float, val y: Float, val label: Int)

/** A Magic Mask selection on a clip (or a video layer's clip): where it was made and what was picked. */
data class MagicSel(
    val id: String = UUID.randomUUID().toString(),
    /** Source time (ms) of the frame the prompts were made on. */
    val srcMs: Long = 0L,
    val points: List<MaskPoint> = emptyList(),
    /** One-point picks: 0 = the AI's best guess, 1 = whole object, 2 = part, 3 = detail. */
    val choice: Int = 0,
    /** Source range the tracking covers, and how many frames it has (0 = not tracked yet). */
    val fromMs: Long = 0L,
    val toMs: Long = 0L,
    val frames: Int = 0,
)

object Sam {
    private val MEAN = floatArrayOf(123.675f, 116.28f, 103.53f)
    private var env: OrtEnvironment? = null
    private var enc: OrtSession? = null
    private var dec: OrtSession? = null
    @Volatile var failed: String? = null; private set

    /** Loads the model (first use takes a moment). */
    @Synchronized fun ready(context: Context): Boolean {
        if (enc != null && dec != null) return true
        return try {
            val e = OrtEnvironment.getEnvironment()
            fun opts() = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 6))
                try { addXnnpack(mapOf("intra_op_num_threads" to Runtime.getRuntime().availableProcessors().coerceIn(2, 6).toString())) } catch (_: Throwable) {}
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            if (!AppData.has(context, "sam_encoder.onnx") || !AppData.has(context, "sam_decoder.onnx")) { failed = "the Magic Mask model is still downloading"; return false }
            env = e; enc = e.createSession(AppData.file(context, "sam_encoder.onnx").path, opts()); dec = e.createSession(AppData.file(context, "sam_decoder.onnx").path, opts())
            true
        } catch (t: Throwable) { failed = t.message ?: t.javaClass.simpleName; false }
    }

    /** A picture's AI features, and how it was scaled into the model's 1024 × 1024 square. */
    class Embedding(val data: FloatArray, val k: Float, val w: Int, val h: Int)

    /** Features of [bmp] (any size). Slow (≈ 0.3–1.5 s): call off the main thread. */
    fun encode(context: Context, bmp: Bitmap): Embedding? {
        if (!ready(context)) return null
        val k = 1024f / max(bmp.width, bmp.height)
        val sw = (bmp.width * k).roundToInt().coerceIn(1, 1024); val sh = (bmp.height * k).roundToInt().coerceIn(1, 1024)
        val s = if (sw == bmp.width && sh == bmp.height) bmp else Bitmap.createScaledBitmap(bmp, sw, sh, true)
        val px = IntArray(sw * sh); s.getPixels(px, 0, sw, 0, 0, sw, sh)
        val n = 1024 * 1024
        val chw = FloatArray(3 * n)
        // Padding gets the mean colour, so it is zero after the model's normalisation (as in training).
        for (c in 0 until 3) java.util.Arrays.fill(chw, c * n, (c + 1) * n, MEAN[c])
        for (y in 0 until sh) for (x in 0 until sw) {
            val p = px[y * sw + x]; val i = y * 1024 + x
            chw[i] = ((p shr 16) and 255).toFloat(); chw[n + i] = ((p shr 8) and 255).toFloat(); chw[2 * n + i] = (p and 255).toFloat()
        }
        val e = env ?: return null
        return try {
            OnnxTensor.createTensor(e, FloatBuffer.wrap(chw), longArrayOf(1, 3, 1024, 1024)).use { t ->
                enc!!.run(mapOf("image" to t)).use { r ->
                    val fb = (r[0] as OnnxTensor).floatBuffer
                    val out = FloatArray(fb.remaining()); fb.get(out)
                    Embedding(out, k, bmp.width, bmp.height)
                }
            }
        } catch (t: Throwable) { failed = t.message; null }
    }

    /** Four answers (256 × 256 logits each, in the model's square) and their scores. */
    class Answer(val logits: FloatArray, val scores: FloatArray) {
        fun mask(i: Int): FloatArray = logits.copyOfRange(i * 65536, (i + 1) * 65536)
    }

    /** Runs the prompts (and optionally the previous frame's answer as a hint) on [e]. Fast (≈ 30 ms). */
    fun decode(e: Embedding, pts: List<MaskPoint>, hint: FloatArray? = null): Answer? {
        val en = env ?: return null
        val list = ArrayList<FloatArray>()
        for (p in pts) list += floatArrayOf(p.x * e.w * e.k, p.y * e.h * e.k, p.label.toFloat())
        // Points without a box need SAM's padding point.
        if (pts.none { it.label == 2 || it.label == 3 }) list += floatArrayOf(0f, 0f, -1f)
        val n = list.size
        val coords = FloatArray(n * 2) { list[it / 2][it % 2] }
        val labels = FloatArray(n) { list[it][2] }
        return try {
            val emb = OnnxTensor.createTensor(en, FloatBuffer.wrap(e.data), longArrayOf(1, 256, 64, 64))
            val c = OnnxTensor.createTensor(en, FloatBuffer.wrap(coords), longArrayOf(1, n.toLong(), 2))
            val l = OnnxTensor.createTensor(en, FloatBuffer.wrap(labels), longArrayOf(1, n.toLong()))
            val m = OnnxTensor.createTensor(en, FloatBuffer.wrap(hint ?: FloatArray(65536)), longArrayOf(1, 1, 256, 256))
            val h = OnnxTensor.createTensor(en, FloatBuffer.wrap(floatArrayOf(if (hint != null) 1f else 0f)), longArrayOf(1))
            try {
                dec!!.run(mapOf("embeddings" to emb, "coords" to c, "labels" to l, "mask_input" to m, "has_mask" to h)).use { r ->
                    val mb = (r[0] as OnnxTensor).floatBuffer; val sb = (r[1] as OnnxTensor).floatBuffer
                    val logits = FloatArray(mb.remaining()); mb.get(logits)
                    val scores = FloatArray(sb.remaining()); sb.get(scores)
                    Answer(logits, scores)
                }
            } finally { emb.close(); c.close(); l.close(); m.close(); h.close() }
        } catch (t: Throwable) { failed = t.message; null }
    }

    /** Which answer to use: several prompts → SAM's single answer; one point → by [choice] (whole / part / detail) or best score. */
    fun pick(a: Answer, pts: List<MaskPoint>, choice: Int): Int {
        if (pts.size != 1 || pts[0].label != 1) return 0
        if (choice == 0) return (1..3).maxBy { a.scores[it] }
        // Order the three answers by size: whole = largest.
        val areas = (1..3).map { i -> i to (0 until 65536).count { a.logits[i * 65536 + it] > 0f } }.sortedByDescending { it.second }
        return areas[(choice - 1).coerceIn(0, 2)].first
    }

    /** A logits square → an 8-bit mask of [ow] × [oh] covering the picture. */
    fun toMask(logits: FloatArray, e: Embedding, ow: Int, oh: Int): ByteArray {
        val out = ByteArray(ow * oh)
        val sx = e.w * e.k / 4f / ow; val sy = e.h * e.k / 4f / oh
        for (y in 0 until oh) for (x in 0 until ow) {
            val v = bilinear(logits, 256, 256, (x + 0.5f) * sx - 0.5f, (y + 0.5f) * sy - 0.5f)
            out[y * ow + x] = (255f / (1f + exp(-v * 1.5f))).roundToInt().coerceIn(0, 255).toByte()
        }
        return out
    }

    fun bilinear(a: FloatArray, w: Int, h: Int, x0: Float, y0: Float): Float {
        val x = x0.coerceIn(0f, w - 1f); val y = y0.coerceIn(0f, h - 1f)
        val xi = x.toInt(); val yi = y.toInt(); val x1 = min(xi + 1, w - 1); val y1 = min(yi + 1, h - 1)
        val fx = x - xi; val fy = y - yi
        val t = a[yi * w + xi] + (a[yi * w + x1] - a[yi * w + xi]) * fx
        val b = a[y1 * w + xi] + (a[y1 * w + x1] - a[y1 * w + xi]) * fx
        return t + (b - t) * fy
    }
}

/** The outline on one analysed frame: an 8-bit mask of [w] × [h] covering [rect] (x0, y0, x1, y1 in 0..1); empty = not in the picture. */
class MaskEntry(val rect: FloatArray, val w: Int, val h: Int, val bytes: ByteArray) {
    val empty: Boolean get() = w == 0 || h == 0
    companion object { val NONE = MaskEntry(floatArrayOf(0f, 0f, 0f, 0f), 0, 0, ByteArray(0)) }
}

/** The outline of a selection on every analysed frame (source ms). */
class MaskTrack(val times: LongArray, val entries: Array<MaskEntry>) {
    /** The entry nearest to [srcMs]. */
    fun at(srcMs: Long): MaskEntry {
        if (times.isEmpty()) return MaskEntry.NONE
        var i = java.util.Arrays.binarySearch(times, srcMs)
        if (i >= 0) return entries[i]
        i = -i - 1
        if (i <= 0) return entries[0]
        if (i >= times.size) return entries[times.size - 1]
        return if (srcMs - times[i - 1] <= times[i] - srcMs) entries[i - 1] else entries[i]
    }

    companion object {
        private fun file(context: Context, id: String) = File(File(context.filesDir, "magicmask").apply { mkdirs() }, "$id.bin")
        private val cache = HashMap<String, MaskTrack>()

        fun save(context: Context, id: String, t: MaskTrack) {
            DataOutputStream(DeflaterOutputStream(file(context, id).outputStream().buffered())).use { o ->
                o.writeInt(2); o.writeInt(t.times.size)
                for (k in t.times.indices) {
                    val e = t.entries[k]
                    o.writeLong(t.times[k]); for (v in e.rect) o.writeFloat(v); o.writeInt(e.w); o.writeInt(e.h); o.write(e.bytes)
                }
            }
            synchronized(cache) { cache[id] = t }
        }

        fun load(context: Context, id: String?): MaskTrack? {
            if (id == null) return null
            synchronized(cache) { cache[id]?.let { return it } }
            val f = file(context, id)
            if (!f.exists()) return null
            return try {
                DataInputStream(InflaterInputStream(f.inputStream().buffered())).use { i ->
                    if (i.readInt() != 2) return null
                    val n = i.readInt()
                    val ts = LongArray(n)
                    val es = Array(n) {
                        ts[it] = i.readLong()
                        val r = FloatArray(4) { i.readFloat() }
                        val w = i.readInt(); val h = i.readInt()
                        val b = ByteArray(w * h); i.readFully(b)
                        MaskEntry(r, w, h, b)
                    }
                    MaskTrack(ts, es).also { synchronized(cache) { cache[id] = it } }
                }
            } catch (_: Throwable) { null }
        }
    }
}
