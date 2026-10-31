package com.dronedirector.veyrocutpro.editor

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Object removal for video ,
 * on the phone. The object is the one Magic Mask follows. For every frame:
 *  1. the motion of the scene is worked out (AlcorCut Pro's own optical flow) and completed inside
 *     the object, so it describes the background behind it;
 *  2. each hidden pixel is looked up along that motion in the frames before and after, where the
 *     background is visible (the camera or the object moved) — real picture, not invented;
 *  3. only what is never seen in any nearby frame is painted by LaMa (Apache 2.0), on a few key
 *     frames, and carried along with the motion so it stays steady instead of flickering.
 * The result is a new video file of the clip's used range, with its sound.
 */
object ObjectRemoval {
    private const val WORK = 640          // long side for the filling
    private const val FLOW = 256          // long side for the motion
    private const val R = 10              // frames looked at on each side

    private var env: OrtEnvironment? = null
    private var lama: OrtSession? = null
    @Volatile var failed: String? = null; private set

    @Synchronized internal fun ready(context: Context): Boolean {
        if (lama != null) return true
        if (!ModelStore.has(context, AiModel.INPAINT)) return false
        return try {
            val e = OrtEnvironment.getEnvironment()
            val o = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 6))
                try { addXnnpack(mapOf("intra_op_num_threads" to Runtime.getRuntime().availableProcessors().coerceIn(2, 6).toString())) } catch (_: Throwable) {}
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            lama = e.createSession(ModelStore.file(context, AiModel.INPAINT.files[0]).path, o); env = e
            true
        } catch (t: Throwable) { failed = t.message ?: t.javaClass.simpleName; false }
    }

    fun outFile(context: Context, c: Clip, maskId: String) =
        File(File(context.filesDir, "edits").apply { mkdirs() }, "erase_${listOf(c.uri.toString(), c.startMs, c.endMs, maskId).hashCode().toUInt().toString(16)}.mp4")

    /** One frame being worked on (working size). */
    private class F(val t: Long, val px: IntArray, val hole: BooleanArray, var fwd: FloatArray? = null, var bwd: FloatArray? = null)

    /** The filled part of one frame: [rect] (working px), its pixels and how much each replaces (0..255). */
    private class Patch(val rect: Rect, val jpeg: ByteArray, val alpha: ByteArray)

    /**
     * Removes the object followed by [track] from [c] (blocking, slow: minutes for a long clip).
     * Returns the new file, or null.
     */
    fun make(context: Context, c: Clip, track: MaskTrack, maskId: String, onProgress: (Int) -> Unit, stop: () -> Boolean): File? {
        if (!MorphNative.available) return null
        val out = outFile(context, c, maskId)
        if (out.exists() && out.length() > 4096) return out
        val canPaint = ready(context)
        val total = (c.endMs - c.startMs).coerceAtLeast(1)
        // ---- Pass 1: fill at working size.
        val patches = java.util.TreeMap<Long, Patch>()
        val win = ArrayDeque<F>()
        var ww = 0; var wh = 0; var fw = 0; var fh = 0
        var memory: IntArray? = null; var memValid: BooleanArray? = null
        var lastPaint = -1000
        var index = 0

        fun flowSize(b: Bitmap) = Bitmap.createScaledBitmap(b, fw, fh, true)
        fun toBmp(px: IntArray) = Bitmap.createBitmap(px, ww, wh, Bitmap.Config.ARGB_8888)

        fun finish(cur: F, k: Int) {
            val holeIdx = cur.hole.indices.filter { cur.hole[it] }
            val res = cur.px.copyOf()
            if (holeIdx.isEmpty()) { patches[cur.t] = Patch(Rect(0, 0, 0, 0), ByteArray(0), ByteArray(0)); return }
            val filled = BooleanArray(cur.px.size)
            val list = win.toList()
            val ci = k
            // 2. Look along the motion into the frames before and after.
            val sx = fw.toFloat() / ww; val sy = fh.toFloat() / wh
            for (p in holeIdx) {
                val x0 = (p % ww).toFloat(); val y0 = (p / ww).toFloat()
                var r = 0f; var g = 0f; var b = 0f; var wsum = 0f
                for (dir in intArrayOf(1, -1)) {
                    var x = x0; var y = y0; var j = ci
                    for (step in 1..R) {
                        // Frame j's own flow to its neighbour (forward: j → j+1, backward: j → j−1).
                        val fl = (if (dir > 0) list.getOrNull(j)?.fwd else list.getOrNull(j)?.bwd) ?: break
                        // Motion of the background at (x, y), sampled from the small flow field.
                        val fx = (x * sx).coerceIn(0f, fw - 1f); val fy = (y * sy).coerceIn(0f, fh - 1f)
                        val ix = fx.toInt(); val iy = fy.toInt(); val q = (iy * fw + ix) * 2
                        x += fl[q] / sx; y += fl[q + 1] / sy
                        j += dir
                        if (x < 0 || y < 0 || x >= ww - 1 || y >= wh - 1) break
                        val o = list.getOrNull(j) ?: break
                        val xi = x.toInt(); val yi = y.toInt(); val idx = yi * ww + xi
                        if (!o.hole[idx] && !o.hole[idx + 1] && !o.hole[idx + ww]) {
                            val c0 = bilinear(o.px, ww, x, y)
                            val w = 1f / step
                            r += ((c0 shr 16) and 255) * w; g += ((c0 shr 8) and 255) * w; b += (c0 and 255) * w; wsum += w
                            break
                        }
                    }
                }
                if (wsum > 0f) { res[p] = (0xFF shl 24) or ((r / wsum).roundToInt() shl 16) or ((g / wsum).roundToInt() shl 8) or (b / wsum).roundToInt(); filled[p] = true }
            }
            // 3. Never seen: carry the painted background along with the motion, and paint it again now and then.
            val back = cur.bwd   // this frame → the previous one
            val mem = memory; val mv = memValid
            if (mem != null && mv != null && back != null) {
                val nm = IntArray(mem.size); val nv = BooleanArray(mem.size)
                for (p in mem.indices) {
                    val x = (p % ww).toFloat(); val y = (p / ww).toFloat()
                    val fx = (x * sx).coerceIn(0f, fw - 1f); val fy = (y * sy).coerceIn(0f, fh - 1f)
                    val q = (fy.toInt() * fw + fx.toInt()) * 2
                    val px = x + back[q] / sx; val py = y + back[q + 1] / sy
                    if (px < 0 || py < 0 || px >= ww - 1 || py >= wh - 1) continue
                    val i0 = py.toInt() * ww + px.toInt()
                    if (!mv[i0]) continue
                    nm[p] = bilinear(mem, ww, px, py); nv[p] = true
                }
                memory = nm; memValid = nv
            } else { memory = null; memValid = null }
            val missing = holeIdx.filter { !filled[it] && !(memValid?.get(it) ?: false) }
            if (canPaint && missing.size > 30) {
                // Paint only what nobody has seen yet, with everything else (seen or painted before) as context,
                // so earlier painting is kept and the fill stays steady.
                val ctx = res.copyOf()
                val mem0 = memory; val mv0 = memValid
                if (mem0 != null && mv0 != null) for (p in holeIdx) if (!filled[p] && mv0[p]) ctx[p] = mem0[p]
                val paint = paint(ctx, missing.toHashSet(), ww, wh)
                if (paint != null) {
                    val nm = memory ?: IntArray(res.size); val nv = memValid ?: BooleanArray(res.size)
                    for (p in missing) { nm[p] = paint[p]; nv[p] = true }
                    memory = nm; memValid = nv; lastPaint = index
                }
            }
            val mm = memory; val mmv = memValid
            for (p in holeIdx) if (!filled[p]) {
                if (mm != null && mmv != null && mmv[p]) res[p] = mm[p]
                else res[p] = nearestFill(res, cur.hole, filled, ww, wh, p)
            }
            // Keep the painted memory in step with what was really seen this frame (it's the background too).
            if (mm != null && mmv != null) for (p in holeIdx) if (filled[p]) { mm[p] = res[p]; mmv[p] = true }
            patches[cur.t] = makePatch(res, cur.hole, ww, wh)
        }

        try {
            FrameReader.read(context, c.uri, c.startMs, c.endMs, WORK, 0) { t, bmp ->
                if (stop()) return@read false
                if (ww == 0) {
                    ww = bmp.width; wh = bmp.height
                    val k = FLOW.toFloat() / max(ww, wh); fw = (ww * k).roundToInt().coerceAtLeast(16); fh = (wh * k).roundToInt().coerceAtLeast(16)
                }
                val b = if (bmp.width == ww && bmp.height == wh) bmp else Bitmap.createScaledBitmap(bmp, ww, wh, true)
                val px = IntArray(ww * wh); b.getPixels(px, 0, ww, 0, 0, ww, wh)
                val f = F(t, px, holeOf(track.at(t), ww, wh))
                win.lastOrNull()?.let { prev ->
                    val a = flowSize(toBmp(prev.px)); val bb = flowSize(b)
                    val fwd = FloatArray(fw * fh * 2); val bwd = FloatArray(fw * fh * 2)
                    if (MorphNative.flow(a, bb, fwd) && MorphNative.flow(bb, a, bwd)) {
                        // The flow inside the object follows the object; replace it with the background's motion around it.
                        complete(fwd, prev.hole, ww, wh, fw, fh); complete(bwd, f.hole, ww, wh, fw, fh)
                        prev.fwd = fwd; f.bwd = bwd
                    }
                }
                win.addLast(f)
                // Work on the frame R behind, once R frames after it are known.
                if (win.size > 2 * R + 1) win.removeFirst()
                val ready = win.size - 1 - R
                if (ready >= 0 && win.size >= R + 1) {
                    val target = win[ready]
                    if (!patches.containsKey(target.t)) { finish(target, ready); index++ }
                }
                onProgress(((t - c.startMs) * 70 / total).toInt().coerceIn(0, 70))
                true
            }
            if (stop()) return null
            // The last frames (no frames after them).
            for (k in win.indices) if (!patches.containsKey(win[k].t)) { finish(win[k], k); index++ }
            if (patches.isEmpty()) return null
            // ---- Pass 2: the full-size picture with the filled part laid in.
            val tmp = File(out.path + ".part"); tmp.delete()
            var wr: FlowRetime.Writer? = null
            try {
                FrameReader.read(context, c.uri, c.startMs, c.endMs, 1920, 0) { t, raw ->
                    if (stop()) return@read false
                    val full = (if (raw.width % 2 == 0 && raw.height % 2 == 0) raw else Bitmap.createScaledBitmap(raw, raw.width / 2 * 2, raw.height / 2 * 2, true))
                        .let { if (it.isMutable && it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, true) }
                    val e = patches.floorEntry(t) ?: patches.ceilingEntry(t)
                    e?.value?.let { lay(full, it, ww, wh) }
                    val w = wr ?: FlowRetime.Writer(context, c, tmp, full.width, full.height, 30.0).also { wr = it }
                    if (full.width == w.w && full.height == w.h) w.still(full, t)
                    onProgress(70 + ((t - c.startMs) * 29 / total).toInt().coerceIn(0, 29))
                    true
                }
                val w = wr ?: return null
                if (stop()) return null
                w.finish()
                if (w.frames < 2) return null
            } finally { try { wr?.release() } catch (_: Throwable) {} }
            if (!tmp.renameTo(out)) return null
            onProgress(100)
            return out
        } catch (t: Throwable) {
            android.util.Log.w("ObjectRemoval", "failed: ${t.message}", t)
            failed = t.message
            return null
        }
    }

    /** The object (grown a little, so its soft edge and shadow line go too) at working size. */
    private fun holeOf(e: MaskEntry, w: Int, h: Int): BooleanArray {
        val m = BooleanArray(w * h)
        if (e.empty) return m
        val x0 = (e.rect[0] * w).toInt(); val y0 = (e.rect[1] * h).toInt(); val x1 = (e.rect[2] * w).toInt(); val y1 = (e.rect[3] * h).toInt()
        val rw = (x1 - x0).coerceAtLeast(1); val rh = (y1 - y0).coerceAtLeast(1)
        for (y in max(0, y0) until min(h, y1)) for (x in max(0, x0) until min(w, x1)) {
            val mx = ((x - x0) * e.w / rw).coerceIn(0, e.w - 1); val my = ((y - y0) * e.h / rh).coerceIn(0, e.h - 1)
            if ((e.bytes[my * e.w + mx].toInt() and 255) > 100) m[y * w + x] = true
        }
        // Grow by ~1.2 % of the picture.
        val g = max(2, (max(w, h) * 0.012f).roundToInt())
        return dilate(m, w, h, g)
    }

    private fun dilate(m: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
        val a = BooleanArray(m.size)
        for (y in 0 until h) { var last = -100000; for (x in 0 until w) { if (m[y * w + x]) last = x; if (x - last <= r) a[y * w + x] = true }; last = 100000; for (x in w - 1 downTo 0) { if (m[y * w + x]) last = x; if (last - x <= r) a[y * w + x] = true } }
        val b = BooleanArray(m.size)
        for (x in 0 until w) { var last = -100000; for (y in 0 until h) { if (a[y * w + x]) last = y; if (y - last <= r) b[y * w + x] = true }; last = 100000; for (y in h - 1 downTo 0) { if (a[y * w + x]) last = y; if (last - y <= r) b[y * w + x] = true } }
        return b
    }

    /** Replaces the flow under [hole] (working size) by a smooth fill from the flow around it. */
    private fun complete(fl: FloatArray, hole: BooleanArray, ww: Int, wh: Int, fw: Int, fh: Int) {
        val inH = BooleanArray(fw * fh)
        for (y in 0 until fh) for (x in 0 until fw) {
            val hx = (x * ww / fw).coerceIn(0, ww - 1); val hy = (y * wh / fh).coerceIn(0, wh - 1)
            inH[y * fw + x] = hole[hy * ww + hx]
        }
        if (inH.none { it }) return
        // Start from the average motion around the object, then relax (Laplace) for a smooth fill.
        var su = 0f; var sv = 0f; var n = 0
        for (i in inH.indices) if (!inH[i]) { su += fl[2 * i]; sv += fl[2 * i + 1]; n++ }
        if (n == 0) return
        for (i in inH.indices) if (inH[i]) { fl[2 * i] = su / n; fl[2 * i + 1] = sv / n }
        repeat(120) {
            for (y in 0 until fh) for (x in 0 until fw) {
                val i = y * fw + x
                if (!inH[i]) continue
                var u = 0f; var v = 0f; var k = 0
                if (x > 0) { u += fl[2 * (i - 1)]; v += fl[2 * (i - 1) + 1]; k++ }
                if (x < fw - 1) { u += fl[2 * (i + 1)]; v += fl[2 * (i + 1) + 1]; k++ }
                if (y > 0) { u += fl[2 * (i - fw)]; v += fl[2 * (i - fw) + 1]; k++ }
                if (y < fh - 1) { u += fl[2 * (i + fw)]; v += fl[2 * (i + fw) + 1]; k++ }
                if (k > 0) { fl[2 * i] = u / k; fl[2 * i + 1] = v / k }
            }
        }
    }

    private fun bilinear(px: IntArray, w: Int, x: Float, y: Float): Int {
        val h = px.size / w
        val x0 = x.toInt().coerceIn(0, w - 1); val y0 = y.toInt().coerceIn(0, h - 1)
        val x1 = (x0 + 1).coerceAtMost(w - 1); val y1 = (y0 + 1).coerceAtMost(h - 1)
        val fx = (x - x0).coerceIn(0f, 1f); val fy = (y - y0).coerceIn(0f, 1f)
        val a = px[y0 * w + x0]; val b = px[y0 * w + x1]; val c = px[y1 * w + x0]; val d = px[y1 * w + x1]
        fun ch(s: Int): Int {
            val va = (a shr s) and 255; val vb = (b shr s) and 255; val vc = (c shr s) and 255; val vd = (d shr s) and 255
            return (va * (1 - fx) * (1 - fy) + vb * fx * (1 - fy) + vc * (1 - fx) * fy + vd * fx * fy).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Last resort (no painting model): the nearest filled or visible colour. */
    private fun nearestFill(res: IntArray, hole: BooleanArray, filled: BooleanArray, w: Int, h: Int, p: Int): Int {
        val x = p % w; val y = p / w
        for (r in 1 until max(w, h)) {
            for ((dx, dy) in listOf(r to 0, -r to 0, 0 to r, 0 to -r)) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until w && yy in 0 until h) { val q = yy * w + xx; if (!hole[q] || filled[q]) return res[q] }
            }
        }
        return res[p]
    }

    /** Runs LaMa once: [img] 3×512×512 (0..1 RGB planes), [msk] 512×512 (1 = paint). Returns 3×512×512 in 0..255, or null. */
    @Synchronized internal fun lama512(img: FloatArray, msk: FloatArray): FloatArray? {
        val e = env ?: return null; val s = lama ?: return null
        val out = try {
            val ti = OnnxTensor.createTensor(e, FloatBuffer.wrap(img), longArrayOf(1, 3, 512, 512))
            val tm = OnnxTensor.createTensor(e, FloatBuffer.wrap(msk), longArrayOf(1, 1, 512, 512))
            try {
                s.run(mapOf("image" to ti, "mask" to tm)).use { r -> val fb = (r[0] as OnnxTensor).floatBuffer; FloatArray(fb.remaining()).also { fb.get(it) } }
            } finally { ti.close(); tm.close() }
        } catch (t: Throwable) { failed = t.message; return null }
        if ((out.maxOrNull() ?: 0f) <= 2f) for (i in out.indices) out[i] *= 255f
        return out
    }

    /** LaMa on a square round the hole (scaled to 512 × 512). Returns the whole working picture with the hole painted. */
    private fun paint(px: IntArray, hole: Set<Int>, w: Int, h: Int): IntArray? {
        if (hole.isEmpty()) return null
        val e = env ?: return null; val s = lama ?: return null
        var x0 = w; var y0 = h; var x1 = 0; var y1 = 0
        for (p in hole) { val x = p % w; val y = p / w; x0 = min(x0, x); y0 = min(y0, y); x1 = max(x1, x); y1 = max(y1, y) }
        // Enough surroundings to paint from: twice the hole, at least a third of the picture.
        val side = max(max(x1 - x0, y1 - y0) * 2, min(w, h) / 3).coerceAtMost(max(w, h))
        val cx = (x0 + x1) / 2; val cy = (y0 + y1) / 2
        val sx0 = (cx - side / 2).coerceIn(0, max(0, w - side)); val sy0 = (cy - side / 2).coerceIn(0, max(0, h - side))
        val sw = min(side, w); val sh = min(side, h)
        val n = 512 * 512
        val img = FloatArray(3 * n); val msk = FloatArray(n)
        for (y in 0 until 512) for (x in 0 until 512) {
            val ox = sx0 + x * sw / 512; val oy = sy0 + y * sh / 512
            val q = oy.coerceIn(0, h - 1) * w + ox.coerceIn(0, w - 1)
            val c = px[q]; val i = y * 512 + x
            img[i] = ((c shr 16) and 255) / 255f; img[n + i] = ((c shr 8) and 255) / 255f; img[2 * n + i] = (c and 255) / 255f
            msk[i] = if (q in hole) 1f else 0f
        }
        // Grow the mask a pixel or two at model size (softens the seam).
        val outPx = try {
            val ti = OnnxTensor.createTensor(e, FloatBuffer.wrap(img), longArrayOf(1, 3, 512, 512))
            val tm = OnnxTensor.createTensor(e, FloatBuffer.wrap(msk), longArrayOf(1, 1, 512, 512))
            try {
                s.run(mapOf("image" to ti, "mask" to tm)).use { r ->
                    val fb = (r[0] as OnnxTensor).floatBuffer
                    FloatArray(fb.remaining()).also { fb.get(it) }
                }
            } finally { ti.close(); tm.close() }
        } catch (t: Throwable) { failed = t.message; return null }
        val scale = if (outPx.maxOrNull() ?: 0f > 2f) 1f else 255f
        val res = px.copyOf()
        for (p in hole) {
            val x = p % w; val y = p / w
            val mx = ((x - sx0) * 512f / sw).coerceIn(0f, 511f); val my = ((y - sy0) * 512f / sh).coerceIn(0f, 511f)
            val i = my.toInt() * 512 + mx.toInt()
            val r = (outPx[i] * scale).roundToInt().coerceIn(0, 255); val g = (outPx[n + i] * scale).roundToInt().coerceIn(0, 255); val b = (outPx[2 * n + i] * scale).roundToInt().coerceIn(0, 255)
            res[p] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return res
    }

    /** The filled part of a frame, with a soft edge, packed small. */
    private fun makePatch(res: IntArray, hole: BooleanArray, w: Int, h: Int): Patch {
        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
        for (i in hole.indices) if (hole[i]) { val x = i % w; val y = i / w; x0 = min(x0, x); y0 = min(y0, y); x1 = max(x1, x); y1 = max(y1, y) }
        if (x1 < 0) return Patch(Rect(0, 0, 0, 0), ByteArray(0), ByteArray(0))
        val pad = 3
        x0 = max(0, x0 - pad); y0 = max(0, y0 - pad); x1 = min(w - 1, x1 + pad); y1 = min(h - 1, y1 + pad)
        val pw = x1 - x0 + 1; val ph = y1 - y0 + 1
        val crop = IntArray(pw * ph); val alpha = ByteArray(pw * ph)
        // Soft edge: distance into the hole (a few pixels).
        for (y in 0 until ph) for (x in 0 until pw) {
            val p = (y0 + y) * w + (x0 + x)
            crop[y * pw + x] = res[p]
            var a = 0f
            if (hole[p]) {
                var near = 3
                loop@ for (r in 1..3) for ((dx, dy) in listOf(r to 0, -r to 0, 0 to r, 0 to -r)) {
                    val xx = x0 + x + dx; val yy = y0 + y + dy
                    if (xx in 0 until w && yy in 0 until h && !hole[yy * w + xx]) { near = r - 1; break@loop }
                }
                a = (near + 1) / 4f
            }
            alpha[y * pw + x] = (a * 255).roundToInt().coerceIn(0, 255).toByte()
        }
        val bos = ByteArrayOutputStream()
        Bitmap.createBitmap(crop, pw, ph, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 95, bos)
        return Patch(Rect(x0, y0, x1 + 1, y1 + 1), bos.toByteArray(), alpha)
    }

    /** Lays a working-size patch into the full-size frame. */
    private fun lay(full: Bitmap, p: Patch, ww: Int, wh: Int) {
        if (p.rect.isEmpty || p.jpeg.isEmpty()) return
        val crop = android.graphics.BitmapFactory.decodeByteArray(p.jpeg, 0, p.jpeg.size) ?: return
        val pw = p.rect.width(); val ph = p.rect.height()
        val px = IntArray(pw * ph); crop.getPixels(px, 0, pw, 0, 0, pw, ph)
        for (i in px.indices) px[i] = (px[i] and 0x00FFFFFF) or ((p.alpha[i].toInt() and 255) shl 24)
        val withAlpha = Bitmap.createBitmap(px, pw, ph, Bitmap.Config.ARGB_8888)
        val sx = full.width.toFloat() / ww; val sy = full.height.toFloat() / wh
        val dst = android.graphics.RectF(p.rect.left * sx, p.rect.top * sy, p.rect.right * sx, p.rect.bottom * sy)
        Canvas(full).drawBitmap(withAlpha, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }
}
