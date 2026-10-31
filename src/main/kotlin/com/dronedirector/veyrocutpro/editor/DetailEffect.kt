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

/** GPU sharpen, denoise, texture, clarity, vignette and film grain for video (preview and export). */
@UnstableApi
class DetailEffect(private val d: Detail) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = DetailProgram(d, useHdr)

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = !d.needsVideoPass
}

@UnstableApi
private class DetailProgram(private val d: Detail, useHdr: Boolean) :
    BaseGlShaderProgram(/* useHighPrecisionColorComponents= */ useHdr, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram = try {
        GlProgram(VERTEX, FRAGMENT)
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }
    private var texelW = 0f
    private var texelH = 0f
    private var aspect = 1f

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        texelW = 1f / inputWidth
        texelH = 1f / inputHeight
        aspect = inputWidth.toFloat() / inputHeight
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uTexel", floatArrayOf(texelW, texelH))
            program.setFloatUniform("uSharpen", d.sharpen * 1.5f)
            program.setFloatUniform("uDenoise", d.denoise)
            program.setFloatUniform("uVignette", d.vignette)
            program.setFloatUniform("uGrain", d.grain * 0.11f)
            program.setFloatUniform("uAspect", aspect)
            program.setFloatUniform("uTime", (presentationTimeUs % 10_000_000L) / 1_000_000f)
            program.setFloatUniform("uDetail", d.localDetail)
            program.setFloatUniform("uClarity", d.clarity)
            program.setFloatUniform("uTexture", d.texture)
            program.setFloatUniform("uLong", maxOf(1f / texelW, 1f / texelH))
            program.setIntUniform("uFx", d.fx.ordinal)
            program.setFloatUniform("uFxAmt", d.fxAmount)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun release() {
        super.release()
        try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    companion object {
        private const val VERTEX = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = aFramePosition;
                vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
            }
        """

        private const val FRAGMENT = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform vec2 uTexel;
            uniform float uSharpen;
            uniform float uDenoise;
            uniform float uVignette;
            uniform float uGrain;
            uniform float uAspect;
            uniform float uTime;
            uniform float uDetail;
            uniform float uClarity;
            uniform float uTexture;
            uniform float uLong;
            uniform int uFx;
            uniform float uFxAmt;
            varying vec2 vTexSamplingCoord;

            vec3 blur9(vec2 uv, float r) {
                vec3 s = vec3(0.0);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        s += texture2D(uTexSampler, uv + vec2(float(dx), float(dy)) * uTexel * r).rgb;
                    }
                }
                return s / 9.0;
            }

            // Soft blur of radius r pixels: two rings of 8 samples and the centre.
            vec3 ring(vec2 uv, float r) {
                vec3 s = texture2D(uTexSampler, uv).rgb;
                for (int k = 0; k < 8; k++) {
                    float a = float(k) * 0.7854;
                    vec2 o = vec2(cos(a), sin(a)) * uTexel * r;
                    s += texture2D(uTexSampler, uv + o).rgb + texture2D(uTexSampler, uv + o * 0.5).rgb;
                }
                return s / 17.0;
            }

            float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233)) + uTime * 7.0) * 43758.5453); }

            void main() {
                vec2 uv = vTexSamplingCoord;
                // Geometry effects first.
                if (uFx == 5) { if (uv.x > 0.5) uv.x = 1.0 - uv.x; }
                if (uFx == 4) {
                    float cells = mix(160.0, 24.0, uFxAmt);
                    vec2 cell = vec2(cells * uAspect, cells);
                    uv = (floor(uv * cell) + 0.5) / cell;
                }
                if (uFx == 7) { uv.x += (rand(vec2(floor(uv.y * 120.0), uTime)) - 0.5) * 0.006 * uFxAmt; }
                vec4 c = texture2D(uTexSampler, uv);
                vec3 rgb = c.rgb;
                if ((uDenoise > 0.0 || uSharpen > 0.0) && uFx != 4) {
                    vec3 sum = vec3(0.0);
                    float wsum = 0.0;
                    vec3 blur = vec3(0.0);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            vec3 s = texture2D(uTexSampler, uv + vec2(float(dx), float(dy)) * uTexel * 1.5).rgb;
                            blur += s;
                            // Edge-aware weight: similar colours count more.
                            float w = exp(-dot(s - rgb, s - rgb) * 60.0);
                            sum += s * w;
                            wsum += w;
                        }
                    }
                    blur /= 9.0;
                    if (uDenoise > 0.0) rgb = mix(rgb, sum / wsum, uDenoise);
                    if (uSharpen > 0.0) rgb = rgb + (rgb - blur) * uSharpen;
                }
                if (uDetail != 0.0) {
                    vec3 wide = blur9(uv, 6.0);
                    float l = dot(rgb, vec3(0.299, 0.587, 0.114));
                    float mid = 1.0 - abs(l - 0.5) * 2.0;
                    rgb += (rgb - wide) * uDetail * 1.2 * mid;
                }
                // Texture and clarity, sized to the frame like the photo editor (long side: 0.37 % and 1.7 %).
                if (uTexture != 0.0) {
                    vec3 fine = ring(uv, max(1.5, uLong * 0.0037));
                    rgb += (rgb - fine) * uTexture * 1.2;
                }
                if (uClarity != 0.0) {
                    vec3 broad = ring(uv, uLong * 0.0167);
                    float l = dot(rgb, vec3(0.299, 0.587, 0.114));
                    float mid = clamp(1.0 - abs(l - 0.5) * 2.0, 0.0, 1.0);
                    rgb += (rgb - broad) * uClarity * 1.1 * mid;
                }
                if (uFx == 1) { float l = dot(rgb, vec3(0.299, 0.587, 0.114)); rgb = mix(rgb, vec3(l), uFxAmt); }
                if (uFx == 2) { rgb = mix(rgb, blur9(uv, 2.0 + 8.0 * uFxAmt), 0.85); }
                if (uFx == 3 || uFx == 10) {
                    vec3 b = blur9(uv, 10.0);
                    vec3 bright = max(b - 0.55, 0.0) * 2.2;
                    rgb += bright * uFxAmt;
                    if (uFx == 10) { rgb = mix(rgb, b + 0.06, 0.35 * uFxAmt); rgb = mix(rgb, rgb * vec3(1.05, 0.98, 1.08), uFxAmt); }
                }
                if (uFx == 6) {
                    float o = 0.004 * uFxAmt + 0.001;
                    rgb.r = texture2D(uTexSampler, uv + vec2(o, 0.0)).r;
                    rgb.b = texture2D(uTexSampler, uv - vec2(o, 0.0)).b;
                }
                if (uFx == 7) {
                    float line = 0.92 + 0.08 * sin(uv.y * 900.0);
                    rgb = mix(rgb, rgb * line * vec3(1.06, 0.97, 1.02) + (rand(uv * 600.0) - 0.5) * 0.06, uFxAmt);
                    rgb.r = mix(rgb.r, texture2D(uTexSampler, uv + vec2(0.003, 0.0)).r, uFxAmt);
                }
                if (uFx == 8) { float lv = mix(12.0, 3.0, uFxAmt); rgb = floor(rgb * lv + 0.5) / lv; }
                if (uFx == 9) { rgb = mix(rgb, 1.0 - rgb, uFxAmt); }
                if (uVignette != 0.0) {
                    vec2 p = (uv - 0.5) * vec2(uAspect, 1.0) / sqrt(uAspect * uAspect * 0.25 + 0.25);
                    float d = length(p);
                    float f = clamp((d - 0.35) / 0.65, 0.0, 1.0);
                    rgb *= 1.0 + uVignette * f * f;
                }
                if (uGrain > 0.0) {
                    rgb += (rand(uv * 1000.0) - 0.5) * uGrain;
                }
                gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), c.a);
            }
        """
    }
}
