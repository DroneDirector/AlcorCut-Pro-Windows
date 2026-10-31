package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.dronedirector.veyrocutpro.camera.GradeIO
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Morph cut (Transitions → Basic → Morph cut),
 * Smooth Cut: for a jump cut (words cut out of an interview), the last frame before the transition
 * and the first frame after it are analysed with AlcorCut Pro's own optical flow, and every frame in
 * between is drawn new — each pixel moves along its own path from one picture to the other, so a
 * head glides to its new position instead of jumping or cross-fading.
 */
object MorphNative {
    val available: Boolean by lazy { try { System.loadLibrary("veyrocut_morph"); true } catch (_: Throwable) { false } }
    @JvmStatic external fun analyze(a: Bitmap, b: Bitmap): Long
    @JvmStatic external fun render(h: Long, t: Float, out: Bitmap): Boolean
    @JvmStatic external fun motion(h: Long, t: Float, buf: ByteBuffer): Boolean
    @JvmStatic external fun occlusion(h: Long, buf: ByteBuffer): Boolean
    @JvmStatic external fun flow(a: Bitmap, b: Bitmap, out: FloatArray): Boolean
    @JvmStatic external fun width(h: Long): Int
    @JvmStatic external fun height(h: Long): Int
    @JvmStatic external fun free(h: Long)
}

/** Size the motion is worked out at (long side, pixels): fine detail is taken from the full frames. */
private const val WORK_SIDE = 768

private fun scaledTo(b: Bitmap, side: Int): Bitmap {
    val k = side.toFloat() / max(b.width, b.height)
    if (k >= 1f) return if (b.config == Bitmap.Config.ARGB_8888) b else b.copy(Bitmap.Config.ARGB_8888, false)
    return Bitmap.createScaledBitmap(b, (b.width * k).roundToInt().coerceAtLeast(16), (b.height * k).roundToInt().coerceAtLeast(16), true)
}

/** One analysed morph cut: the native analysis plus the two held frames at full size (export). */
class MorphData(val handle: Long, val a: Bitmap, val b: Bitmap) {
    val workW = MorphNative.width(handle)
    val workH = MorphNative.height(handle)
    @Volatile private var freed = false
    fun release() { if (!freed) { freed = true; MorphNative.free(handle) } }

    companion object {
        /** Analyses [a] → [b] (same size). Slow (seconds): call off the main thread. */
        fun analyze(a: Bitmap, b: Bitmap, side: Int = WORK_SIDE): MorphData? {
            if (!MorphNative.available) return null
            val wa = scaledTo(a, side)
            val wb = if (b.width == a.width && b.height == a.height) scaledTo(b, side) else Bitmap.createScaledBitmap(b, wa.width, wa.height, true)
            val h = try { MorphNative.analyze(wa, wb) } catch (_: Throwable) { 0L }
            return if (h == 0L) null else MorphData(h, a, b)
        }
    }
}

/** The morph cuts in an edit: the cut after clip i, when it is a morph cut with an overlap. */
fun morphCuts(clips: List<Clip>): List<Int> =
    clips.indices.filter { it + 1 < clips.size && clips[it].transition.type == TransitionType.SMOOTH_CUT && overlapMs(clips, it) > 0 }

// ---------------------------------------------------------------- export: held frames through the clip's own effects

/** Reads the first frame that reaches it back into a bitmap (and passes every frame through). */
@UnstableApi
private class FrameGrabEffect(private val onFrame: (Bitmap) -> Unit) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = FrameGrabProgram(onFrame, useHdr)
}

@UnstableApi
private class FrameGrabProgram(private val onFrame: (Bitmap) -> Unit, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program = try { GlProgram(VERT, COPY) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private var w = 1; private var h = 1
    private var done = false
    init { program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE) }
    override fun configure(inputWidth: Int, inputHeight: Int): Size { w = inputWidth; h = inputHeight; return Size(w, h) }
    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTex", inputTexId, 0)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            if (!done) {
                done = true
                val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
                GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
                buf.rewind()
                val raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                raw.copyPixelsFromBuffer(buf)
                // GL rows run bottom-up.
                val bmp = Bitmap.createBitmap(raw, 0, 0, w, h, Matrix().apply { preScale(1f, -1f) }, false)
                raw.recycle()
                onFrame(bmp)
            }
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }
    override fun release() { super.release(); try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) } }
}

/**
 * Before an export: for every morph cut, the frame where the transition starts (outgoing clip) and
 * the frame where it ends (incoming clip) are rendered through each clip's own effects (grade,
 * reframe, film look…) at the export size, then analysed. Calls [onDone] on the main thread with
 * the analysed cuts (cut index → data); cuts that fail fall back to a smooth dissolve.
 */
@UnstableApi
class MorphPrep(
    private val context: Context,
    private val clips: List<Clip>,
    /** The clip's effects up to (not including) its transition, for a clip starting [offsetMs] into it. */
    private val effectsFor: (Clip, Long) -> List<Effect>,
    private val decoderFactory: ((Transformer.Builder) -> Unit)?,
    private val onStatus: (String) -> Unit,
    private val onDone: (Map<Int, MorphData>) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val result = HashMap<Int, MorphData>()
    @Volatile private var cancelled = false
    private var current: Transformer? = null

    fun cancel() { cancelled = true; try { current?.cancel() } catch (_: Throwable) {}; result.values.forEach { it.release() } }

    fun start() {
        val cuts = morphCuts(clips)
        if (cuts.isEmpty() || !MorphNative.available) { onDone(emptyMap()); return }
        step(cuts, 0)
    }

    private fun step(cuts: List<Int>, k: Int) {
        if (cancelled) return
        if (k >= cuts.size) { onDone(result); return }
        val i = cuts[k]
        val a = clips[i]; val b = clips[i + 1]
        val ov = overlapMs(clips, i)
        onStatus("Analysing morph cut ${k + 1} of ${cuts.size}…")
        val aOff = (a.lengthMs - ov).coerceAtLeast(0)
        val bOff = ov.coerceAtMost(b.lengthMs - 1)
        grab(a, aOff) { fa ->
            if (fa == null) { step(cuts, k + 1); return@grab }
            grab(b, bOff) { fb ->
                if (fb == null) { step(cuts, k + 1); return@grab }
                Thread {
                    val d = try { MorphData.analyze(fa, fb) } catch (_: Throwable) { null }
                    handler.post {
                        if (cancelled) { d?.release(); return@post }
                        if (d != null) result[i] = d
                        step(cuts, k + 1)
                    }
                }.start()
            }
        }
    }

    /** Renders the frame [offsetMs] (timeline time) into clip [c] through its effects. */
    private fun grab(c: Clip, offsetMs: Long, cb: (Bitmap?) -> Unit) {
        val src = c.srcAtOffset(offsetMs).coerceIn(c.startMs, (c.endMs - 1).coerceAtLeast(c.startMs))
        val end = (src + 250).coerceAtMost(maxOf(c.endMs, src + 1))
        val tmp = File(context.cacheDir, "morph_${System.nanoTime()}.mp4")
        var frame: Bitmap? = null
        var answered = false
        fun answer(b: Bitmap?) { if (answered) return; answered = true; tmp.delete(); handler.post { cb(b) } }
        try {
            val item = EditedMediaItem.Builder(
                MediaItem.Builder().setUri(c.uri)
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(src).setEndPositionMs(end).build()).build()
            )
                .setRemoveAudio(true)
                .setEffects(Effects(emptyList(), effectsFor(c, offsetMs) + FrameGrabEffect { frame = it }))
                .build()
            val tb = Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264)
            decoderFactory?.invoke(tb)
            val tr = tb.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) { answer(frame) }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { answer(frame) }
            }).build()
            current = tr
            tr.start(Composition.Builder(EditedMediaItemSequence.Builder(listOf(item)).build()).build(), tmp.path)
        } catch (_: Throwable) { answer(null) }
    }
}

// ---------------------------------------------------------------- export: drawing the in-between frames

/** On a clip of [lengthMs]: the morph coming in over its first [inMs] and going out over its last [outMs]. */
@UnstableApi
class MorphEffect(
    private val lengthMs: Long,
    private val inData: MorphData?, private val inMs: Long,
    private val outData: MorphData?, private val outMs: Long,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = MorphProgram(lengthMs, inData, inMs, outData, outMs, useHdr)
    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = inData == null && outData == null
}

@UnstableApi
private class MorphProgram(
    private val lengthMs: Long,
    private val inData: MorphData?, private val inMs: Long,
    private val outData: MorphData?, private val outMs: Long,
    useHdr: Boolean,
) : BaseGlShaderProgram(useHdr, 1) {
    private val program = try { GlProgram(VERT, FRAG) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private var t0 = Long.MIN_VALUE
    private var w = 1; private var h = 1

    /** GPU copies of one cut's held frames, occlusion and the motion texture (filled per frame). */
    private inner class Tex(val d: MorphData) {
        val a = upload(d.a); val b = upload(d.b)
        val occ: Int; val mot: Int
        val motBuf: ByteBuffer = ByteBuffer.allocateDirect(d.workW * d.workH * 4).order(ByteOrder.nativeOrder())
        init {
            val ob = ByteBuffer.allocateDirect(d.workW * d.workH * 4).order(ByteOrder.nativeOrder())
            MorphNative.occlusion(d.handle, ob); ob.rewind()
            occ = rawTex(d.workW, d.workH, ob, GLES20.GL_LINEAR)
            mot = rawTex(d.workW, d.workH, null, GLES20.GL_NEAREST)
        }
        fun motionAt(t: Float) {
            motBuf.clear()
            MorphNative.motion(d.handle, t, motBuf); motBuf.rewind()
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mot)
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, d.workW, d.workH, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, motBuf)
        }
        fun delete() { for (t in intArrayOf(a, b, occ, mot)) try { GlUtil.deleteTexture(t) } catch (_: Throwable) {} }
    }
    private var inTex: Tex? = null
    private var outTex: Tex? = null

    init { program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE) }

    override fun configure(inputWidth: Int, inputHeight: Int): Size { w = inputWidth; h = inputHeight; return Size(w, h) }

    private fun rawTex(tw: Int, th: Int, data: ByteBuffer?, filter: Int): Int {
        val t = IntArray(1); GLES20.glGenTextures(1, t, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0])
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, tw, th, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, data)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return t[0]
    }

    private fun upload(b: Bitmap): Int {
        val t = IntArray(1); GLES20.glGenTextures(1, t, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0])
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return t[0]
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        if (t0 == Long.MIN_VALUE) t0 = presentationTimeUs
        val local = (presentationTimeUs - t0) / 1000L
        var tex: Tex? = null; var t = 0f
        try {
            if (inData != null && inMs > 0 && local < inMs) {
                tex = inTex ?: Tex(inData).also { inTex = it }; t = (local.toFloat() / inMs).coerceIn(0f, 1f)
            } else if (outData != null && outMs > 0 && local > lengthMs - outMs) {
                tex = outTex ?: Tex(outData).also { outTex = it }; t = (1f - (lengthMs - local).toFloat() / outMs).coerceIn(0f, 1f)
            }
            tex?.motionAt(t)
            program.use()
            program.setSamplerTexIdUniform("uIn", inputTexId, 0)
            if (tex != null) {
                program.setSamplerTexIdUniform("uA", tex.a, 1)
                program.setSamplerTexIdUniform("uB", tex.b, 2)
                program.setSamplerTexIdUniform("uMot", tex.mot, 3)
                program.setSamplerTexIdUniform("uOcc", tex.occ, 4)
                program.setFloatsUniform("uWork", floatArrayOf(tex.d.workW.toFloat(), tex.d.workH.toFloat()))
            } else {
                for ((n, u) in listOf("uA", "uB", "uMot", "uOcc").withIndex()) program.setSamplerTexIdUniform(u, inputTexId, n + 1)
                program.setFloatsUniform("uWork", floatArrayOf(1f, 1f))
            }
            program.setFloatUniform("uT", t)
            program.setFloatUniform("uOn", if (tex != null) 1f else 0f)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    override fun release() {
        super.release()
        inTex?.delete(); outTex?.delete()
        try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }
}

private const val VERT = """
    attribute vec4 aFramePosition;
    varying vec2 vUv;
    void main() { gl_Position = aFramePosition; vUv = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5); }
"""

private const val COPY = """
    precision mediump float;
    uniform sampler2D uTex;
    varying vec2 vUv;
    void main() { gl_FragColor = texture2D(uTex, vUv); }
"""

/** In-between frame: each pixel from A and B along the motion through it; covered / uncovered parts from the one frame that sees them. */
private const val FRAG = """
    #ifdef GL_FRAGMENT_PRECISION_HIGH
    precision highp float;
    #else
    precision mediump float;
    #endif
    uniform sampler2D uIn;
    uniform sampler2D uA;
    uniform sampler2D uB;
    uniform sampler2D uMot;
    uniform sampler2D uOcc;
    uniform vec2 uWork;
    uniform float uT;
    uniform float uOn;
    varying vec2 vUv;
    vec2 dec(vec4 c) {
        vec4 b = floor(c * 255.0 + 0.5);
        return vec2((b.r * 256.0 + b.g - 32768.0) / 32.0, (b.b * 256.0 + b.a - 32768.0) / 32.0);
    }
    vec2 mot(vec2 p) {
        vec2 q = p - 0.5;
        vec2 f = fract(q);
        vec2 i0 = clamp(floor(q), vec2(0.0), uWork - 1.0);
        vec2 i1 = clamp(floor(q) + 1.0, vec2(0.0), uWork - 1.0);
        vec2 a = dec(texture2D(uMot, (vec2(i0.x, i0.y) + 0.5) / uWork));
        vec2 b = dec(texture2D(uMot, (vec2(i1.x, i0.y) + 0.5) / uWork));
        vec2 c = dec(texture2D(uMot, (vec2(i0.x, i1.y) + 0.5) / uWork));
        vec2 d = dec(texture2D(uMot, (vec2(i1.x, i1.y) + 0.5) / uWork));
        return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
    }
    float inside(vec2 p) { return step(0.0, p.x) * step(p.x, 1.0) * step(0.0, p.y) * step(p.y, 1.0); }
    void main() {
        if (uOn < 0.5) { gl_FragColor = texture2D(uIn, vUv); return; }
        // Picture coordinates (y down), like the analysed frames.
        vec2 iuv = vec2(vUv.x, 1.0 - vUv.y);
        vec2 m = mot(iuv * uWork) / uWork;
        vec2 pa = iuv - uT * m;
        vec2 pb = iuv + (1.0 - uT) * m;
        vec4 ca = texture2D(uA, clamp(pa, 0.0, 1.0));
        vec4 cb = texture2D(uB, clamp(pb, 0.0, 1.0));
        float oa = texture2D(uOcc, clamp(pa, 0.0, 1.0)).r;
        float ob = texture2D(uOcc, clamp(pb, 0.0, 1.0)).g;
        float wa = (1.0 - uT) * (1.0 - ob) * inside(pa);
        float wb = uT * (1.0 - oa) * inside(pb);
        if (wa + wb < 0.0001) { wa = 1.0 - uT; wb = uT; }
        gl_FragColor = vec4((ca.rgb * wa + cb.rgb * wb) / (wa + wb), 1.0);
    }
"""

// ---------------------------------------------------------------- preview

/** The in-between frames of one morph cut for the editor's preview (small, drawn ahead of time). */
class MorphPreviewFrames(val key: String, val frames: List<Bitmap>)

object MorphPreview {
    /** What the preview of the cut after [a] depends on. */
    fun keyOf(a: Clip, b: Clip, ov: Long): String =
        listOf(a.uri, a.endMs, a.startMs, a.speed, a.ramp, a.reframe, a.grade, a.detail, a.film, b.uri, b.startMs, b.endMs, b.speed, b.ramp, b.reframe, b.grade, b.detail, b.film, ov).hashCode().toString()

    /** The frame [offsetMs] (timeline) into [c] at about [side] px, with its framing and grade. */
    private fun frameOf(context: Context, c: Clip, offsetMs: Long, side: Int, motion: Motion?): Bitmap? {
        val src = c.srcAtOffset(offsetMs).coerceIn(c.startMs, (c.endMs - 1).coerceAtLeast(c.startMs))
        val raw = GradeIO.videoFrame(context, c.uri, src * 1000, side) ?: return null
        var f = if (raw.config == Bitmap.Config.ARGB_8888) raw else raw.copy(Bitmap.Config.ARGB_8888, true)
        // Framing (crop / zoom / keyframes / stabilisation), as the player view shows it.
        val x = ClipXform.at(c, src, motion)
        if (x.scale != 1f || x.rotation != 0f || x.tx != 0f || x.ty != 0f || x.flip) {
            val out = Bitmap.createBitmap(f.width, f.height, Bitmap.Config.ARGB_8888)
            val m = Matrix()
            val cx = f.width / 2f; val cy = f.height / 2f
            m.postScale(x.scale * (if (x.flip) -1f else 1f), x.scale, cx, cy)
            m.postRotate(x.rotation, cx, cy)
            m.postTranslate(x.tx * f.width, x.ty * f.height)
            Canvas(out).apply { drawColor(0xFF000000.toInt()); drawBitmap(f, m, Paint(Paint.FILTER_BITMAP_FLAG)) }
            f = out
        }
        if (!c.grade.isIdentity || !c.detail.isZero) f = ImageOps.render(f, c.grade, c.detail.copy(aiDenoise = 0f), Geometry(), if (c.grade.isIdentity) null else c.grade.tables())
        return f
    }

    /** Builds the preview frames for the cut after clip [a] (slow: call off the main thread). */
    fun build(context: Context, a: Clip, b: Clip, ov: Long, motionA: Motion?, motionB: Motion?, fps: Int = 30): MorphPreviewFrames? {
        if (!MorphNative.available || ov <= 0) return null
        val fa = frameOf(context, a, (a.lengthMs - ov).coerceAtLeast(0), 640, motionA) ?: return null
        var fb = frameOf(context, b, ov.coerceAtMost(b.lengthMs - 1), 640, motionB) ?: return null
        if (fb.width != fa.width || fb.height != fa.height) fb = Bitmap.createScaledBitmap(fb, fa.width, fa.height, true)
        val d = MorphData.analyze(fa, fb) ?: return null
        try {
            val n = (ov * fps / 1000).toInt().coerceIn(4, 45)
            val frames = (0 until n).map { k ->
                val t = if (n == 1) 0.5f else k.toFloat() / (n - 1)
                Bitmap.createBitmap(d.workW, d.workH, Bitmap.Config.ARGB_8888).also { MorphNative.render(d.handle, t, it) }
            }
            return MorphPreviewFrames(keyOf(a, b, ov), frames)
        } finally { d.release() }
    }
}
