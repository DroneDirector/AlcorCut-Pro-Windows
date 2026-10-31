package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import kotlin.math.abs
import kotlin.math.sqrt

/** One keyframe of a layer: where it is, how big and how visible at timeline time [t] (ms). */
data class LayerKey(val t: Long, val x: Float, val y: Float, val size: Float, val opacity: Float)

/** How a picture layer mixes with the video under it. */
enum class LayerBlend(val label: String) {
    NORMAL("Normal"), SCREEN("Screen"), MULTIPLY("Multiply"), OVERLAY("Overlay"), ADD("Add"),
    LIGHTEN("Lighten"), DARKEN("Darken"), SOFT_LIGHT("Soft light"), DIFFERENCE("Difference");

    val compose: androidx.compose.ui.graphics.BlendMode get() = when (this) {
        NORMAL -> androidx.compose.ui.graphics.BlendMode.SrcOver
        SCREEN -> androidx.compose.ui.graphics.BlendMode.Screen
        MULTIPLY -> androidx.compose.ui.graphics.BlendMode.Multiply
        OVERLAY -> androidx.compose.ui.graphics.BlendMode.Overlay
        ADD -> androidx.compose.ui.graphics.BlendMode.Plus
        LIGHTEN -> androidx.compose.ui.graphics.BlendMode.Lighten
        DARKEN -> androidx.compose.ui.graphics.BlendMode.Darken
        SOFT_LIGHT -> androidx.compose.ui.graphics.BlendMode.Softlight
        DIFFERENCE -> androidx.compose.ui.graphics.BlendMode.Difference
    }
}

/** The layer as it is at timeline time [ms] (keyframes interpolated with a gentle ease). */
fun Layer.animAt(ms: Long): Layer {
    val k = keys
    if (k.isNullOrEmpty()) return this
    val sorted = k.sortedBy { it.t }
    if (ms <= sorted.first().t) return withKey(sorted.first())
    if (ms >= sorted.last().t) return withKey(sorted.last())
    val i = sorted.indexOfLast { it.t <= ms }
    val a = sorted[i]; val b = sorted[i + 1]
    var p = (ms - a.t).toFloat() / (b.t - a.t).coerceAtLeast(1)
    p = p * p * (3f - 2f * p)
    fun mix(u: Float, v: Float) = u + (v - u) * p
    return copy(x = mix(a.x, b.x), y = mix(a.y, b.y), size = mix(a.size, b.size), opacity = mix(a.opacity, b.opacity))
}

private fun Layer.withKey(k: LayerKey) = copy(x = k.x, y = k.y, size = k.size, opacity = k.opacity)

/** With keyframes, an edit at the playhead records (or updates) the keyframe there. */
fun Layer.editedAt(ms: Long, edited: Layer): Layer {
    val k = keys
    if (k.isNullOrEmpty()) return edited
    val moved = edited.x != x || edited.y != y || edited.size != size || edited.opacity != opacity
    if (!moved) return edited.copy(keys = edited.keys)
    val near = k.firstOrNull { abs(it.t - ms) < 120 }
    val nk = LayerKey(near?.t ?: ms, edited.x, edited.y, edited.size, edited.opacity)
    return edited.copy(keys = (k.filter { it !== near } + nk).sortedBy { it.t })
}

/** Adds / replaces a keyframe at [ms] with the layer's look there. */
fun Layer.keyAt(ms: Long): Layer {
    val cur = animAt(ms)
    val k = (keys ?: emptyList()).filter { abs(it.t - ms) >= 120 }
    return copy(keys = (k + LayerKey(ms, cur.x, cur.y, cur.size, cur.opacity)).sortedBy { it.t })
}

/** Makes [key]-coloured pixels transparent (CPU, for picture layers). */
fun chromaKeyBitmap(src: Bitmap, key: Int, tol: Float, soft: Float): Bitmap {
    val w = src.width; val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    val (kcb, kcr) = cbcr(((key shr 16) and 0xFF) / 255f, ((key shr 8) and 0xFF) / 255f, (key and 0xFF) / 255f)
    val t0 = 0.04f + tol * 0.3f; val t1 = t0 + 0.02f + soft * 0.2f
    for (i in px.indices) {
        val c = px[i]
        val r = ((c shr 16) and 0xFF) / 255f; val g = ((c shr 8) and 0xFF) / 255f; val b = (c and 0xFF) / 255f
        val (cb, cr) = cbcr(r, g, b)
        val d = sqrt((cb - kcb) * (cb - kcb) + (cr - kcr) * (cr - kcr))
        val a = ((d - t0) / (t1 - t0)).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        val oa = ((c ushr 24) and 0xFF) / 255f * a
        px[i] = ((oa * 255f).toInt() shl 24) or (c and 0x00FFFFFF)
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

/** Default luma-key level: cut below 15 % (dark) or above 85 % (bright). */
fun Layer.lumaLevelOrDefault(): Float = lumaLevel ?: if (lumaKey == 2) 0.85f else 0.15f

/** Luma key alpha: [mode] 1 removes dark parts, 2 removes bright parts, with a soft edge. */
fun lumaAlpha(y: Float, mode: Int, level: Float, soft: Float): Float {
    val e = 0.01f + soft * 0.35f
    val t = when (mode) {
        1 -> ((y - level) / e).coerceIn(0f, 1f)
        2 -> ((level - y) / e).coerceIn(0f, 1f)
        else -> return 1f
    }
    return t * t * (3f - 2f * t)
}

/** Makes the dark (mode 1) or bright (mode 2) parts of a picture transparent. */
fun lumaKeyBitmap(src: Bitmap, mode: Int, level: Float, soft: Float): Bitmap {
    val w = src.width; val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    for (i in px.indices) {
        val c = px[i]
        val y = (0.2126f * ((c shr 16) and 0xFF) + 0.7152f * ((c shr 8) and 0xFF) + 0.0722f * (c and 0xFF)) / 255f
        val oa = ((c ushr 24) and 0xFF) / 255f * lumaAlpha(y, mode, level, soft)
        px[i] = ((oa * 255f).toInt() shl 24) or (c and 0x00FFFFFF)
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

private fun cbcr(r: Float, g: Float, b: Float): Pair<Float, Float> =
    (-0.1687f * r - 0.3313f * g + 0.5f * b) to (0.5f * r - 0.4187f * g - 0.0813f * b)

/** Shared chroma-key maths for the GPU shaders (GLSL / AGSL). */
private const val CHROMA_FN = """
    float chromaAlpha(vec3 c, vec3 key, float tol, float soft) {
        vec2 a = vec2(-0.1687 * c.r - 0.3313 * c.g + 0.5 * c.b, 0.5 * c.r - 0.4187 * c.g - 0.0813 * c.b);
        vec2 k = vec2(-0.1687 * key.r - 0.3313 * key.g + 0.5 * key.b, 0.5 * key.r - 0.4187 * key.g - 0.0813 * key.b);
        float t0 = 0.04 + tol * 0.3;
        float t1 = t0 + 0.02 + soft * 0.2;
        return smoothstep(t0, t1, distance(a, k));
    }
    float lumaAlpha(vec3 c, float mode, float level, float soft) {
        float y = dot(c, vec3(0.2126, 0.7152, 0.0722));
        float e = 0.01 + soft * 0.35;
        if (mode < 0.5) return 1.0;
        if (mode < 1.5) return smoothstep(0.0, 1.0, clamp((y - level) / e, 0.0, 1.0));
        return smoothstep(0.0, 1.0, clamp((level - y) / e, 0.0, 1.0));
    }
"""

/** Preview (Android 13+): chroma key on a video layer's view. */
object ChromaPreview {
    private const val SHADER = """
        uniform shader content;
        uniform float3 uKey;
        uniform float uTol;
        uniform float uSoft;
        uniform float uUseKey;
        uniform float uLuma;
        uniform float uLevel;
        uniform float uLSoft;
        half4 main(float2 p) {
            half4 c = content.eval(p);
            float y = 0.2126 * c.r + 0.7152 * c.g + 0.0722 * c.b;
            float e = 0.01 + uLSoft * 0.35;
            float la = 1.0;
            if (uLuma > 0.5 && uLuma < 1.5) la = smoothstep(0.0, 1.0, clamp((y - uLevel) / e, 0.0, 1.0));
            if (uLuma > 1.5) la = smoothstep(0.0, 1.0, clamp((uLevel - y) / e, 0.0, 1.0));
            float2 a = float2(-0.1687 * c.r - 0.3313 * c.g + 0.5 * c.b, 0.5 * c.r - 0.4187 * c.g - 0.0813 * c.b);
            float2 k = float2(-0.1687 * uKey.r - 0.3313 * uKey.g + 0.5 * uKey.b, 0.5 * uKey.r - 0.4187 * uKey.g - 0.0813 * uKey.b);
            float t0 = 0.04 + uTol * 0.3;
            float t1 = t0 + 0.02 + uSoft * 0.2;
            float al = uUseKey > 0.5 ? smoothstep(t0, t1, distance(a, k)) : 1.0;
            return c * half(al * la);
        }
    """

    fun apply(v: android.view.View, l: Layer, extra: android.graphics.RenderEffect? = null) {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val key = l.chroma
        val luma = l.lumaKey ?: 0
        if (key == null && luma == 0) { v.setRenderEffect(extra); return }
        val sh = android.graphics.RuntimeShader(SHADER)
        val k = key ?: 0
        sh.setFloatUniform("uKey", ((k shr 16) and 0xFF) / 255f, ((k shr 8) and 0xFF) / 255f, (k and 0xFF) / 255f)
        sh.setFloatUniform("uUseKey", if (key != null) 1f else 0f)
        sh.setFloatUniform("uLuma", luma.toFloat())
        sh.setFloatUniform("uLevel", l.lumaLevelOrDefault())
        sh.setFloatUniform("uLSoft", l.lumaSoft ?: 0.15f)
        sh.setFloatUniform("uTol", l.chromaTol ?: 0.4f)
        sh.setFloatUniform("uSoft", l.chromaSoft ?: 0.3f)
        val ck = android.graphics.RenderEffect.createRuntimeShaderEffect(sh, "content")
        v.setRenderEffect(if (extra != null) android.graphics.RenderEffect.createChainEffect(ck, extra) else ck)
    }
}

/** Export: chroma key on a video layer (the compositor then uses the transparency). */
@UnstableApi
class ChromaKeyEffect(
    private val key: Int?, private val tol: Float, private val soft: Float,
    private val luma: Int = 0, private val level: Float = 0.15f, private val lsoft: Float = 0.15f,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = ChromaProgram(key, tol, soft, luma, level, lsoft, useHdr)
}

@UnstableApi
private class ChromaProgram(
    private val key: Int?, private val tol: Float, private val soft: Float,
    private val luma: Int, private val level: Float, private val lsoft: Float, useHdr: Boolean,
) :
    BaseGlShaderProgram(useHdr, 1) {
    private val program = try { GlProgram(VERT, FRAG) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }

    init { program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE) }

    override fun configure(inputWidth: Int, inputHeight: Int) = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            val k = key ?: 0
            program.setFloatsUniform("uKey", floatArrayOf(((k shr 16) and 0xFF) / 255f, ((k shr 8) and 0xFF) / 255f, (k and 0xFF) / 255f))
            program.setFloatUniform("uTol", tol)
            program.setFloatUniform("uSoft", soft)
            program.setFloatUniform("uUseKey", if (key != null) 1f else 0f)
            program.setFloatUniform("uLuma", luma.toFloat())
            program.setFloatUniform("uLevel", level)
            program.setFloatUniform("uLSoft", lsoft)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    override fun release() {
        super.release()
        try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    companion object {
        const val VERT = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = aFramePosition;
                vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
            }
        """
        const val FRAG = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform vec3 uKey;
            uniform float uTol;
            uniform float uSoft;
            uniform float uUseKey;
            uniform float uLuma;
            uniform float uLevel;
            uniform float uLSoft;
            varying vec2 vTexSamplingCoord;
            $CHROMA_FN
            void main() {
                vec4 c = texture2D(uTexSampler, vTexSamplingCoord);
                float a = (uUseKey > 0.5 ? chromaAlpha(c.rgb, uKey, uTol, uSoft) : 1.0) * lumaAlpha(c.rgb, uLuma, uLevel, uLSoft);
                gl_FragColor = vec4(c.rgb, c.a * a);
            }
        """
    }
}

/**
 * Export: a picture layer (text, sticker, logo, image) drawn over the video with a blend mode,
 * moving / scaling / fading with its keyframes.
 */
@UnstableApi
class BlendLayerEffect(private val bitmap: Bitmap, private val layer: Layer, private val outW: Int, private val outH: Int) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = BlendProgram(bitmap, layer, outW, outH, useHdr)
}

@UnstableApi
private class BlendProgram(private val bitmap: Bitmap, private val l: Layer, private val outW: Int, private val outH: Int, useHdr: Boolean) :
    BaseGlShaderProgram(useHdr, 1) {
    private val program = try { GlProgram(ChromaProgram.VERT, FRAG) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private var tex = 0
    private var frameW = outW; private var frameH = outH

    init { program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE) }

    override fun configure(inputWidth: Int, inputHeight: Int): Size { frameW = inputWidth; frameH = inputHeight; return Size(inputWidth, inputHeight) }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            if (tex == 0) {
                val t = IntArray(1); GLES20.glGenTextures(1, t, 0); tex = t[0]
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            }
            val ms = presentationTimeUs / 1000
            val on = l.wholeVideo || ms in l.startMs..l.endMs
            val a = l.animAt(ms)
            val fx = LayerAnims.at(l, ms)
            val k = (if (l.size > 0f) a.size / l.size else 1f) * fx.scale
            val hw = bitmap.width * k / (2f * frameW); val hh = bitmap.height * k / (2f * frameH)
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setSamplerTexIdUniform("uLayer", tex, 1)
            program.setFloatsUniform("uRect", floatArrayOf(a.x + fx.dx, 1f - (a.y + fx.dy), hw, hh))
            program.setFloatUniform("uAlpha", if (on) (a.opacity * fx.alpha).coerceIn(0f, 1f) else 0f)
            program.setIntUniform("uMode", (l.blend ?: LayerBlend.NORMAL).ordinal)
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
        const val FRAG = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform sampler2D uLayer;
            uniform vec4 uRect;   // centre x, centre y (y up), half width, half height
            uniform float uAlpha;
            uniform int uMode;
            varying vec2 vTexSamplingCoord;
            vec3 blend(vec3 b, vec3 s) {
                if (uMode == 1) return 1.0 - (1.0 - b) * (1.0 - s);
                if (uMode == 2) return b * s;
                if (uMode == 3) return mix(2.0 * b * s, 1.0 - 2.0 * (1.0 - b) * (1.0 - s), step(0.5, b));
                if (uMode == 4) return min(b + s, 1.0);
                if (uMode == 5) return max(b, s);
                if (uMode == 6) return min(b, s);
                if (uMode == 7) return (1.0 - 2.0 * s) * b * b + 2.0 * s * b;
                if (uMode == 8) return abs(b - s);
                return s;
            }
            void main() {
                vec4 base = texture2D(uTexSampler, vTexSamplingCoord);
                vec2 uv = vTexSamplingCoord;
                vec2 lo = uRect.xy - uRect.zw;
                vec2 st = vec2((uv.x - lo.x) / (2.0 * uRect.z), (uRect.y + uRect.w - uv.y) / (2.0 * uRect.w));
                if (uAlpha <= 0.0 || st.x < 0.0 || st.x > 1.0 || st.y < 0.0 || st.y > 1.0) { gl_FragColor = base; return; }
                vec4 ov = texture2D(uLayer, st);
                vec3 s = ov.a > 0.0001 ? ov.rgb / ov.a : vec3(0.0);
                vec3 res = blend(base.rgb, s);
                gl_FragColor = vec4(mix(base.rgb, res, ov.a * uAlpha), base.a);
            }
        """
    }
}


/** Export: a clip's keyframed opacity (fades to black, or shows the layers under the main video). */
@UnstableApi
class ClipOpacityEffect(private val c: Clip, private val offsetMs: Long = 0L) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = OpacityProgram(c, offsetMs, useHdr)
}

@UnstableApi
private class OpacityProgram(private val c: Clip, private val offsetMs: Long, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program = try { GlProgram(VERT, FRAG) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private var t0 = Long.MIN_VALUE

    init { program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE) }

    override fun configure(inputWidth: Int, inputHeight: Int) = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        if (t0 == Long.MIN_VALUE) t0 = presentationTimeUs
        val src = c.srcAtOffset(offsetMs + (presentationTimeUs - t0) / 1000)
        val k = c.reframe.opacityAt(src - c.startMs).coerceIn(0f, 1f)
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatUniform("uK", k)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    override fun release() {
        super.release()
        try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    companion object {
        const val VERT = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = aFramePosition;
                vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
            }
        """
        const val FRAG = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform float uK;
            varying vec2 vTexSamplingCoord;
            void main() {
                vec4 c = texture2D(uTexSampler, vTexSamplingCoord);
                gl_FragColor = vec4(c.rgb * uK, c.a * uK);
            }
        """
    }
}

/**
 * Picture-in-picture frame for video layers: rounded corners, a border and a soft drop shadow
 * (the output grows by [pad] pixels on each side for the shadow, so the layer stays centred).
 */
@UnstableApi
class PipStyleEffect(private val radiusPx: Float, private val borderPx: Float, private val borderColor: Int, private val pad: Int) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = PipStyleProgram(radiusPx, borderPx, borderColor, pad, useHdr)
}

@UnstableApi
private class PipStyleProgram(private val r: Float, private val b: Float, private val bc: Int, private val pad: Int, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program = try { GlProgram(VERT, FRAG) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private var iw = 1; private var ih = 1
    init { program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE) }
    override fun configure(inputWidth: Int, inputHeight: Int): Size { iw = inputWidth; ih = inputHeight; return Size(inputWidth + pad * 2, inputHeight + pad * 2) }
    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTex", inputTexId, 0)
            program.setFloatsUniform("uOut", floatArrayOf((iw + pad * 2).toFloat(), (ih + pad * 2).toFloat()))
            program.setFloatsUniform("uIn", floatArrayOf(iw.toFloat(), ih.toFloat()))
            program.setFloatUniform("uPad", pad.toFloat())
            program.setFloatUniform("uR", r.coerceAtMost(minOf(iw, ih) / 2f))
            program.setFloatUniform("uB", b)
            program.setFloatsUniform("uBC", floatArrayOf(((bc shr 16) and 255) / 255f, ((bc shr 8) and 255) / 255f, (bc and 255) / 255f))
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }
    override fun release() { super.release(); try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) } }

    companion object {
        private const val VERT = """
            attribute vec4 aFramePosition;
            varying vec2 vUv;
            void main() { gl_Position = aFramePosition; vUv = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5); }
        """
        private const val FRAG = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform sampler2D uTex;
            uniform vec2 uOut;
            uniform vec2 uIn;
            uniform float uPad;
            uniform float uR;
            uniform float uB;
            uniform vec3 uBC;
            varying vec2 vUv;
            float sdRR(vec2 p, vec2 bb, float r) { vec2 q = abs(p) - bb + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
            void main() {
                vec2 px = vUv * uOut;
                vec2 c = px - uOut * 0.5;
                float d = sdRR(c, uIn * 0.5, uR);
                float inside = 1.0 - smoothstep(-0.75, 0.75, d);
                vec4 img = texture2D(uTex, clamp((px - uPad) / uIn, 0.0, 1.0));
                vec3 col = img.rgb;
                float a = img.a;
                if (uB > 0.0) { float bm = smoothstep(-uB - 0.75, -uB + 0.75, d); col = mix(col, uBC, bm); a = mix(a, 1.0, bm); }
                a *= inside;
                if (uPad > 0.0) {
                    float ds = sdRR(c - vec2(0.0, -uPad * 0.3), uIn * 0.5, uR);
                    float sa = 0.55 * (1.0 - smoothstep(-uPad * 0.3, uPad * 0.9, ds));
                    float oa = a + sa * (1.0 - a);
                    col = col * a / max(oa, 0.0001);
                    a = oa;
                }
                gl_FragColor = vec4(col, a);
            }
        """
    }
}
