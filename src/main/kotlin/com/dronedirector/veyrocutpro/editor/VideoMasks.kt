package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Mask kinds that work on video (drawn by the GPU every frame). */
val VIDEO_MASK_TYPES = listOf(MaskType.LINEAR, MaskType.RADIAL, MaskType.COLOR_RANGE, MaskType.LUMA_RANGE)
const val MAX_VIDEO_MASKS = 3

/** The masks that actually change the picture (visible, with a grade), at most [MAX_VIDEO_MASKS]. */
fun Clip.activeMasks(): List<PhotoMask> =
    (masks ?: emptyList()).filter { it.visible && !it.grade.isIdentity && it.type in VIDEO_MASK_TYPES && it.amount > 0f }.take(MAX_VIDEO_MASKS)

/** Shared numbers for one mask, used by the export shader and the live preview shader. */
object MaskUniforms {
    const val LUT_N = 33

    fun typeId(t: MaskType) = when (t) { MaskType.LINEAR -> 0f; MaskType.RADIAL -> 1f; MaskType.COLOR_RANGE -> 2f; else -> 3f }

    /** Geometry: linear (x0, y0, x1, y1) · radial (cx, cy, rx, ry) · colour (hue, half width, soft, 0) · brightness (low, high, soft, 0). */
    fun geo(m: PhotoMask): FloatArray = when (m.type) {
        MaskType.LINEAR -> floatArrayOf(m.x0, m.y0, m.x1, m.y1)
        MaskType.RADIAL -> floatArrayOf(m.cx, m.cy, m.rx, m.ry)
        MaskType.COLOR_RANGE -> floatArrayOf(m.hue / 360f, m.hueWidth / 720f, (4f + m.rangeSoft * 60f) / 360f, 0f)
        else -> floatArrayOf(m.lumLow, m.lumHigh, 0.01f + m.rangeSoft * 0.3f, 0f)
    }

    /** Options: type, feather (radial), invert, amount. */
    fun opt(m: PhotoMask) = floatArrayOf(typeId(m.type), m.feather.coerceIn(0.02f, 1f), if (m.invert) 1f else 0f, m.amount.coerceIn(0f, 1f))

    /** The mask's grade as a 2D strip (x = b * N + r, y = g), ARGB. */
    fun strip(g: ProGrade): IntArray {
        val n = LUT_N
        val cube = LutCache.cube(g)
        val px = IntArray(n * n * n)
        for (r in 0 until n) for (gg in 0 until n) for (b in 0 until n) px[gg * n * n + b * n + r] = cube[r][gg][b]
        return px
    }

    /** GLSL / AGSL shared maths (both languages accept this subset). */
    const val WEIGHT_GLSL = """
        vec3 mRgb2hsv(vec3 c) {
            vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
            vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
            vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
            float d = q.x - min(q.w, q.y);
            float e = 1.0e-10;
            return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
        }
        float mWeight(vec4 g, vec4 o, vec2 uv, vec2 size, vec3 c) {
            float w = 0.0;
            if (o.x < 0.5) {
                vec2 p = uv * size; vec2 a = g.xy * size; vec2 b = g.zw * size;
                vec2 d = b - a;
                float t = clamp(dot(p - a, d) / max(dot(d, d), 1.0), 0.0, 1.0);
                w = 1.0 - t * t * (3.0 - 2.0 * t);
            } else if (o.x < 1.5) {
                vec2 p = uv * size;
                vec2 r = max(g.zw * size, vec2(1.0));
                float dd = length((p - g.xy * size) / r);
                float inner = 1.0 - o.y;
                float t = clamp((dd - inner) / max(1.0 - inner, 0.01), 0.0, 1.0);
                w = 1.0 - t * t * (3.0 - 2.0 * t);
            } else if (o.x < 2.5) {
                vec3 h = mRgb2hsv(clamp(c, 0.0, 1.0));
                float d = abs(h.x - g.x); d = min(d, 1.0 - d);
                w = (1.0 - smoothstep(g.y, g.y + g.z, d)) * smoothstep(0.04, 0.18, h.y) * smoothstep(0.03, 0.12, h.z);
            } else {
                float l = dot(clamp(c, 0.0, 1.0), vec3(0.2126, 0.7152, 0.0722));
                w = smoothstep(g.x - g.z, g.x, l) * (1.0 - smoothstep(g.y, g.y + g.z, l));
            }
            if (o.z > 0.5) w = 1.0 - w;
            return w * o.w;
        }
    """
}

/** Export (and older-phone preview): each mask's own grade, blended in where the mask is. */
@UnstableApi
class MaskGradeEffect(private val masks: List<PhotoMask>, private val windows: List<LongRange?>? = null) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = try {
        MaskGradeProgram(masks.take(MAX_VIDEO_MASKS), useHdr, windows)
    } catch (t: Throwable) {
        // A GPU that can't build the mask shader: export without the masks instead of failing.
        android.util.Log.w("MaskGradeEffect", "Mask shader unavailable: ${t.message}")
        androidx.media3.effect.Contrast(0f).toGlShaderProgram(context, useHdr)
    }
    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = masks.isEmpty()
}

@UnstableApi
private class MaskGradeProgram(private val masks: List<PhotoMask>, useHdr: Boolean, private val windows: List<LongRange?>? = null) : BaseGlShaderProgram(useHdr, 1) {
    private val program = try { GlProgram(VERT, FRAG) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private val strips = masks.map { MaskUniforms.strip(it.grade) }
    private val tex = IntArray(MAX_VIDEO_MASKS)
    private var w = 1f; private var h = 1f

    init { program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE) }

    override fun configure(inputWidth: Int, inputHeight: Int): Size { w = inputWidth.toFloat(); h = inputHeight.toFloat(); return Size(inputWidth, inputHeight) }

    private fun upload() {
        if (tex[0] != 0) return
        GLES20.glGenTextures(MAX_VIDEO_MASKS, tex, 0)
        val n = MaskUniforms.LUT_N
        for (k in 0 until MAX_VIDEO_MASKS) {
            val strip = strips.getOrNull(k) ?: strips.firstOrNull() ?: IntArray(n * n * n)
            val buf = ByteBuffer.allocateDirect(strip.size * 4).order(ByteOrder.nativeOrder())
            for (p in strip) { buf.put(((p shr 16) and 0xFF).toByte()); buf.put(((p shr 8) and 0xFF).toByte()); buf.put((p and 0xFF).toByte()); buf.put(0xFF.toByte()) }
            buf.position(0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[k])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, n * n, n, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        }
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            upload()
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uSize", floatArrayOf(w, h))
            program.setFloatUniform("uN", MaskUniforms.LUT_N.toFloat())
            program.setFloatUniform("uCount", masks.size.toFloat())
            for (k in 0 until MAX_VIDEO_MASKS) {
                val m = masks.getOrNull(k)
                program.setFloatsUniform("uG$k", m?.let { MaskUniforms.geo(it) } ?: FloatArray(4))
                // Adjustment layers: only inside their time window (ms on the timeline).
                val on = windows?.getOrNull(k)?.let { (presentationTimeUs / 1000) in it } ?: true
                program.setFloatsUniform("uO$k", m?.let { MaskUniforms.opt(it).also { o -> if (!on) o[3] = 0f } } ?: floatArrayOf(0f, 0.5f, 0f, 0f))
                program.setSamplerTexIdUniform("uLut$k", tex[k], 1 + k)
            }
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    override fun release() {
        super.release()
        if (tex[0] != 0) GLES20.glDeleteTextures(MAX_VIDEO_MASKS, tex, 0)
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
            precision highp float;
            uniform sampler2D uTexSampler;
            uniform sampler2D uLut0;
            uniform sampler2D uLut1;
            uniform sampler2D uLut2;
            uniform vec2 uSize;
            uniform float uN;
            uniform float uCount;
            uniform vec4 uG0; uniform vec4 uG1; uniform vec4 uG2;
            uniform vec4 uO0; uniform vec4 uO1; uniform vec4 uO2;
            varying vec2 vTexSamplingCoord;
        """ + MaskUniforms.WEIGHT_GLSL + """
            vec3 lutOf(sampler2D lut, vec3 c) {
                float n = uN;
                vec3 s = clamp(c, 0.0, 1.0) * (n - 1.0);
                float b0 = floor(s.b);
                float b1 = min(b0 + 1.0, n - 1.0);
                float f = s.b - b0;
                vec2 p = vec2(s.r + 0.5, s.g + 0.5);
                vec3 c0 = texture2D(lut, vec2((b0 * n + p.x) / (n * n), p.y / n)).rgb;
                vec3 c1 = texture2D(lut, vec2((b1 * n + p.x) / (n * n), p.y / n)).rgb;
                return mix(c0, c1, f);
            }
            void main() {
                vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
                vec3 c = src.rgb;
                vec3 base = c;
                // Masks use top-left coordinates like the photo editor.
                vec2 uv = vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y);
                if (uCount > 0.5) c = mix(c, lutOf(uLut0, c), clamp(mWeight(uG0, uO0, uv, uSize, base), 0.0, 1.0));
                if (uCount > 1.5) c = mix(c, lutOf(uLut1, c), clamp(mWeight(uG1, uO1, uv, uSize, base), 0.0, 1.0));
                if (uCount > 2.5) c = mix(c, lutOf(uLut2, c), clamp(mWeight(uG2, uO2, uv, uSize, base), 0.0, 1.0));
                gl_FragColor = vec4(c, src.a);
            }
        """
    }
}

/** Adjustment layers at [ms] as full-frame "masks" (strength = the layer's opacity). */
fun adjustMasks(layers: List<Layer>, ms: Long?): List<PhotoMask> =
    layers.filter { it.visible && it.kind == LayerKind.ADJUST && it.adjGrade != null && !it.adjGrade.isIdentity && (ms == null || it.activeAt(ms)) }
        .take(MAX_VIDEO_MASKS)
        .map { asMask(it) }

fun asMask(l: Layer) = PhotoMask(id = l.id, type = MaskType.LUMA_RANGE, lumLow = 0f, lumHigh = 1f, rangeSoft = 0f, grade = l.adjGrade ?: ProGrade(), amount = l.opacity.coerceIn(0f, 1f))
