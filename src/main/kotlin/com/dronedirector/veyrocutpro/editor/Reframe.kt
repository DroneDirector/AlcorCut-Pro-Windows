package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.ImageFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Crop / zoom / reposition of a clip; optionally animated from [start] to [end] over the clip. */
data class Frame(val zoom: Float = 1f, val x: Float = 0f, val y: Float = 0f, val rotation: Float = 0f)

/**
 * A clip keyframe: framing and opacity at [t] (source ms from the clip's in point), and how it
 * eases to the next key — 0 smooth, 1 linear, 2 hold.
 */
data class ClipKey(
    val t: Long, val frame: Frame = Frame(), val opacity: Float = 1f, val ease: Int = 0,
    /** ease 3 = own curve: cubic Bézier handles x1, y1, x2, y2 (x 0..1; y may overshoot), like a graph editor. */
    val bez: List<Float>? = null,
)

/** Keyframe curves: the eased progress for [u] (0..1) under [a]'s easing. */
object KeyCurve {
    val PRESETS = listOf(
        "Ease in" to listOf(0.42f, 0f, 1f, 1f), "Ease out" to listOf(0f, 0f, 0.58f, 1f), "Ease in-out" to listOf(0.42f, 0f, 0.58f, 1f),
        "Strong in-out" to listOf(0.85f, 0f, 0.15f, 1f), "Back (overshoot)" to listOf(0.34f, 1.56f, 0.64f, 1f), "Anticipate" to listOf(0.36f, -0.4f, 0.66f, 1f),
    )
    private fun bz(t: Float, p1: Float, p2: Float): Float { val m = 1 - t; return 3 * m * m * t * p1 + 3 * m * t * t * p2 + t * t * t }
    /** Cubic-Bézier timing (CSS style): solve x(t) = u, return y(t). */
    fun bezier(u: Float, c: List<Float>): Float {
        if (c.size < 4) return u
        val x1 = c[0].coerceIn(0f, 1f); val x2 = c[2].coerceIn(0f, 1f)
        var lo = 0f; var hi = 1f; var t = u
        repeat(24) { t = (lo + hi) / 2; if (bz(t, x1, x2) < u) lo = t else hi = t }
        return bz(t, c[1], c[3])
    }
    fun eased(k: ClipKey, u: Float): Float = when (k.ease) { 1 -> u; 2 -> 0f; 3 -> bezier(u, k.bez ?: PRESETS[2].second); else -> u * u * (3f - 2f * u) }
}

data class Reframe(
    val start: Frame = Frame(),
    val end: Frame = Frame(),
    val animate: Boolean = false,
    val flip: Boolean = false,
    /** Auto-reframe: horizontal shift (fraction of the frame) at evenly spaced points through the clip. */
    val track: List<Float>? = null,
    /** Keyframes (zoom, position, rotation, opacity); when present they replace start / end. */
    val keys: List<ClipKey>? = null,
) {
    val isIdentity get() = start == Frame() && (!animate || end == Frame()) && !flip && track.isNullOrEmpty() && keys.isNullOrEmpty()
    val hasKeys: Boolean get() = !keys.isNullOrEmpty()
    /** Any keyframe below full opacity (needs the opacity effect on export). */
    val hasOpacity: Boolean get() = keys?.any { it.opacity < 0.999f } == true

    /** The keyframed framing / opacity at [rel] ms from the in point, or null without keyframes. */
    fun keyedAt(rel: Long): ClipKey? {
        val k = keys?.sortedBy { it.t } ?: return null
        if (k.isEmpty()) return null
        if (rel <= k.first().t) return k.first().copy(t = rel)
        if (rel >= k.last().t) return k.last().copy(t = rel)
        val i = k.indexOfLast { it.t <= rel }.coerceIn(0, k.size - 2)
        val a = k[i]; val b = k[i + 1]
        val u = ((rel - a.t).toFloat() / (b.t - a.t).coerceAtLeast(1)).coerceIn(0f, 1f)
        val e = KeyCurve.eased(a, u)
        fun l(x: Float, y: Float) = x + (y - x) * e
        return ClipKey(
            rel,
            Frame(l(a.frame.zoom, b.frame.zoom), l(a.frame.x, b.frame.x), l(a.frame.y, b.frame.y), l(a.frame.rotation, b.frame.rotation)),
            l(a.opacity, b.opacity).coerceIn(0f, 1f), a.ease, a.bez,
        )
    }

    fun opacityAt(rel: Long): Float = keyedAt(rel)?.opacity ?: 1f

    /** Adds or replaces the keyframe at [t] (within [tolMs]). */
    fun upsertKey(t: Long, frame: Frame, opacity: Float, tolMs: Long = 40): Reframe {
        val list = (keys ?: emptyList()).filter { kotlin.math.abs(it.t - t) > tolMs }
        val ease = keys?.firstOrNull { kotlin.math.abs(it.t - t) <= tolMs }?.ease ?: 0
        return copy(keys = (list + ClipKey(t.coerceAtLeast(0), frame, opacity.coerceIn(0f, 1f), ease)).sortedBy { it.t }, animate = false)
    }
    fun at(f: Float): Frame {
        val t = track
        if (!t.isNullOrEmpty()) {
            val x = f.coerceIn(0f, 1f) * (t.size - 1)
            val i = x.toInt().coerceAtMost(t.size - 2).coerceAtLeast(0)
            val tx = if (t.size == 1) t[0] else t[i] + (t[i + 1] - t[i]) * (x - i)
            return (if (animate) atKenBurns(f) else start).copy(x = tx)
        }
        return atKenBurns(f)
    }

    private fun atKenBurns(f: Float): Frame {
        if (!animate) return start
        val e = TransitionMath.ease(f.coerceIn(0f, 1f))
        return Frame(
            start.zoom + (end.zoom - start.zoom) * e, start.x + (end.x - start.x) * e,
            start.y + (end.y - start.y) * e, start.rotation + (end.rotation - start.rotation) * e,
        )
    }
}

data class Stabilize(
    val enabled: Boolean = false, val strength: Float = 0.6f, val tripod: Boolean = false, val autoZoom: Boolean = true,
    /** Use the phone's motion data (Capture's .gcsv) instead of the picture alone; and a file picked by hand. */
    val gyro: Boolean? = null, val gyroFile: String? = null,
)

/** Final transform at a moment: scale, rotation (degrees, clockwise) and shift (fraction of the frame, y down). */
data class Xform(val scale: Float, val rotation: Float, val tx: Float, val ty: Float, val flip: Boolean)

object ClipXform {
    /** Combined crop + stabilisation transform at [sourceMs] inside the clip. */
    fun at(c: Clip, sourceMs: Long, motion: Motion?): Xform {
        val f = c.reframe.keyedAt(sourceMs - c.startMs)?.frame
            ?: c.reframe.at(((sourceMs - c.startMs).toFloat() / (c.endMs - c.startMs).coerceAtLeast(1)))
        var s = f.zoom; var r = f.rotation; var tx = f.x; var ty = f.y
        if (c.stab.enabled && motion != null) {
            val corr = motion.correction(c.stab, sourceMs, c.startMs, c.endMs)
            // user ∘ stab: T = Tu + Ru·Su·Ts, R = Ru·Rs, S = Su·Ss
            val rad = Math.toRadians(r.toDouble())
            val cs = Math.cos(rad).toFloat(); val sn = Math.sin(rad).toFloat()
            val aspect = c.width.toFloat() / c.height.coerceAtLeast(1)
            // rotate the stab shift in pixel space (x scaled by aspect)
            val sx = corr.tx * aspect; val sy = corr.ty
            tx += (s * (cs * sx - sn * sy)) / aspect
            ty += s * (sn * sx + cs * sy)
            s *= corr.scale
            r += corr.rotation
        }
        return Xform(s, r, tx, ty, c.reframe.flip)
    }

    /** android.graphics.Matrix in normalised device coordinates for the export. */
    fun ndcMatrix(x: Xform, aspect: Float): android.graphics.Matrix {
        val m = android.graphics.Matrix()
        // NDC → centred pixels (height = 2 units), y down.
        m.setScale(aspect, -1f)
        if (x.flip) m.postScale(-1f, 1f)
        m.postScale(x.scale, x.scale)
        m.postRotate(x.rotation)
        m.postTranslate(x.tx * 2f * aspect, x.ty * 2f)
        m.postScale(1f / aspect, -1f)
        return m
    }

    /** Preview: the same transform on the player's view. */
    fun applyToView(v: android.view.View, x: Xform) {
        v.pivotX = v.width / 2f; v.pivotY = v.height / 2f
        v.scaleX = x.scale * (if (x.flip) -1f else 1f); v.scaleY = x.scale
        v.rotation = x.rotation
        v.translationX = x.tx * v.width; v.translationY = x.ty * v.height
    }
}

/** Export effect: crop / zoom / stabilise frame by frame. */
@UnstableApi
class ReframeEffect(private val c: Clip, private val motion: Motion?, private val offsetMs: Long = 0L) : MatrixTransformation {
    private var t0 = Long.MIN_VALUE
    private val aspect = c.width.toFloat() / c.height.coerceAtLeast(1)
    override fun getMatrix(presentationTimeUs: Long): android.graphics.Matrix {
        if (t0 == Long.MIN_VALUE) t0 = presentationTimeUs
        val src = c.srcAtOffset(offsetMs + (presentationTimeUs - t0) / 1000)
        return ClipXform.ndcMatrix(ClipXform.at(c, src, motion), aspect)
    }
}

/** Camera path measured from a clip: cumulative shift (fraction of frame, y down) and rotation (degrees). */
class Motion(val timesMs: LongArray, val x: FloatArray, val y: FloatArray, val a: FloatArray) {
    private var cacheKey: Triple<Stabilize, Long, Long>? = null
    private var cx = FloatArray(0); private var cy = FloatArray(0); private var ca = FloatArray(0)
    private var zoom = 1f

    private fun build(s: Stabilize, from: Long, to: Long) {
        val n = timesMs.size
        cx = FloatArray(n); cy = FloatArray(n); ca = FloatArray(n)
        if (n == 0) return
        if (s.tripod) {
            val r0 = timesMs.indexOfFirst { it >= from }.coerceAtLeast(0)
            for (i in 0 until n) { cx[i] = x[r0] - x[i]; cy[i] = y[r0] - y[i]; ca[i] = a[r0] - a[i] }
        } else {
            val step = if (n > 1) (timesMs[n - 1] - timesMs[0]).toFloat() / (n - 1) else 100f
            val sigma = max(1f, (0.25f + s.strength * 2.2f) * 1000f / step)
            val r = (sigma * 3).toInt()
            for (i in 0 until n) {
                var sx = 0f; var sy = 0f; var sa = 0f; var ws = 0f
                for (j in max(0, i - r)..min(n - 1, i + r)) {
                    val w = exp(-((j - i) * (j - i)) / (2 * sigma * sigma))
                    sx += x[j] * w; sy += y[j] * w; sa += a[j] * w; ws += w
                }
                cx[i] = sx / ws - x[i]; cy[i] = sy / ws - y[i]; ca[i] = sa / ws - a[i]
            }
        }
        var need = 0f
        for (i in 0 until n) if (timesMs[i] in from..to) need = max(need, max(abs(cx[i]), abs(cy[i])) * 2f + abs(ca[i]) * 0.02f)
        zoom = if (s.autoZoom) (1f + need).coerceAtMost(1.5f) else 1f
        cacheKey = Triple(s, from, to)
    }

    @Synchronized
    fun correction(s: Stabilize, sourceMs: Long, from: Long, to: Long): Xform {
        if (cacheKey != Triple(s, from, to)) build(s, from, to)
        val n = timesMs.size
        if (n == 0) return Xform(1f, 0f, 0f, 0f, false)
        var i = timesMs.binarySearch(sourceMs).let { if (it < 0) -it - 2 else it }.coerceIn(0, n - 1)
        val j = min(i + 1, n - 1)
        val f = if (j == i) 0f else ((sourceMs - timesMs[i]).toFloat() / (timesMs[j] - timesMs[i])).coerceIn(0f, 1f)
        fun lerp(arr: FloatArray) = arr[i] + (arr[j] - arr[i]) * f
        return Xform(zoom, lerp(ca), lerp(cx), lerp(cy), false)
    }
}

/**
 * Measures camera shake: decodes the clip, compares consecutive frames on a small grey copy
 * (block matching) and builds the camera path. Results are cached on disk per clip range.
 */
object Stabilizer {
    private val memory = HashMap<String, Motion>()

    fun keyOf(c: Clip) = c.uri.toString() + if (c.stab.gyro == true) "|gyro|" + (c.stab.gyroFile ?: "") else ""
    private fun key(c: Clip) = keyOf(c)
    private fun file(context: Context, c: Clip) = File(MediaCatalog.sub(context, "stab"), "m_" + key(c).hashCode().toUInt().toString(16) + ".bin")
        .also { MediaCatalog.claim(context, c.uri, it) }

    fun cached(context: Context, c: Clip): Motion? {
        memory[key(c)]?.let { return it }
        val f = file(context, c)
        if (!f.exists()) return null
        return try {
            DataInputStream(f.inputStream().buffered()).use { s ->
                val n = s.readInt()
                val t = LongArray(n) { s.readLong() }
                val x = FloatArray(n) { s.readFloat() }; val y = FloatArray(n) { s.readFloat() }; val a = FloatArray(n) { s.readFloat() }
                Motion(t, x, y, a).also { memory[key(c)] = it }
            }
        } catch (_: Throwable) { null }
    }

    private fun save(context: Context, c: Clip, m: Motion) {
        memory[key(c)] = m
        try {
            DataOutputStream(file(context, c).outputStream().buffered()).use { s ->
                s.writeInt(m.timesMs.size)
                m.timesMs.forEach { s.writeLong(it) }
                m.x.forEach { s.writeFloat(it) }; m.y.forEach { s.writeFloat(it) }; m.a.forEach { s.writeFloat(it) }
            }
        } catch (_: Throwable) {}
    }

    /** Analyses in the background; returns a cancel function. Callbacks arrive on the main thread. */
    fun analyze(context: Context, c: Clip, onProgress: (Int) -> Unit, onDone: (Motion?, String?) -> Unit): () -> Unit {
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val main = Handler(Looper.getMainLooper())
        Thread {
            val optical = try { run(context, c, { cancelled.get() }) { p -> main.post { onProgress(p) } } } catch (t: Throwable) { null }
            if (cancelled.get()) return@Thread
            // Gyro: line the motion file up with the picture-based path and use the gyro's path instead.
            var note: String? = null
            val result = if (optical != null && c.stab.gyro == true) {
                (try { GyroStab.fuse(context, c, optical) } catch (_: Throwable) { null })
                    ?: optical.also { note = "No matching motion file for ${c.name} — stabilised from the picture instead" }
            } else optical
            if (result != null) save(context, c, result)
            main.post { onDone(result, if (result == null) "Couldn't analyse this clip" else note ?: if (c.stab.gyro == true) "Gyro stabilisation ready for ${c.name}" else null) }
        }.apply { priority = Thread.MIN_PRIORITY + 1 }.start()
        return { cancelled.set(true) }
    }

    private const val AW = 160  // analysis width (decoded orientation)

    private fun run(context: Context, c: Clip, cancelled: () -> Boolean, progress: (Int) -> Unit): Motion? {
        val from = 0L
        val to = c.durationMs
        val ex = MediaExtractor()
        ex.setDataSource(context, c.uri, null)
        var track = -1
        var fmt: MediaFormat? = null
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) { track = i; fmt = f; break }
        }
        if (track < 0 || fmt == null) { ex.release(); return null }
        ex.selectTrack(track)
        val rotation = if (fmt.containsKey("rotation-degrees")) fmt.getInteger("rotation-degrees") else 0
        val mime = fmt.getString(MediaFormat.KEY_MIME)!!
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(fmt, null, null, 0)
        codec.start()
        ex.seekTo(from * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        val stepMs = 1000L / 15
        val times = ArrayList<Long>(); val xs = ArrayList<Float>(); val ys = ArrayList<Float>(); val angs = ArrayList<Float>()
        var prev: FloatArray? = null
        var pw = 0; var ph = 0
        var cx = 0f; var cy = 0f; var ca = 0f
        var nextSample = from
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        val span = (to - from).coerceAtLeast(1)
        try {
            while (!outputDone) {
                if (cancelled()) return null
                if (!inputDone) {
                    val ii = codec.dequeueInputBuffer(5000)
                    if (ii >= 0) {
                        val buf = codec.getInputBuffer(ii)!!
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0 || ex.sampleTime > to * 1000 + 100_000) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                        } else {
                            codec.queueInputBuffer(ii, 0, size, ex.sampleTime, 0); ex.advance()
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 5000)
                if (oi >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    val tMs = info.presentationTimeUs / 1000
                    if (info.size > 0 && tMs >= nextSample && tMs <= to) {
                        val img = codec.getOutputImage(oi)
                        if (img != null) {
                            val g = grey(img)
                            pw = AW; ph = (AW * img.height / img.width.coerceAtLeast(1)).coerceAtLeast(16)
                            img.close()
                            val p = prev
                            if (p != null) {
                                val m = estimate(p, g, pw, ph)
                                // decoded → display orientation
                                val (dx, dy) = when (rotation) {
                                    90 -> -m[1] to m[0]
                                    180 -> -m[0] to -m[1]
                                    270 -> m[1] to -m[0]
                                    else -> m[0] to m[1]
                                }
                                val (dw, dh) = if (rotation == 90 || rotation == 270) ph to pw else pw to ph
                                cx += dx / dw; cy += dy / dh; ca += m[2]
                            }
                            prev = g
                            times += tMs; xs += cx; ys += cy; angs += ca
                            nextSample = tMs + stepMs
                            progress(((tMs - from) * 100 / span).toInt().coerceIn(0, 99))
                        }
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if (tMs > to) outputDone = true
                }
            }
        } finally {
            try { codec.stop() } catch (_: Throwable) {}
            codec.release(); ex.release()
        }
        if (times.size < 3) return null
        return Motion(times.toLongArray(), xs.toFloatArray(), ys.toFloatArray(), angs.toFloatArray())
    }

    /** Small grey copy (AW wide) of the Y plane; works for 8-bit and 10-bit frames. */
    private fun grey(img: android.media.Image): FloatArray {
        val plane = img.planes[0]
        val buf = plane.buffer
        val rs = plane.rowStride; val ps = plane.pixelStride
        val w = img.width; val h = img.height
        val oh = (AW * h / w.coerceAtLeast(1)).coerceAtLeast(16)
        val out = FloatArray(AW * oh)
        val tenBit = android.os.Build.VERSION.SDK_INT >= 33 && img.format == ImageFormat.YCBCR_P010
        for (y in 0 until oh) {
            val sy = y * h / oh
            for (x in 0 until AW) {
                val sx = x * w / AW
                val idx = sy * rs + sx * ps + (if (tenBit) 1 else 0)
                out[y * AW + x] = if (idx < buf.limit()) (buf.get(idx).toInt() and 0xFF).toFloat() else 0f
            }
        }
        return out
    }

    /** Global motion between two frames: dx, dy (pixels, content moved by) and rotation (degrees). */
    private fun estimate(a: FloatArray, b: FloatArray, w: Int, h: Int): FloatArray {
        val bs = 12; val r = 10
        val vx = ArrayList<Float>(); val vy = ArrayList<Float>(); val px = ArrayList<Float>(); val py = ArrayList<Float>()
        val cols = 6; val rows = 4
        for (gy in 0 until rows) for (gx in 0 until cols) {
            val x0 = r + 2 + (w - 2 * r - bs - 4) * gx / (cols - 1)
            val y0 = r + 2 + (h - 2 * r - bs - 4) * gy / (rows - 1)
            if (y0 < r || y0 + bs + r >= h) continue
            // Skip flat blocks: nothing to match.
            var mean = 0f
            for (yy in 0 until bs) for (xx in 0 until bs) mean += a[(y0 + yy) * w + x0 + xx]
            mean /= bs * bs
            var v = 0f
            for (yy in 0 until bs) for (xx in 0 until bs) { val d = a[(y0 + yy) * w + x0 + xx] - mean; v += d * d }
            if (v / (bs * bs) < 25f) continue
            var best = Float.MAX_VALUE; var bx = 0; var by = 0
            for (dy in -r..r) for (dx in -r..r) {
                var sad = 0f
                var yy = 0
                while (yy < bs) {
                    val ra = (y0 + yy) * w + x0; val rb = (y0 + yy + dy) * w + x0 + dx
                    var xx = 0
                    while (xx < bs) { sad += abs(a[ra + xx] - b[rb + xx]); xx += 2 }
                    if (sad >= best) break
                    yy += 2
                }
                if (sad < best) { best = sad; bx = dx; by = dy }
            }
            vx += bx.toFloat(); vy += by.toFloat()
            px += (x0 + bs / 2f - w / 2f); py += (y0 + bs / 2f - h / 2f)
        }
        if (vx.size < 3) return floatArrayOf(0f, 0f, 0f)
        val mx = vx.sorted()[vx.size / 2]; val my = vy.sorted()[vy.size / 2]
        // Rotation from how the vectors turn around the centre.
        val angles = ArrayList<Float>()
        for (i in vx.indices) {
            val rx = px[i]; val ry = py[i]
            val d2 = rx * rx + ry * ry
            if (d2 < 400f) continue
            val ux = vx[i] - mx; val uy = vy[i] - my
            angles += Math.toDegrees(((rx * uy - ry * ux) / d2).toDouble()).toFloat()
        }
        val ang = if (angles.size >= 3) angles.sorted()[angles.size / 2] else 0f
        return floatArrayOf(mx, my, ang)
    }
}
