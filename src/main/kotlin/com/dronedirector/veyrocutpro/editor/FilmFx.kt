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
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/**
 * Film emulation, lens correction and deflicker for a clip (Color → FILM & LENS). Written for
 * AlcorCut Pro (no third-party code).
 */
data class FilmLook(
    /** Red-orange glow around bright highlights, like film's halation (0..1). */
    val halation: Float = 0f,
    /** Soft glow from the brightest parts (0..1). */
    val bloom: Float = 0f,
    /** Gentle frame-to-frame wobble of a film gate (0..1). */
    val weave: Float = 0f,
    /** Film grain amount (0..1) and its size (0 fine .. 1 coarse). */
    val grain: Float = 0f,
    val grainSize: Float = 0.4f,
    /** Lens distortion fix: + straightens barrel (wide / action cams), − straightens pincushion. */
    val lensK: Float = 0f,
    /** Chromatic aberration fix (colour fringes at the edges), −1..1. */
    val lensCa: Float = 0f,
    /** Vignette fix: brightens dark corners (0..1). */
    val lensVig: Float = 0f,
    /** Deflicker strength (0 = off): evens out brightness flicker (timelapse, lights, hyperlapse). */
    val deflicker: Float = 0f,
    /** Name of the preset last picked (display only). */
    val preset: String? = null,
    /** Sky replacement: which sky ([VideoSky.NAMES], −1 = off), strength, the horizon (0..1 from the top), how much counts as sky, and light matching. */
    val sky: Int = -1,
    val skyAmount: Float = 1f,
    val horizon: Float = 0.45f,
    val skyTol: Float = 0.5f,
    val skyLight: Float = 0.3f,
) {
    val isZero: Boolean get() = copy(preset = null) == FilmLook()
}

/** Skies for video sky replacement: colours at the top, the middle and the horizon. */
object VideoSky {
    val NAMES = listOf("Clear blue", "Golden hour", "Sunset", "Dramatic", "Night")
    private val COLS = listOf(
        intArrayOf(0x2E6FD8, 0x5A9BE8, 0xBFDDF5), intArrayOf(0x5A7FC2, 0xE9B784, 0xF8D49A), intArrayOf(0x2B2A6B, 0xD9576B, 0xFFB25B),
        intArrayOf(0x1E2A3A, 0x4A5C70, 0xA9B6C2), intArrayOf(0x050B1E, 0x0E1B3D, 0x2B3D66),
    )
    /** The three colours of sky [i] as 0..1 floats (9 values). */
    fun colours(i: Int): FloatArray {
        val c = COLS.getOrElse(i) { COLS[0] }
        return FloatArray(9) { k -> ((c[k / 3] shr (16 - 8 * (k % 3))) and 0xFF) / 255f }
    }
}

/** Ready-made film stocks and lens profiles (own settings, generic names). */
object FilmPresets {
    val films: List<Pair<String, FilmLook>> = listOf(
        "Off" to FilmLook(),
        "35mm Daylight" to FilmLook(halation = 0.25f, bloom = 0.1f, grain = 0.25f, grainSize = 0.3f, weave = 0.1f),
        "35mm Tungsten 500" to FilmLook(halation = 0.55f, bloom = 0.25f, grain = 0.45f, grainSize = 0.45f, weave = 0.15f),
        "16mm" to FilmLook(halation = 0.4f, bloom = 0.2f, grain = 0.65f, grainSize = 0.65f, weave = 0.35f),
        "Super 8" to FilmLook(halation = 0.5f, bloom = 0.35f, grain = 0.85f, grainSize = 0.9f, weave = 0.75f, lensVig = 0f),
        "Dreamy bloom" to FilmLook(halation = 0.2f, bloom = 0.7f, grain = 0.15f, grainSize = 0.3f),
        "Night city glow" to FilmLook(halation = 0.85f, bloom = 0.45f, grain = 0.3f, grainSize = 0.4f),
    )
    val lenses: List<Pair<String, FilmLook>> = listOf(
        "No correction" to FilmLook(),
        "Action cam wide" to FilmLook(lensK = 0.75f, lensCa = 0.3f, lensVig = 0.25f),
        "Action cam linear-ish" to FilmLook(lensK = 0.4f, lensCa = 0.2f, lensVig = 0.15f),
        "Drone wide" to FilmLook(lensK = 0.25f, lensCa = 0.25f, lensVig = 0.2f),
        "Phone ultra-wide" to FilmLook(lensK = 0.35f, lensCa = 0.35f, lensVig = 0.3f),
        "Telephoto pincushion" to FilmLook(lensK = -0.25f),
    )

    /** [base] with the film part of [p] (lens and deflicker kept). */
    fun withFilm(base: FilmLook, name: String, p: FilmLook) =
        base.copy(halation = p.halation, bloom = p.bloom, weave = p.weave, grain = p.grain, grainSize = p.grainSize, preset = name)

    fun withLens(base: FilmLook, p: FilmLook) = base.copy(lensK = p.lensK, lensCa = p.lensCa, lensVig = p.lensVig)
}

/** The film / lens / deflicker effects of [c], or none. */
@UnstableApi
fun filmEffects(c: Clip): List<androidx.media3.common.Effect> = c.film?.takeIf { !it.isZero }?.let { listOf(FilmEffect(it)) } ?: emptyList()

/** Effects the preview player runs itself on Android 13+ (the grade and the background cut-out are drawn on the view): film / lens. */
@UnstableApi
fun liveEffects(c: Clip): List<androidx.media3.common.Effect> =
    // Film looks and privacy blur are drawn on the view (LivePreviewFx); only deflicker needs the frames themselves.
    c.film?.deflicker?.takeIf { it > 0f }?.let { listOf(FilmEffect(FilmLook(deflicker = it))) } ?: emptyList()

/** Just the local-contrast part of a clip's detail (texture, clarity, image detail), or null when unused. */
fun localDetailOf(c: Clip): Detail? = c.detail.let { d ->
    if (d.clarity == 0f && d.texture == 0f && d.localDetail == 0f) null else Detail(clarity = d.clarity, texture = d.texture, localDetail = d.localDetail)
}

/** What [liveEffects] depends on (to know when to rebuild them). */
fun liveKey(c: Clip): Any? = c.film?.deflicker?.takeIf { it > 0f }

/** Preview before Android 13 (effects in the player): people background removal first, then the rest. */
@UnstableApi
fun olderPreviewEffects(c: Clip): List<androidx.media3.common.Effect> =
    (if (c.privacy?.active == true) listOf(PrivacyBlurEffect(c, 0L, preview = true)) else emptyList()) +
    (c.bg?.takeIf { it.magic == null }?.let { listOf(BgRemoveEffect(it.mode, it.color, it.invert == true)) } ?: emptyList()) + clipEffects(c)

@UnstableApi
class FilmEffect(private val f: FilmLook) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = FilmProgram(f, useHdr)
    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = f.isZero
}

@UnstableApi
private class FilmProgram(private val f: FilmLook, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program: GlProgram = try { GlProgram(VERTEX, FRAGMENT) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private val small: GlProgram? = if (f.deflicker > 0f) try { GlProgram(VERTEX, SMALL) } catch (_: Throwable) { null } else null
    private var w = 1; private var h = 1
    private var fbo = 0; private var tex = 0
    private val px: ByteBuffer = ByteBuffer.allocateDirect(M * M * 4).order(ByteOrder.nativeOrder())
    // Deflicker state: the slowly-followed brightness and the last frame time.
    private var smooth = -1.0; private var lastUs = Long.MIN_VALUE

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        small?.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size { w = inputWidth; h = inputHeight; return Size(inputWidth, inputHeight) }

    /** Mean brightness (linear light) of the frame, from a small copy read back from the GPU. */
    private fun measure(texId: Int): Double? {
        val sp = small ?: return null
        return try {
            val prevFbo = IntArray(1); GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, prevFbo, 0)
            val vp = IntArray(4); GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0)
            if (fbo == 0) { tex = GlUtil.createTexture(M, M, false); fbo = GlUtil.createFboForTexture(tex) }
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
            GLES20.glViewport(0, 0, M, M)
            sp.use()
            sp.setSamplerTexIdUniform("uTex", texId, 0)
            sp.setFloatsUniform("uStep", floatArrayOf(1f / M / 4f, 1f / M / 4f))
            sp.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            px.clear()
            GLES20.glReadPixels(0, 0, M, M, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, px)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFbo[0])
            GLES20.glViewport(vp[0], vp[1], vp[2], vp[3])
            var s = 0.0
            for (i in 0 until M * M) {
                val r = (px.get(i * 4).toInt() and 255) / 255.0
                val g = (px.get(i * 4 + 1).toInt() and 255) / 255.0
                val b = (px.get(i * 4 + 2).toInt() and 255) / 255.0
                s += (0.2126 * r + 0.7152 * g + 0.0722 * b).pow(2.2)
            }
            (s / (M * M)).coerceAtLeast(1e-4)
        } catch (_: Throwable) { null }
    }

    /** Gain (display space) that evens out the brightness of this frame. */
    private fun deflickerGain(texId: Int, us: Long): Float {
        if (f.deflicker <= 0f) return 1f
        val m = measure(texId) ?: return 1f
        val dt = if (lastUs == Long.MIN_VALUE || us < lastUs || us - lastUs > 2_000_000) -1.0 else (us - lastUs) / 1e6
        lastUs = us
        // A new shot (big jump) or a seek: start following from here.
        if (smooth < 0 || dt < 0 || abs(ln(m / smooth)) > 0.7) { smooth = m; return 1f }
        val tau = 0.25 + 1.5 * f.deflicker
        smooth += (m - smooth) * (1 - exp(-dt / tau))
        val g = (smooth / m).pow(f.deflicker.toDouble().coerceIn(0.0, 1.0)).coerceIn(0.6, 1.6)
        return g.pow(1 / 2.2).toFloat()
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            val gain = deflickerGain(inputTexId, presentationTimeUs)
            val t = presentationTimeUs / 1e6
            // Gate weave: two slow sine mixes per axis plus a little per-frame jitter.
            val wx = (sin(t * 2.1) * 0.6 + sin(t * 5.3 + 1.7) * 0.3 + sin(t * 13.0) * 0.1) * 0.0018 * f.weave
            val wy = (sin(t * 1.7 + 0.5) * 0.6 + sin(t * 4.1 + 2.9) * 0.3 + sin(t * 11.0 + 0.3) * 0.1) * 0.0026 * f.weave
            program.use()
            program.setSamplerTexIdUniform("uTex", inputTexId, 0)
            program.setFloatsUniform("uTexel", floatArrayOf(1f / w, 1f / h))
            program.setFloatUniform("uAspect", w.toFloat() / h)
            program.setFloatUniform("uTime", (presentationTimeUs % 100_000_000L) / 1e6f)
            program.setFloatUniform("uHal", f.halation)
            program.setFloatUniform("uBloom", f.bloom)
            program.setFloatUniform("uGrain", f.grain)
            program.setFloatUniform("uGrainPx", 1f + f.grainSize * 3f * (h / 1080f).coerceAtLeast(0.5f))
            program.setFloatUniform("uK", f.lensK)
            program.setFloatUniform("uCa", f.lensCa)
            program.setFloatUniform("uVig", f.lensVig)
            program.setFloatUniform("uGain", gain)
            program.setFloatsUniform("uWeave", floatArrayOf(wx.toFloat(), wy.toFloat()))
            program.setFloatsUniform("uRes", floatArrayOf(w.toFloat(), h.toFloat()))
            val sc = VideoSky.colours(f.sky)
            program.setFloatUniform("uSky", if (f.sky >= 0) f.skyAmount else 0f)
            program.setFloatUniform("uHorizon", f.horizon)
            program.setFloatUniform("uSkyTol", f.skyTol)
            program.setFloatUniform("uSkyLight", f.skyLight)
            program.setFloatsUniform("uSkyTop", sc.copyOfRange(0, 3))
            program.setFloatsUniform("uSkyMid", sc.copyOfRange(3, 6))
            program.setFloatsUniform("uSkyHor", sc.copyOfRange(6, 9))
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun release() {
        super.release()
        try {
            program.delete(); small?.delete()
            if (fbo != 0) GlUtil.deleteFbo(fbo)
            if (tex != 0) GlUtil.deleteTexture(tex)
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    companion object {
        private const val M = 16

        private const val VERTEX = """
            attribute vec4 aFramePosition;
            varying vec2 vUv;
            void main() {
                gl_Position = aFramePosition;
                vUv = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
            }
        """

        /** 4×4 taps per output pixel: a 16×16 copy averages 4096 points of the frame. */
        private const val SMALL = """
            precision mediump float;
            uniform sampler2D uTex;
            uniform vec2 uStep;
            varying vec2 vUv;
            void main() {
                vec3 s = vec3(0.0);
                for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++)
                    s += texture2D(uTex, vUv + (vec2(float(i), float(j)) - 1.5) * uStep).rgb;
                gl_FragColor = vec4(s / 16.0, 1.0);
            }
        """

        const val FRAGMENT = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform sampler2D uTex;
            uniform vec2 uTexel;
            uniform vec2 uRes;
            uniform float uAspect;
            uniform float uTime;
            uniform float uHal;
            uniform float uBloom;
            uniform float uGrain;
            uniform float uGrainPx;
            uniform float uK;
            uniform float uCa;
            uniform float uVig;
            uniform float uGain;
            uniform vec2 uWeave;
            uniform float uSky;
            uniform float uHorizon;
            uniform float uSkyTol;
            uniform float uSkyLight;
            uniform vec3 uSkyTop;
            uniform vec3 uSkyMid;
            uniform vec3 uSkyHor;
            varying vec2 vUv;

            float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
            float vn(vec2 q) {
                vec2 i = floor(q); vec2 f = fract(q); f = f * f * (3.0 - 2.0 * f);
                return mix(mix(h21(i), h21(i + vec2(1.0, 0.0)), f.x), mix(h21(i + vec2(0.0, 1.0)), h21(i + vec2(1.0, 1.0)), f.x), f.y);
            }
            // Lens: maps an output position to where it is in the source picture.
            vec2 lens(vec2 uv, float scale) {
                vec2 p = (uv - 0.5) * vec2(uAspect, 1.0);
                float rn = 0.25 * (uAspect * uAspect + 1.0);
                float r2 = dot(p, p) / rn;
                float k = uK * 0.35;
                // Normalised so the picture still fills the frame (no black edges).
                float norm = uK >= 0.0 ? 1.0 + k : 1.0 + k / (uAspect * uAspect + 1.0);
                p *= (1.0 + k * r2) / norm * scale;
                return p / vec2(uAspect, 1.0) + 0.5;
            }
            vec3 src(vec2 uv) {
                uv += uWeave;
                if (uCa == 0.0) return texture2D(uTex, clamp(lens(uv, 1.0), 0.0, 1.0)).rgb;
                float ca = uCa * 0.0035;
                return vec3(
                    texture2D(uTex, clamp(lens(uv, 1.0 - ca), 0.0, 1.0)).r,
                    texture2D(uTex, clamp(lens(uv, 1.0), 0.0, 1.0)).g,
                    texture2D(uTex, clamp(lens(uv, 1.0 + ca), 0.0, 1.0)).b);
            }
            vec3 glowAt(vec2 uv, float radius) {
                vec3 s = vec3(0.0);
                for (int i = 0; i < 12; i++) {
                    float a = float(i) * 0.5236;
                    vec2 o = vec2(cos(a), sin(a)) * radius * vec2(1.0 / uAspect, 1.0);
                    vec3 c = texture2D(uTex, clamp(lens(uv + uWeave + o, 1.0), 0.0, 1.0)).rgb;
                    s += max(c - 0.62, 0.0);
                }
                return s / 12.0;
            }
            // Sky: above the horizon, smooth, and blue or bright and pale. 1 = sky.
            float skyW(vec2 uv, vec3 c) {
                float y = 1.0 - uv.y;
                float top = 1.0 - smoothstep(uHorizon - 0.08, uHorizon + 0.03, y);
                if (top <= 0.0) return 0.0;
                float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
                float mx = max(max(c.r, c.g), c.b); float mn = min(min(c.r, c.g), c.b);
                float sat = mx > 0.0 ? (mx - mn) / mx : 0.0;
                float t = uSkyTol * 0.1;
                float blue = smoothstep(-0.02 - t, 0.1 - t, c.b - max(c.r, c.g * 0.95));
                float pale = smoothstep(0.62 - t * 2.0, 0.85 - t * 2.0, l) * (1.0 - smoothstep(0.2 + t, 0.45 + t, sat));
                vec3 n1 = texture2D(uTex, clamp(uv + vec2(uTexel.x * 3.0, 0.0), 0.0, 1.0)).rgb;
                vec3 n2 = texture2D(uTex, clamp(uv + vec2(0.0, uTexel.y * 3.0), 0.0, 1.0)).rgb;
                float tx = length(c - n1) + length(c - n2);
                float smoothArea = 1.0 - smoothstep(0.05 + t * 0.4, 0.16 + t * 0.6, tx);
                return top * max(blue, pale) * smoothArea;
            }
            vec3 skyCol(vec2 uv) {
                float k = clamp((1.0 - uv.y) / max(uHorizon, 0.05), 0.0, 1.0);
                return k < 0.6 ? mix(uSkyTop, uSkyMid, k / 0.6) : mix(uSkyMid, uSkyHor, (k - 0.6) / 0.4);
            }
            void main() {
                vec2 uv = vUv;
                float alpha = texture2D(uTex, uv).a;
                vec3 rgb = src(uv);
                if (uSky > 0.0) {
                    float w = skyW(uv, rgb) * uSky;
                    // Keep a little of the old sky's brightness (clouds' light) in the new one.
                    float l = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
                    vec3 s = skyCol(uv) * (0.85 + 0.3 * l);
                    rgb = mix(rgb, s, w);
                    rgb = mix(rgb, rgb * (0.8 + 0.4 * uSkyHor), uSkyLight * (1.0 - w) * 0.6);
                }
                if (uHal > 0.0 || uBloom > 0.0) {
                    vec3 g1 = glowAt(uv, 0.006);
                    vec3 g2 = glowAt(uv, 0.016);
                    vec3 g3 = glowAt(uv, 0.032);
                    if (uBloom > 0.0) rgb += (g1 * 0.6 + g2 * 0.8 + g3 * 0.6) * uBloom * 1.6;
                    if (uHal > 0.0) {
                        float hl = dot(g1 * 0.5 + g2 + g3 * 0.7, vec3(0.3, 0.5, 0.2));
                        vec3 halo = vec3(1.0, 0.32, 0.12) * hl * uHal * 2.6;
                        rgb = 1.0 - (1.0 - rgb) * (1.0 - clamp(halo, 0.0, 1.0));
                    }
                }
                if (uVig > 0.0) {
                    vec2 p = (uv - 0.5) * vec2(uAspect, 1.0);
                    float r2 = dot(p, p) / (0.25 * (uAspect * uAspect + 1.0));
                    rgb *= 1.0 + uVig * r2 * r2 * 0.7 + uVig * r2 * 0.2;
                }
                rgb *= uGain;
                if (uGrain > 0.0) {
                    vec2 q = uv * uRes / uGrainPx;
                    float fr = floor(uTime * 24.0);
                    vec2 off = vec2(h21(vec2(fr, 1.3)), h21(vec2(fr, 7.9))) * 400.0;
                    float n = vn(q + off) - 0.5;
                    float nc = vn(q * 1.3 + off.yx) - 0.5;
                    float l = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
                    float amt = uGrain * 0.16 * (0.35 + 1.3 * l * (1.0 - l) * 2.0);
                    rgb += vec3(n + nc * 0.15, n, n - nc * 0.15) * amt;
                }
                gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), alpha);
            }
        """
    }
}
