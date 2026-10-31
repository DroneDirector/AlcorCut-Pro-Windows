package com.dronedirector.veyrocutpro.editor

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Build
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * AI depth for a clip (Color → Detail → AI DEPTH): how far away
 * every part of the picture is, worked out by Depth Anything V2 Small (Apache 2.0, on the phone),
 * then used for a lens blur (depth of field), haze / fog, or a darker / calmer background.
 * [mode] 0 = depth of field, 1 = fog, 2 = background light. [focus] = depth kept sharp (1 = nearest),
 * [range] = how deep the sharp zone is, [amount] = strength. Depths: 0 = far … 1 = near.
 */
data class DepthFx(
    val id: String = java.util.UUID.randomUUID().toString(),
    val frames: Int = 0,
    val mode: Int = 0,
    val focus: Float = 0.85f,
    val range: Float = 0.15f,
    val amount: Float = 0.6f,
    val fogColor: Int = 0xFFC9D3DD.toInt(),
    val bgExposure: Float = -0.6f,
    val bgSaturation: Float = -0.3f,
    /** Show the depth map itself in the preview (to set the focus). */
    val showMap: Boolean? = null,
)

/** Depth Anything V2 Small on ONNX Runtime. */
object DepthModel {
    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    @Volatile var failed: String? = null; private set

    @Synchronized fun ready(context: Context): Boolean {
        if (session != null) return true
        if (!ModelStore.has(context, AiModel.DEPTH)) return false
        return try {
            val e = OrtEnvironment.getEnvironment()
            val o = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 6))
                try { addXnnpack(mapOf("intra_op_num_threads" to Runtime.getRuntime().availableProcessors().coerceIn(2, 6).toString())) } catch (_: Throwable) {}
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            session = e.createSession(ModelStore.file(context, AiModel.DEPTH.files[0]).path, o)
            env = e
            true
        } catch (t: Throwable) { failed = t.message ?: t.javaClass.simpleName; false }
    }

    /** Relative depth of [bmp] (larger = nearer), at the model's working size. Slow: off the main thread. */
    fun infer(context: Context, bmp: Bitmap, longSide: Int = 518): Triple<FloatArray, Int, Int>? {
        if (!ready(context)) return null
        val k = longSide.toFloat() / max(bmp.width, bmp.height)
        fun m14(v: Float) = ((v / 14f).roundToInt() * 14).coerceAtLeast(14)
        val w = m14(bmp.width * k); val h = m14(bmp.height * k)
        val s = Bitmap.createScaledBitmap(bmp, w, h, true)
        val px = IntArray(w * h); s.getPixels(px, 0, w, 0, 0, w, h)
        val n = w * h
        val chw = FloatArray(3 * n)
        for (i in 0 until n) {
            val p = px[i]
            chw[i] = (((p shr 16) and 255) / 255f - MEAN[0]) / STD[0]
            chw[n + i] = (((p shr 8) and 255) / 255f - MEAN[1]) / STD[1]
            chw[2 * n + i] = ((p and 255) / 255f - MEAN[2]) / STD[2]
        }
        val e = env ?: return null
        val sess = session ?: return null
        return try {
            OnnxTensor.createTensor(e, FloatBuffer.wrap(chw), longArrayOf(1, 3, h.toLong(), w.toLong())).use { t ->
                sess.run(mapOf(sess.inputNames.first() to t)).use { r ->
                    val out = r[0] as OnnxTensor
                    val shape = out.info.shape
                    val oh = shape[shape.size - 2].toInt(); val ow = shape[shape.size - 1].toInt()
                    val fb = out.floatBuffer
                    val d = FloatArray(fb.remaining()); fb.get(d)
                    Triple(d, ow, oh)
                }
            }
        } catch (t: Throwable) { failed = t.message; null }
    }
}

/** One depth map: 8-bit (255 = near), w×h. */
class DepthEntry(val w: Int, val h: Int, val bytes: ByteArray) {
    fun bitmap(): Bitmap {
        val px = IntArray(bytes.size) { val g = bytes[it].toInt() and 255; (0xFF shl 24) or (g shl 16) or (g shl 8) or g }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}

/** A clip's depth through time (source ms), saved in the app's files. */
class DepthTrack(val times: LongArray, val entries: Array<DepthEntry>) {
    fun at(srcMs: Long): DepthEntry {
        var lo = 0; var hi = times.lastIndex
        if (srcMs <= times[0]) return entries[0]
        if (srcMs >= times[hi]) return entries[hi]
        while (hi - lo > 1) { val m = (lo + hi) / 2; if (times[m] <= srcMs) lo = m else hi = m }
        // Between two analysed frames: blend them.
        val a = entries[lo]; val b = entries[hi]
        if (a.w != b.w || a.h != b.h) return a
        val u = (srcMs - times[lo]).toFloat() / (times[hi] - times[lo]).coerceAtLeast(1)
        if (u < 0.02f) return a
        if (u > 0.98f) return b
        val o = ByteArray(a.bytes.size) { i -> ((a.bytes[i].toInt() and 255) * (1 - u) + (b.bytes[i].toInt() and 255) * u).roundToInt().toByte() }
        return DepthEntry(a.w, a.h, o)
    }

    companion object {
        private val cache = HashMap<String, DepthTrack>()
        private fun file(context: Context, id: String) = File(File(context.filesDir, "depth").apply { mkdirs() }, "$id.bin")

        fun load(context: Context, id: String?): DepthTrack? {
            id ?: return null
            synchronized(cache) { cache[id]?.let { return it } }
            val f = file(context, id)
            if (!f.exists()) return null
            return try {
                DataInputStream(f.inputStream().buffered()).use { ins ->
                    if (ins.readInt() != 0x44455031) return null
                    val n = ins.readInt()
                    val t = LongArray(n); val e = arrayOfNulls<DepthEntry>(n)
                    for (i in 0 until n) {
                        t[i] = ins.readLong(); val w = ins.readInt(); val h = ins.readInt()
                        val b = ByteArray(w * h); ins.readFully(b); e[i] = DepthEntry(w, h, b)
                    }
                    @Suppress("UNCHECKED_CAST")
                    DepthTrack(t, e as Array<DepthEntry>).also { synchronized(cache) { cache[id] = it } }
                }
            } catch (_: Throwable) { null }
        }

        private fun save(context: Context, id: String, t: List<Pair<Long, DepthEntry>>) {
            DataOutputStream(file(context, id).outputStream().buffered()).use { o ->
                o.writeInt(0x44455031); o.writeInt(t.size)
                for ((ms, e) in t) { o.writeLong(ms); o.writeInt(e.w); o.writeInt(e.h); o.write(e.bytes) }
            }
            synchronized(cache) { cache.remove(id) }
        }

        /**
         * Works out the depth of [c] from [fromMs] to [toMs] (source time) about 15 times a second and
         * saves it under [id]. Steady over time: the near / far range glides instead of jumping each
         * frame, and changes only at a scene cut. Returns the number of maps, or 0.
         */
        fun make(context: Context, c: Clip, id: String, onProgress: (Int) -> Unit, stop: () -> Boolean): Int {
            val out = ArrayList<Pair<Long, DepthEntry>>()
            var lo = Float.NaN; var hi = Float.NaN
            var prevSmall: FloatArray? = null
            var prevMap: FloatArray? = null
            val total = (c.endMs - c.startMs).coerceAtLeast(1)
            FrameReader.read(context, c.uri, c.startMs, c.endMs, 640, 66) { t, bmp ->
                if (stop()) return@read false
                val r = DepthModel.infer(context, bmp) ?: return@read false
                val (d, w, h) = r
                // A scene cut: start the smoothing afresh.
                val small = Bitmap.createScaledBitmap(bmp, 32, 18, true).let { s -> val p = IntArray(576); s.getPixels(p, 0, 32, 0, 0, 32, 18); FloatArray(576) { ((p[it] shr 8) and 255) / 255f } }
                val cut = prevSmall?.let { ps -> var s = 0f; for (i in ps.indices) s += abs(ps[i] - small[i]); s / ps.size > 0.25f } ?: true
                prevSmall = small
                val sorted = d.copyOf().also { it.sort() }
                val p2 = sorted[(sorted.size * 0.02).toInt()]; val p98 = sorted[(sorted.size * 0.98).toInt().coerceAtMost(sorted.lastIndex)]
                if (cut || lo.isNaN()) { lo = p2; hi = p98 } else { lo += (p2 - lo) * 0.25f; hi += (p98 - hi) * 0.25f }
                val span = (hi - lo).coerceAtLeast(1e-4f)
                val norm = FloatArray(d.size) { ((d[it] - lo) / span).coerceIn(0f, 1f) }
                val pm = prevMap
                if (!cut && pm != null && pm.size == norm.size) for (i in norm.indices) norm[i] = norm[i] * 0.7f + pm[i] * 0.3f
                prevMap = norm
                // Store at most 320 px on the long side.
                val k = minOf(1f, 320f / max(w, h))
                val sw = (w * k).roundToInt().coerceAtLeast(2); val sh = (h * k).roundToInt().coerceAtLeast(2)
                val bytes = ByteArray(sw * sh)
                for (y in 0 until sh) for (x in 0 until sw) {
                    val sx = (x + 0.5f) / sw * w - 0.5f; val sy = (y + 0.5f) / sh * h - 0.5f
                    val x0 = sx.toInt().coerceIn(0, w - 1); val y0 = sy.toInt().coerceIn(0, h - 1)
                    val x1 = (x0 + 1).coerceAtMost(w - 1); val y1 = (y0 + 1).coerceAtMost(h - 1)
                    val fx = (sx - x0).coerceIn(0f, 1f); val fy = (sy - y0).coerceIn(0f, 1f)
                    val v = norm[y0 * w + x0] * (1 - fx) * (1 - fy) + norm[y0 * w + x1] * fx * (1 - fy) + norm[y1 * w + x0] * (1 - fx) * fy + norm[y1 * w + x1] * fx * fy
                    bytes[y * sw + x] = (v * 255f).roundToInt().coerceIn(0, 255).toByte()
                }
                out += t to DepthEntry(sw, sh, bytes)
                onProgress(((t - c.startMs) * 100 / total).toInt().coerceIn(0, 99))
                true
            }
            if (stop() || out.isEmpty()) return 0
            save(context, id, out)
            onProgress(100)
            return out.size
        }
    }
}

/** GLSL / AGSL shared depth effect maths. */
private object DepthGlsl {
    /** Uniforms: uMode, uFocus, uRange, uAmount, uFog (rgb), uBgExp, uBgSat, uShow. */
    const val PARAMS = """
        float cocOf(float d) { return smoothstep(uRange * 0.5, uRange * 0.5 + 0.18, abs(d - uFocus)); }
        float bgMask(float d) { return smoothstep(uRange * 0.5, uRange * 0.5 + 0.2, uFocus - d); }
    """
}

/** Export: the depth effect on the clip's picture. [offsetMs] = where this part of the clip starts (timeline, from the clip start). */
@OptIn(UnstableApi::class)
class DepthEffect(private val c: Clip, private val fx: DepthFx, private val track: DepthTrack, private val offsetMs: Long) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = DepthProgram(c, fx, track, offsetMs, useHdr)
}

@OptIn(UnstableApi::class)
fun depthEffectFor(context: Context, c: Clip, offsetMs: Long): DepthEffect? {
    val fx = c.depth ?: return null
    if (fx.frames <= 0) return null
    val t = DepthTrack.load(context, fx.id) ?: return null
    return DepthEffect(c, fx, t, offsetMs)
}

@OptIn(UnstableApi::class)
private class DepthProgram(private val c: Clip, private val fx: DepthFx, private val track: DepthTrack, private val offsetMs: Long, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program = try { GlProgram(VERT, FRAG) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private var tex = 0
    private var t0 = Long.MIN_VALUE
    private var last: DepthEntry? = null
    private var w = 1; private var h = 1
    init { program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE) }
    override fun configure(inputWidth: Int, inputHeight: Int): Size { w = inputWidth; h = inputHeight; return Size(inputWidth, inputHeight) }
    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        if (t0 == Long.MIN_VALUE) t0 = presentationTimeUs
        val src = c.srcAtOffset(offsetMs + (presentationTimeUs - t0) / 1000)
        try {
            val e = track.at(src)
            if (tex == 0) { val t = IntArray(1); GLES20.glGenTextures(1, t, 0); tex = t[0] }
            if (e !== last) {
                val b = e.bitmap()
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
                b.recycle(); last = e
            }
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setSamplerTexIdUniform("uDepth", tex, 1)
            program.setFloatsUniform("uSize", floatArrayOf(w.toFloat(), h.toFloat()))
            program.setFloatsUniform("uDSize", floatArrayOf(e.w.toFloat(), e.h.toFloat()))
            program.setFloatUniform("uMode", fx.mode.toFloat())
            program.setFloatUniform("uFocus", fx.focus); program.setFloatUniform("uRange", fx.range); program.setFloatUniform("uAmount", fx.amount)
            program.setFloatsUniform("uFog", floatArrayOf(((fx.fogColor shr 16) and 255) / 255f, ((fx.fogColor shr 8) and 255) / 255f, (fx.fogColor and 255) / 255f))
            program.setFloatUniform("uBgExp", fx.bgExposure); program.setFloatUniform("uBgSat", fx.bgSaturation)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }
    override fun release() {
        super.release()
        if (tex != 0) GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
        try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }
    companion object {
        const val VERT = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() { gl_Position = aFramePosition; vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5); }
        """
        const val FRAG = """
            precision highp float;
            uniform sampler2D uTexSampler;
            uniform sampler2D uDepth;
            uniform vec2 uSize; uniform vec2 uDSize;
            uniform float uMode; uniform float uFocus; uniform float uRange; uniform float uAmount;
            uniform vec3 uFog; uniform float uBgExp; uniform float uBgSat;
            varying vec2 vTexSamplingCoord;
        """ + DepthGlsl.PARAMS + """
            // Depth (picture coordinates, y down), sharpened against the picture's own edges.
            float depthAt(vec2 uv, vec3 col) {
                vec2 st = 1.0 / uDSize;
                float s = 0.0; float ws = 0.0;
                for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
                    vec2 o = vec2(float(i), float(j)) * st;
                    float d = texture2D(uDepth, uv + o).r;
                    vec3 c = texture2D(uTexSampler, vec2(uv.x + o.x, 1.0 - (uv.y + o.y))).rgb;
                    vec3 dc = c - col;
                    float w = exp(-dot(dc, dc) * 40.0) * (1.0 - 0.15 * abs(float(i * j)));
                    s += d * w; ws += w;
                }
                return s / max(ws, 1e-4);
            }
            void main() {
                vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
                vec2 uv = vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y);
                float d = depthAt(uv, src.rgb);
                vec3 c = src.rgb;
                if (uMode < 0.5) {
                    // Lens blur: a disc whose size follows the distance from the focus.
                    float coc = cocOf(d);
                    float r = coc * uAmount * 0.03 * uSize.y;
                    if (r > 0.6) {
                        vec3 acc = vec3(0.0); float wsum = 0.0;
                        for (int k = 0; k < 40; k++) {
                            float fk = float(k);
                            float rr = sqrt((fk + 0.5) / 40.0) * r;
                            float a = fk * 2.39996;
                            vec2 o = vec2(cos(a), sin(a)) * rr / uSize;
                            vec2 suv = uv + o;
                            float ds = texture2D(uDepth, suv).r;
                            // A sharp thing in front doesn't bleed into the blurred background behind it.
                            float w = (ds > d + 0.06) ? cocOf(ds) : 1.0;
                            acc += texture2D(uTexSampler, vec2(suv.x, 1.0 - suv.y)).rgb * w; wsum += w;
                        }
                        c = acc / max(wsum, 1e-4);
                    }
                } else if (uMode < 1.5) {
                    float f = uAmount * pow(1.0 - d, 1.6);
                    c = mix(c, uFog, clamp(f, 0.0, 1.0));
                } else {
                    float m = bgMask(d) * uAmount;
                    vec3 g = c * exp2(uBgExp);
                    float l = dot(g, vec3(0.2126, 0.7152, 0.0722));
                    g = mix(vec3(l), g, 1.0 + uBgSat);
                    c = mix(c, clamp(g, 0.0, 1.0), m);
                }
                gl_FragColor = vec4(c, src.a);
            }
        """
    }
}

/** Live preview of the depth effect on the player (Android 13+). */
object DepthPreview {
    private const val AGSL = """
        uniform shader content;
        uniform shader depth;
        uniform float2 uSize; uniform float2 uDSize;
        uniform float uMode; uniform float uFocus; uniform float uRange; uniform float uAmount;
        uniform float3 uFog; uniform float uBgExp; uniform float uBgSat; uniform float uShow;
        float cocOf(float d) { return smoothstep(uRange * 0.5, uRange * 0.5 + 0.18, abs(d - uFocus)); }
        float bgMask(float d) { return smoothstep(uRange * 0.5, uRange * 0.5 + 0.2, uFocus - d); }
        float dAt(float2 p) { return float(depth.eval(p / uSize * uDSize).r); }
        half4 main(float2 p) {
            half4 src = content.eval(p);
            float d = dAt(p);
            if (uShow > 0.5) return half4(half3(d), 1.0);
            float3 c = float3(src.rgb);
            if (uMode < 0.5) {
                float r = cocOf(d) * uAmount * 0.03 * uSize.y;
                if (r > 0.6) {
                    float3 acc = float3(0.0); float ws = 0.0;
                    for (int k = 0; k < 24; k++) {
                        float fk = float(k);
                        float rr = sqrt((fk + 0.5) / 24.0) * r;
                        float a = fk * 2.39996;
                        float2 q = p + float2(cos(a), sin(a)) * rr;
                        float ds = dAt(q);
                        float w = (ds > d + 0.06) ? cocOf(ds) : 1.0;
                        acc += float3(content.eval(q).rgb) * w; ws += w;
                    }
                    c = acc / max(ws, 0.0001);
                }
            } else if (uMode < 1.5) {
                c = mix(c, uFog, clamp(uAmount * pow(1.0 - d, 1.6), 0.0, 1.0));
            } else {
                float m = bgMask(d) * uAmount;
                float3 g = c * exp2(uBgExp);
                float l = dot(g, float3(0.2126, 0.7152, 0.0722));
                g = mix(float3(l), g, 1.0 + uBgSat);
                c = mix(c, clamp(g, 0.0, 1.0), m);
            }
            return half4(half3(c), src.a);
        }
    """

    private val bitmaps = java.util.WeakHashMap<DepthEntry, Bitmap>()

    fun effect(context: Context, v: View, c: Clip, srcMs: Long): RenderEffect? {
        if (Build.VERSION.SDK_INT < 33 || v.width <= 0 || v.height <= 0) return null
        val fx = c.depth ?: return null
        if (fx.frames <= 0) return null
        val t = DepthTrack.load(context, fx.id) ?: return null
        val e = t.at(srcMs)
        val b = synchronized(bitmaps) { bitmaps.getOrPut(e) { e.bitmap() } }
        val sh = try { RuntimeShader(AGSL) } catch (_: Throwable) { return null }
        sh.setInputShader("depth", BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR })
        sh.setFloatUniform("uSize", v.width.toFloat(), v.height.toFloat())
        sh.setFloatUniform("uDSize", e.w.toFloat(), e.h.toFloat())
        sh.setFloatUniform("uMode", fx.mode.toFloat())
        sh.setFloatUniform("uFocus", fx.focus); sh.setFloatUniform("uRange", fx.range); sh.setFloatUniform("uAmount", fx.amount)
        sh.setFloatUniform("uFog", ((fx.fogColor shr 16) and 255) / 255f, ((fx.fogColor shr 8) and 255) / 255f, (fx.fogColor and 255) / 255f)
        sh.setFloatUniform("uBgExp", fx.bgExposure); sh.setFloatUniform("uBgSat", fx.bgSaturation)
        sh.setFloatUniform("uShow", if (fx.showMap == true) 1f else 0f)
        return RenderEffect.createRuntimeShaderEffect(sh, "content")
    }
}
