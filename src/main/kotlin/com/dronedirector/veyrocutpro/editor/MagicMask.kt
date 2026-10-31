package com.dronedirector.veyrocutpro.editor

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
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
import kotlin.math.sqrt

/**
 * Magic Mask: tap or box any person, animal, vehicle or object
 * on one frame; the AI outlines it (MobileSAM — Apache 2.0 — on the phone, through ONNX Runtime —
 * MIT) and it is tracked through the clip: every frame's outline is carried forward with
 * AlcorCut Pro's own optical flow and re-fitted by the AI.
 */

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

// ---------------------------------------------------------------- the AI (MobileSAM through ONNX Runtime)

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

// ---------------------------------------------------------------- tracked masks on disk

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

// ---------------------------------------------------------------- reading frames in order

/** Decodes a video's frames in order as RGB bitmaps of at most [maxSide] px (rotation applied). */
object FrameReader {
    /** Calls [onFrame] for the frames from [fromMs] to [toMs], at least [stepMs] apart; return false from it to stop. */
    fun read(context: Context, uri: Uri, fromMs: Long, toMs: Long, maxSide: Int, stepMs: Long, onFrame: (Long, Bitmap) -> Boolean) {
        var count = 0; var stopped = false
        try {
            readCodec(context, uri, fromMs, toMs, maxSide, stepMs) { t, b -> count++; val go = onFrame(t, b); if (!go) stopped = true; go }
        } catch (e: Throwable) { android.util.Log.w("FrameReader", "decoder path failed: ${e.message}") }
        if (count > 0 || stopped) return
        // Some decoders (e.g. emulators) don't hand out frames as images: read them one by one instead.
        val mmr = android.media.MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            var t = fromMs
            while (t <= toMs) {
                val b = mmr.getFrameAtTime(t * 1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST) ?: break
                val k = min(1f, maxSide.toFloat() / max(b.width, b.height))
                val sb = if (k < 1f) Bitmap.createScaledBitmap(b, (b.width * k).roundToInt().coerceAtLeast(2), (b.height * k).roundToInt().coerceAtLeast(2), true) else b
                if (!onFrame(t, sb.copy(Bitmap.Config.ARGB_8888, false))) break
                t += stepMs + 1
            }
        } catch (_: Throwable) {} finally { try { mmr.release() } catch (_: Throwable) {} }
    }

    private fun readCodec(context: Context, uri: Uri, fromMs: Long, toMs: Long, maxSide: Int, stepMs: Long, onFrame: (Long, Bitmap) -> Boolean) {
        val ex = MediaExtractor()
        ex.setDataSource(context, uri, null)
        var track = -1; var fmt: MediaFormat? = null
        for (i in 0 until ex.trackCount) { val f = ex.getTrackFormat(i); if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) { track = i; fmt = f; break } }
        if (track < 0 || fmt == null) { ex.release(); return }
        ex.selectTrack(track)
        val rotation = if (fmt.containsKey("rotation-degrees")) fmt.getInteger("rotation-degrees") else 0
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0); codec.start()
        ex.seekTo(fromMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val info = MediaCodec.BufferInfo()
        var inDone = false; var outDone = false
        var next = fromMs
        try {
            while (!outDone) {
                if (!inDone) {
                    val ii = codec.dequeueInputBuffer(5000)
                    if (ii >= 0) {
                        val n = ex.readSampleData(codec.getInputBuffer(ii)!!, 0)
                        if (n < 0 || ex.sampleTime > toMs * 1000 + 150_000) { codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 5000)
                if (oi >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    val t = info.presentationTimeUs / 1000
                    if (info.size > 0 && t >= next && t <= toMs) {
                        val img = try { codec.getOutputImage(oi) } catch (_: Throwable) { null }
                        val bmp = try { img?.let { toBitmap(it, maxSide, rotation) } } catch (_: Throwable) { null }
                        img?.close()
                        codec.releaseOutputBuffer(oi, false)
                        if (bmp != null) { next = t + stepMs; if (!onFrame(t, bmp)) outDone = true }
                        continue
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if (t > toMs) outDone = true
                }
            }
        } finally {
            try { codec.stop() } catch (_: Throwable) {}
            codec.release(); ex.release()
        }
    }

    /** YUV 4:2:0 (8- or 10-bit) → RGB at the target size. */
    private fun toBitmap(img: android.media.Image, maxSide: Int, rotation: Int): Bitmap {
        val cw = img.cropRect.width(); val ch = img.cropRect.height(); val cx = img.cropRect.left; val cy = img.cropRect.top
        val k = min(1f, maxSide.toFloat() / max(cw, ch))
        val ow = (cw * k).roundToInt().coerceAtLeast(2); val oh = (ch * k).roundToInt().coerceAtLeast(2)
        val yP = img.planes[0]; val uP = img.planes[1]; val vP = img.planes[2]
        val yB = yP.buffer; val uB = uP.buffer; val vB = vP.buffer
        val ys = yP.rowStride; val yps = yP.pixelStride; val us = uP.rowStride; val ups = uP.pixelStride; val vs = vP.rowStride; val vps = vP.pixelStride
        // 10-bit frames (two bytes per sample): read the high byte.
        val hi = if (yps == 2) 1 else 0
        val px = IntArray(ow * oh)
        for (y in 0 until oh) {
            val sy = cy + ((y + 0.5f) / k).toInt().coerceAtMost(ch - 1)
            for (x in 0 until ow) {
                val sx = cx + ((x + 0.5f) / k).toInt().coerceAtMost(cw - 1)
                val Y = (yB.get(sy * ys + sx * yps + hi).toInt() and 255) - 16
                val U = (uB.get((sy / 2) * us + (sx / 2) * ups + (if (ups >= 2 && yps == 2) 1 else 0)).toInt() and 255) - 128
                val V = (vB.get((sy / 2) * vs + (sx / 2) * vps + (if (vps >= 2 && yps == 2) 1 else 0)).toInt() and 255) - 128
                val c = 1.164f * Y
                val r = (c + 1.596f * V).toInt().coerceIn(0, 255)
                val g = (c - 0.392f * U - 0.813f * V).toInt().coerceIn(0, 255)
                val b = (c + 2.017f * U).toInt().coerceIn(0, 255)
                px[y * ow + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val bmp = Bitmap.createBitmap(px, ow, oh, Bitmap.Config.ARGB_8888)
        return if (rotation == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, ow, oh, Matrix().apply { postRotate(rotation.toFloat()) }, true)
    }

    /** One frame at [srcMs] (exact), or null. */
    fun frameAt(context: Context, uri: Uri, srcMs: Long, maxSide: Int): Bitmap? {
        var out: Bitmap? = null
        read(context, uri, srcMs, srcMs + 1000, maxSide, 1) { _, b -> out = b; false }
        if (out != null) return out
        // Past the last frame: the last one there is.
        read(context, uri, (srcMs - 500).coerceAtLeast(0), srcMs, maxSide, 1) { _, b -> out = b; true }
        return out
    }
}

// ---------------------------------------------------------------- tracking

/**
 * Follows a selection through a clip, frame by frame:
 *  1. the outline is carried from the previous frame along the optical flow (both directions are
 *     measured, so newly uncovered pixels don't inherit it);
 *  2. a close-up of the object (twice its size) is cut from the full-resolution frame and the AI
 *     fits the outline there — a box round the carried outline, the point deepest inside it, and the
 *     carried answer itself as a hint — so small objects and fine edges keep their detail;
 *  3. stray specks are dropped; if the answer disagrees wildly with the carried outline (hidden
 *     object), the carried outline is kept; at a scene cut, or when the object leaves the picture,
 *     tracking stops in that direction (empty outline).
 */
object MagicTracker {
    private const val GRID = 256
    private const val MAXMASK = 512
    private const val PAD = 0.35f
    private const val FRAME_SIDE = 1920

    /** The selection on its own frame, for the picker (mask at [ow] × [oh] over the whole picture). */
    fun preview(e: Sam.Embedding, sel: MagicSel, ow: Int, oh: Int): ByteArray? {
        if (sel.points.isEmpty()) return null
        val a = Sam.decode(e, sel.points) ?: return null
        return Sam.toMask(a.mask(Sam.pick(a, sel.points, sel.choice)), e, ow, oh)
    }

    /** One frame's state: the stored entry plus the AI's raw answer and the crop's geometry (for the next hint). */
    private class St(val entry: MaskEntry, val logits: FloatArray?, val cropW: Float, val cropH: Float)

    private fun small(b: Bitmap): Bitmap {
        val k = GRID.toFloat() / max(b.width, b.height)
        return Bitmap.createScaledBitmap(b, (b.width * k).roundToInt().coerceAtLeast(16), (b.height * k).roundToInt().coerceAtLeast(16), true)
    }

    /** An entry painted onto the whole-frame grid (0..1). */
    private fun toGrid(e: MaskEntry, gw: Int, gh: Int): FloatArray {
        val g = FloatArray(gw * gh)
        if (e.empty) return g
        val r = e.rect
        for (y in 0 until gh) for (x in 0 until gw) {
            val nx = (x + 0.5f) / gw; val ny = (y + 0.5f) / gh
            if (nx < r[0] || nx > r[2] || ny < r[1] || ny > r[3]) continue
            val u = (nx - r[0]) / (r[2] - r[0]) * e.w - 0.5f; val v = (ny - r[1]) / (r[3] - r[1]) * e.h - 0.5f
            g[y * gw + x] = bil8(e.bytes, e.w, e.h, u, v) / 255f
        }
        return g
    }

    private fun bil8(a: ByteArray, w: Int, h: Int, x0: Float, y0: Float): Float {
        if (x0 < -0.5f || y0 < -0.5f || x0 > w - 0.5f || y0 > h - 0.5f) return 0f
        val x = x0.coerceIn(0f, w - 1f); val y = y0.coerceIn(0f, h - 1f)
        val xi = x.toInt(); val yi = y.toInt(); val x1 = min(xi + 1, w - 1); val y1 = min(yi + 1, h - 1)
        val fx = x - xi; val fy = y - yi
        fun g(i: Int) = (a[i].toInt() and 255).toFloat()
        val t = g(yi * w + xi) + (g(yi * w + x1) - g(yi * w + xi)) * fx
        val b = g(y1 * w + xi) + (g(y1 * w + x1) - g(y1 * w + xi)) * fx
        return t + (b - t) * fy
    }

    private fun bilF(a: FloatArray, w: Int, h: Int, x0: Float, y0: Float, outside: Float): Float {
        if (x0 < -0.5f || y0 < -0.5f || x0 > w - 0.5f || y0 > h - 0.5f) return outside
        return Sam.bilinear(a, w, h, x0, y0)
    }

    /** Crop round the outline: twice its size (at least 1/5 of the frame), square in pixels, inside the frame. */
    private fun rectFor(g: FloatArray, gw: Int, gh: Int, W: Int, H: Int): FloatArray? {
        var x0 = gw; var y0 = gh; var x1 = -1; var y1 = -1; var n = 0
        for (y in 0 until gh) for (x in 0 until gw) if (g[y * gw + x] > 0.5f) { n++; if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y }
        if (n < 3) return null
        val px0 = x0.toFloat() / gw * W; val px1 = (x1 + 1f) / gw * W; val py0 = y0.toFloat() / gh * H; val py1 = (y1 + 1f) / gh * H
        var side = max(px1 - px0, py1 - py0) * 2f + 16f
        side = max(side, 0.2f * max(W, H))
        val cx = (px0 + px1) / 2; val cy = (py0 + py1) / 2
        val sw = min(side, W.toFloat()); val sh = min(side, H.toFloat())
        val rx0 = (cx - sw / 2).coerceIn(0f, W - sw); val ry0 = (cy - sh / 2).coerceIn(0f, H - sh)
        return floatArrayOf(rx0 / W, ry0 / H, (rx0 + sw) / W, (ry0 + sh) / H)
    }

    private fun cropOf(frame: Bitmap, r: FloatArray): Bitmap {
        val W = frame.width; val H = frame.height
        val x0 = (r[0] * W).roundToInt().coerceIn(0, W - 1); val y0 = (r[1] * H).roundToInt().coerceIn(0, H - 1)
        val x1 = (r[2] * W).roundToInt().coerceIn(x0 + 1, W); val y1 = (r[3] * H).roundToInt().coerceIn(y0 + 1, H)
        return Bitmap.createBitmap(frame, x0, y0, x1 - x0, y1 - y0)
    }

    /** Box round the outline (padded) and the point deepest inside it, in crop coordinates. */
    private fun promptsFrom(g: FloatArray, gw: Int, gh: Int, r: FloatArray): List<MaskPoint> {
        var x0 = gw; var y0 = gh; var x1 = -1; var y1 = -1
        val bin = ByteArray(gw * gh)
        for (y in 0 until gh) for (x in 0 until gw) if (g[y * gw + x] > 0.5f) { bin[y * gw + x] = -1; if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y }
        val nx0 = x0.toFloat() / gw; val nx1 = (x1 + 1f) / gw; val ny0 = y0.toFloat() / gh; val ny1 = (y1 + 1f) / gh
        val px = (nx1 - nx0) * PAD + 1f / gw; val py = (ny1 - ny0) * PAD + 1f / gh
        val (dx, dy) = deepest(bin, gw, gh)
        fun tc(x: Float, y: Float) = floatArrayOf(((x - r[0]) / (r[2] - r[0])).coerceIn(0f, 1f), ((y - r[1]) / (r[3] - r[1])).coerceIn(0f, 1f))
        val p = tc((dx + 0.5f) / gw, (dy + 0.5f) / gh); val a = tc(nx0 - px, ny0 - py); val z = tc(nx1 + px, ny1 + py)
        return listOf(MaskPoint(p[0], p[1], 1), MaskPoint(a[0], a[1], 2), MaskPoint(z[0], z[1], 3))
    }

    /** The previous answer carried into the new crop's model square (logits; −20 outside, low where pixels are newly uncovered). */
    private fun hintFor(prev: St, r: FloatArray, W: Int, H: Int, back: (Float, Float) -> FloatArray, bad: BooleanArray?, gw: Int, gh: Int): FloatArray? {
        val pl = prev.logits ?: return null
        if (prev.entry.empty) return null
        val pr = prev.entry.rect
        val cw = (r[2] - r[0]) * W; val ch = (r[3] - r[1]) * H
        val k = 1024f / max(cw, ch)
        val pk = 1024f / max(prev.cropW, prev.cropH)
        val out = FloatArray(65536)
        for (y in 0 until 256) for (x in 0 until 256) {
            val cx = (x + 0.5f) * 4f; val cy = (y + 0.5f) * 4f
            if (cx > cw * k || cy > ch * k) { out[y * 256 + x] = -20f; continue }
            val nx = r[0] + (cx / k) / W; val ny = r[1] + (cy / k) / H
            val b = back(nx, ny)
            val sx = ((b[0] - pr[0]) * W) * pk / 4f - 0.5f; val sy = ((b[1] - pr[1]) * H) * pk / 4f - 0.5f
            var v = bilF(pl, 256, 256, sx, sy, -20f)
            if (bad != null) {
                val gx = (nx * gw).toInt().coerceIn(0, gw - 1); val gy = (ny * gh).toInt().coerceIn(0, gh - 1)
                if (bad[gy * gw + gx]) v = min(v, -2f)
            }
            out[y * 256 + x] = v
        }
        return out
    }

    /** Keeps the largest piece of the mask and any piece at least 8 % of its size (stray specks go). */
    private fun clean(m: ByteArray, w: Int, h: Int) {
        val lab = IntArray(w * h); var n = 0
        val areas = ArrayList<Int>(); areas += 0
        val q = IntArray(w * h)
        for (i in 0 until w * h) {
            if ((m[i].toInt() and 255) <= 127 || lab[i] != 0) continue
            n++; var head = 0; var tail = 0; q[tail++] = i; lab[i] = n; var a = 0
            while (head < tail) {
                val p = q[head++]; a++
                val px = p % w; val py = p / w
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = px + dx; val yy = py + dy
                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
                    val j = yy * w + xx
                    if (lab[j] == 0 && (m[j].toInt() and 255) > 127) { lab[j] = n; q[tail++] = j }
                }
            }
            areas += a
        }
        if (n <= 1) return
        val big = areas.max()
        val keepLab = BooleanArray(n + 1) { it > 0 && areas[it] >= 0.08f * big }
        // Keep-area grown by 2 px, so the soft edge of kept pieces stays.
        val keep = BooleanArray(w * h) { keepLab[lab[it]] }
        val grown = keep.copyOf()
        for (y in 0 until h) for (x in 0 until w) if (keep[y * w + x]) for (dy in -2..2) for (dx in -2..2) {
            val xx = x + dx; val yy = y + dy; if (xx in 0 until w && yy in 0 until h) grown[yy * w + xx] = true
        }
        for (i in m.indices) if (!grown[i]) m[i] = 0
    }

    /** Fits the outline on a close-up of [frame] round [r]. */
    private fun fit(context: Context, frame: Bitmap, r: FloatArray, pts: List<MaskPoint>, hint: FloatArray?, carried: FloatArray? = null, gw: Int = 0, gh: Int = 0, bad: BooleanArray? = null): St? {
        val crop = cropOf(frame, r)
        val e = Sam.encode(context, crop) ?: return null
        val a = Sam.decode(e, pts, hint) ?: return null
        // Of SAM's four answers, the one that best matches the carried outline wins (a face stays a face, not the whole head).
        var logits = a.mask(0)
        if (carried != null) {
            var best = -1f
            val tw = 96; val th = max(8, (96f * crop.height / crop.width).roundToInt())
            for (i in 0 until 4) {
                val g = toGrid(MaskEntry(r, tw, th, Sam.toMask(a.mask(i), e, tw, th)), gw, gh)
                var inter = 0; var uni = 0
                // Newly uncovered pixels don't count (parts coming back into view may join).
                for (k in g.indices) { if (bad != null && bad[k]) continue; val p = g[k] > 0.5f; val c = carried[k] > 0.5f; if (p && c) inter++; if (p || c) uni++ }
                val sc = (if (uni > 0) inter.toFloat() / uni else 0f) + 0.05f * a.scores[i] + (if (i == 0) 0.03f else 0f)
                if (sc > best) { best = sc; logits = a.mask(i) }
            }
        }
        val k = min(1f, MAXMASK.toFloat() / max(crop.width, crop.height))
        val mw = (crop.width * k).roundToInt().coerceAtLeast(8); val mh = (crop.height * k).roundToInt().coerceAtLeast(8)
        val m = Sam.toMask(logits, e, mw, mh)
        clean(m, mw, mh)
        return St(MaskEntry(r, mw, mh, m), logits, crop.width.toFloat(), crop.height.toFloat())
    }

    /** Scene cut between two grid frames (big change in both pixels and colours). */
    private fun isCut(a: Bitmap, b: Bitmap): Boolean {
        val ta = Bitmap.createScaledBitmap(a, 32, 18, true); val tb = Bitmap.createScaledBitmap(b, 32, 18, true)
        val pa = IntArray(576); val pb = IntArray(576); ta.getPixels(pa, 0, 32, 0, 0, 32, 18); tb.getPixels(pb, 0, 32, 0, 0, 32, 18)
        var d = 0.0
        val ha = FloatArray(64); val hb = FloatArray(64)
        for (i in 0 until 576) {
            val x = pa[i]; val y = pb[i]
            d += (kotlin.math.abs(((x shr 16) and 255) - ((y shr 16) and 255)) + kotlin.math.abs(((x shr 8) and 255) - ((y shr 8) and 255)) + kotlin.math.abs((x and 255) - (y and 255))) / 765.0
            ha[((x shr 22) and 3) * 16 + ((x shr 14) and 3) * 4 + ((x shr 6) and 3)] += 1f
            hb[((y shr 22) and 3) * 16 + ((y shr 14) and 3) * 4 + ((y shr 6) and 3)] += 1f
        }
        var hd = 0f; for (i in 0 until 64) hd += kotlin.math.abs(ha[i] - hb[i]) / 576f
        return d / 576 > 0.16 && hd / 2 > 0.25f
    }

    /** The picked frame: the whole picture first, then a close-up of the object for a finer outline. */
    private fun first(context: Context, frame: Bitmap, sel: MagicSel): St? {
        val W = frame.width; val H = frame.height
        val e = Sam.encode(context, frame) ?: return null
        val a = Sam.decode(e, sel.points) ?: return null
        val full = a.mask(Sam.pick(a, sel.points, sel.choice))
        val gw = small(frame).width; val gh = small(frame).height
        val gb = Sam.toMask(full, e, gw, gh); val g = FloatArray(gw * gh) { (gb[it].toInt() and 255) / 255f }
        val mw = min(MAXMASK, W); val mh = max(8, (mw.toFloat() * H / W).roundToInt())
        val whole = St(MaskEntry(floatArrayOf(0f, 0f, 1f, 1f), mw, mh, Sam.toMask(full, e, mw, mh)), full, W.toFloat(), H.toFloat())
        val r = rectFor(g, gw, gh, W, H) ?: return whole
        if (r[0] <= 0f && r[1] <= 0f && r[2] >= 1f && r[3] >= 1f) return whole
        val pts = promptsFrom(g, gw, gh, r).toMutableList()
        for (p in sel.points) if (p.label == 0 || p.label == 1) pts += MaskPoint((p.x - r[0]) / (r[2] - r[0]), (p.y - r[1]) / (r[3] - r[1]), p.label)
        val hint = hintFor(whole, r, W, H, { x, y -> floatArrayOf(x, y) }, null, gw, gh)
        return fit(context, frame, r, pts, hint) ?: whole
    }

    /** One step from [prev] (on [prevFrame]) to [frame]; null = lost (cut or gone). */
    private fun advance(context: Context, prev: St, prevFrame: Bitmap, frame: Bitmap): St? {
        if (prev.entry.empty) return null
        val W = frame.width; val H = frame.height
        val a = small(frame); val gw = a.width; val gh = a.height
        val b = Bitmap.createScaledBitmap(prevFrame, gw, gh, true)
        if (isCut(a, b)) return null
        val f = FloatArray(gw * gh * 2); val f2 = FloatArray(gw * gh * 2)
        if (!MorphNative.available || !MorphNative.flow(a, b, f) || !MorphNative.flow(b, a, f2)) return null
        fun back(x: Float, y: Float): FloatArray {
            val xi = (x * gw - 0.5f).roundToInt().coerceIn(0, gw - 1); val yi = (y * gh - 0.5f).roundToInt().coerceIn(0, gh - 1)
            val q = (yi * gw + xi) * 2
            return floatArrayOf(x + f[q] / gw, y + f[q + 1] / gh)
        }
        val u2 = FloatArray(gw * gh) { f2[it * 2] }; val v2 = FloatArray(gw * gh) { f2[it * 2 + 1] }
        val pg = toGrid(prev.entry, gw, gh)
        val carried = FloatArray(gw * gh); val bad = BooleanArray(gw * gh)
        for (y in 0 until gh) for (x in 0 until gw) {
            val i = y * gw + x
            val bx = x + f[i * 2]; val by = y + f[i * 2 + 1]
            // Forward-backward check: a pixel whose way back and forth doesn't meet was just uncovered.
            val rx = Sam.bilinear(u2, gw, gh, bx, by)
            val ry = Sam.bilinear(v2, gw, gh, bx, by)
            val e = sqrt((f[i * 2] + rx) * (f[i * 2] + rx) + (f[i * 2 + 1] + ry) * (f[i * 2 + 1] + ry))
            val mag = sqrt(f[i * 2] * f[i * 2] + f[i * 2 + 1] * f[i * 2 + 1])
            if (e > 1.5f + 0.05f * mag) { bad[i] = true; continue }
            carried[i] = bilF(pg, gw, gh, bx, by, 0f)
        }
        // A cut the colours didn't give away: motion that doesn't agree both ways over most of the frame.
        if (bad.count { it } > 0.45f * bad.size) return null
        // The object suddenly mostly gone (left the picture, or the shot changed).
        val pa = pg.count { it > 0.5f }
        if (pa >= 50 && carried.count { it > 0.5f } < 0.25f * pa) return null
        val r = rectFor(carried, gw, gh, W, H) ?: return null
        val pts = promptsFrom(carried, gw, gh, r)
        val hint = hintFor(prev, r, W, H, ::back, bad, gw, gh)
        val st = fit(context, frame, r, pts, hint, carried, gw, gh, bad) ?: return null
        // Disagreement check against the carried outline.
        val fg = toGrid(st.entry, gw, gh)
        var inter = 0; var uni = 0
        for (i in fg.indices) { val p = fg[i] > 0.5f; val c = carried[i] > 0.5f; if (p && c) inter++; if (p || c) uni++ }
        if (uni > 0 && inter.toFloat() / uni < 0.35f) {
            val e = st.entry
            val m = ByteArray(e.w * e.h)
            for (y in 0 until e.h) for (x in 0 until e.w) {
                val gx = (r[0] + (x + 0.5f) / e.w * (r[2] - r[0])) * gw - 0.5f; val gy = (r[1] + (y + 0.5f) / e.h * (r[3] - r[1])) * gh - 0.5f
                m[y * e.w + x] = (Sam.bilinear(carried, gw, gh, gx, gy) * 255f).roundToInt().coerceIn(0, 255).toByte()
            }
            return St(MaskEntry(r, e.w, e.h, m), hint ?: st.logits, st.cropW, st.cropH)
        }
        return st
    }

    private fun frameRate(context: Context, uri: Uri): Float = try {
        val ex = MediaExtractor(); ex.setDataSource(context, uri, null)
        var r = 30f
        for (i in 0 until ex.trackCount) { val f = ex.getTrackFormat(i); if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true && f.containsKey(MediaFormat.KEY_FRAME_RATE)) { r = f.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat(); break } }
        ex.release(); r
    } catch (_: Throwable) { 30f }

    /** Tracks [sel] through [fromMs]..[toMs] (source time) of [uri], forwards and backwards from the picked frame. */
    fun track(context: Context, uri: Uri, fromMs: Long, toMs: Long, sel: MagicSel, onProgress: (Int) -> Unit, cancelled: () -> Boolean): MaskTrack? {
        if (!Sam.ready(context)) return null
        val f0 = FrameReader.frameAt(context, uri, sel.srcMs, FRAME_SIDE) ?: return null
        val s0 = first(context, f0, sel) ?: return null
        val out = java.util.TreeMap<Long, MaskEntry>()
        out[sel.srcMs] = s0.entry
        val fps = frameRate(context, uri).coerceIn(1f, 240f)
        val step = (max(1000f / fps, 1000f / 30f).toLong() - 1).coerceAtLeast(1)
        val total = ((toMs - fromMs).coerceAtLeast(1) / (step + 1)).coerceAtLeast(1)
        var done = 0L
        fun progress() { done++; onProgress((done * 100 / total).toInt().coerceIn(0, 99)) }
        // Forwards.
        var st: St? = s0; var pf = f0
        FrameReader.read(context, uri, sel.srcMs + 1, toMs, FRAME_SIDE, step) { t, bmp ->
            if (cancelled()) return@read false
            val cur = st
            st = if (cur == null) null else advance(context, cur, pf, bmp)
            out[t] = st?.entry ?: MaskEntry.NONE
            pf = bmp; progress(); true
        }
        if (cancelled()) return null
        // Backwards: frames before the picked one are decoded forwards into a temporary folder, then walked back.
        if (sel.srcMs > fromMs) {
            val tmp = File(context.cacheDir, "mm_${System.nanoTime()}").apply { mkdirs() }
            try {
                val list = ArrayList<Pair<Long, File>>()
                FrameReader.read(context, uri, fromMs, sel.srcMs - 1, FRAME_SIDE, step) { t, bmp ->
                    if (cancelled()) return@read false
                    val f = File(tmp, "$t.jpg"); f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    list += t to f; true
                }
                st = s0; pf = f0
                for ((t, f) in list.asReversed()) {
                    if (cancelled()) return null
                    val bmp = BitmapFactory.decodeFile(f.path) ?: continue
                    val cur = st
                    st = if (cur == null) null else advance(context, cur, pf, bmp)
                    out[t] = st?.entry ?: MaskEntry.NONE
                    pf = bmp; progress()
                }
            } finally { tmp.deleteRecursively() }
        }
        if (cancelled()) return null
        onProgress(100)
        return MaskTrack(out.keys.toLongArray(), out.values.toTypedArray())
    }

    /** The pixel deepest inside a binary mask (largest distance to its edge, two-pass chamfer). */
    private fun deepest(m: ByteArray, w: Int, h: Int): Pair<Int, Int> {
        val d = FloatArray(w * h) { if (m[it].toInt() != 0) 1e9f else 0f }
        val s2 = sqrt(2f)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x; if (d[i] == 0f) continue
            var v = d[i]
            if (x > 0) v = min(v, d[i - 1] + 1); if (y > 0) v = min(v, d[i - w] + 1)
            if (x > 0 && y > 0) v = min(v, d[i - w - 1] + s2); if (x < w - 1 && y > 0) v = min(v, d[i - w + 1] + s2)
            if (x == 0 || y == 0) v = min(v, 1f)
            d[i] = v
        }
        var best = 0; var bv = -1f
        for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
            val i = y * w + x; if (d[i] == 0f) continue
            var v = d[i]
            if (x < w - 1) v = min(v, d[i + 1] + 1); if (y < h - 1) v = min(v, d[i + w] + 1)
            if (x < w - 1 && y < h - 1) v = min(v, d[i + w + 1] + s2); if (x > 0 && y < h - 1) v = min(v, d[i + w - 1] + s2)
            if (x == w - 1 || y == h - 1) v = min(v, 1f)
            d[i] = v
            if (v > bv) { bv = v; best = i }
        }
        return (best % w) to (best / w)
    }
}
