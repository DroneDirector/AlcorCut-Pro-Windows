package com.dronedirector.veyrocutpro.editor

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import java.util.WeakHashMap
import kotlin.math.sin

/**
 * Film looks and privacy blur drawn on the preview (Android 13+), redrawn every frame like the grade, so changes
 * show at once and moving things are followed while playing. Same maths as the export's GPU effects. Own code.
 */
object FilmPreview {
    private val shaders = WeakHashMap<View, RuntimeShader>()

    fun effect(v: View, c: Clip, srcMs: Long): RenderEffect? {
        if (Build.VERSION.SDK_INT < 33 || v.width <= 0 || v.height <= 0) return null
        val f = c.film ?: return null
        val look = f.copy(deflicker = 0f, preset = null)
        if (look.isZero) return null
        val sh = shaders.getOrPut(v) { try { RuntimeShader(AGSL) } catch (_: Throwable) { return null } }
        val t = srcMs / 1000.0
        val wx = (sin(t * 2.1) * 0.6 + sin(t * 5.3 + 1.7) * 0.3 + sin(t * 13.0) * 0.1) * 0.0018 * f.weave
        val wy = (sin(t * 1.7 + 0.5) * 0.6 + sin(t * 4.1 + 2.9) * 0.3 + sin(t * 11.0 + 0.3) * 0.1) * 0.0026 * f.weave
        val w = v.width.toFloat(); val h = v.height.toFloat()
        sh.setFloatUniform("uSize", w, h)
        sh.setFloatUniform("uAspect", w / h)
        sh.setFloatUniform("uTime", ((srcMs % 100_000_000L) / 1000f))
        sh.setFloatUniform("uHal", f.halation)
        sh.setFloatUniform("uBloom", f.bloom)
        sh.setFloatUniform("uGrain", f.grain)
        sh.setFloatUniform("uGrainPx", 1f + f.grainSize * 3f * (h / 1080f).coerceAtLeast(0.5f))
        sh.setFloatUniform("uK", f.lensK)
        sh.setFloatUniform("uCa", f.lensCa)
        sh.setFloatUniform("uVig", f.lensVig)
        sh.setFloatUniform("uWeave", wx.toFloat(), wy.toFloat())
        val sc = VideoSky.colours(f.sky)
        sh.setFloatUniform("uSky", if (f.sky >= 0) f.skyAmount else 0f)
        sh.setFloatUniform("uHorizon", f.horizon)
        sh.setFloatUniform("uSkyTol", f.skyTol)
        sh.setFloatUniform("uSkyLight", f.skyLight)
        sh.setFloatUniform("uSkyTop", sc[0], sc[1], sc[2])
        sh.setFloatUniform("uSkyMid", sc[3], sc[4], sc[5])
        sh.setFloatUniform("uSkyHor", sc[6], sc[7], sc[8])
        return RenderEffect.createRuntimeShaderEffect(sh, "content")
    }

    private const val AGSL = """
        uniform shader content;
        uniform float2 uSize;
        uniform float uAspect;
        uniform float uTime;
        uniform float uHal;
        uniform float uBloom;
        uniform float uGrain;
        uniform float uGrainPx;
        uniform float uK;
        uniform float uCa;
        uniform float uVig;
        uniform float2 uWeave;
        uniform float uSky;
        uniform float uHorizon;
        uniform float uSkyTol;
        uniform float uSkyLight;
        uniform float3 uSkyTop;
        uniform float3 uSkyMid;
        uniform float3 uSkyHor;

        float h21(float2 p) { p = fract(p * float2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
        float vn(float2 q) {
            float2 i = floor(q); float2 f = fract(q); f = f * f * (3.0 - 2.0 * f);
            return mix(mix(h21(i), h21(i + float2(1.0, 0.0)), f.x), mix(h21(i + float2(0.0, 1.0)), h21(i + float2(1.0, 1.0)), f.x), f.y);
        }
        float3 tex(float2 uv) {
            half4 c = content.eval(clamp(uv, 0.0, 1.0) * uSize);
            return c.a > 0.0 ? float3(c.rgb) / float(c.a) : float3(0.0);
        }
        float2 lens(float2 uv, float scale) {
            float2 p = (uv - 0.5) * float2(uAspect, 1.0);
            float rn = 0.25 * (uAspect * uAspect + 1.0);
            float r2 = dot(p, p) / rn;
            float k = uK * 0.35;
            float norm = uK >= 0.0 ? 1.0 + k : 1.0 + k / (uAspect * uAspect + 1.0);
            p *= (1.0 + k * r2) / norm * scale;
            return p / float2(uAspect, 1.0) + 0.5;
        }
        float3 src(float2 uv) {
            uv += uWeave;
            if (uCa == 0.0) return tex(lens(uv, 1.0));
            float ca = uCa * 0.0035;
            return float3(tex(lens(uv, 1.0 - ca)).r, tex(lens(uv, 1.0)).g, tex(lens(uv, 1.0 + ca)).b);
        }
        float3 glowAt(float2 uv, float radius) {
            float3 s = float3(0.0);
            for (int i = 0; i < 12; i++) {
                float a = float(i) * 0.5236;
                float2 o = float2(cos(a), sin(a)) * radius * float2(1.0 / uAspect, 1.0);
                s += max(tex(lens(uv + uWeave + o, 1.0)) - 0.62, 0.0);
            }
            return s / 12.0;
        }
        float skyW(float2 uv, float3 c) {
            float y = uv.y;
            float top = 1.0 - smoothstep(uHorizon - 0.08, uHorizon + 0.03, y);
            if (top <= 0.0) return 0.0;
            float l = dot(c, float3(0.2126, 0.7152, 0.0722));
            float mx = max(max(c.r, c.g), c.b); float mn = min(min(c.r, c.g), c.b);
            float sat = mx > 0.0 ? (mx - mn) / mx : 0.0;
            float t = uSkyTol * 0.1;
            float blue = smoothstep(-0.02 - t, 0.1 - t, c.b - max(c.r, c.g * 0.95));
            float pale = smoothstep(0.62 - t * 2.0, 0.85 - t * 2.0, l) * (1.0 - smoothstep(0.2 + t, 0.45 + t, sat));
            float3 n1 = tex(uv + float2(3.0 / uSize.x, 0.0));
            float3 n2 = tex(uv + float2(0.0, 3.0 / uSize.y));
            float tx = length(c - n1) + length(c - n2);
            float smoothArea = 1.0 - smoothstep(0.05 + t * 0.4, 0.16 + t * 0.6, tx);
            return top * max(blue, pale) * smoothArea;
        }
        float3 skyCol(float2 uv) {
            float k = clamp(uv.y / max(uHorizon, 0.05), 0.0, 1.0);
            return k < 0.6 ? mix(uSkyTop, uSkyMid, k / 0.6) : mix(uSkyMid, uSkyHor, (k - 0.6) / 0.4);
        }
        half4 main(float2 fc) {
            float2 uv = fc / uSize;
            half4 orig = content.eval(fc);
            float3 rgb = src(uv);
            if (uSky > 0.0) {
                float w = skyW(uv, rgb) * uSky;
                float l = dot(rgb, float3(0.2126, 0.7152, 0.0722));
                float3 s = skyCol(uv) * (0.85 + 0.3 * l);
                rgb = mix(rgb, s, w);
                rgb = mix(rgb, rgb * (0.8 + 0.4 * uSkyHor), uSkyLight * (1.0 - w) * 0.6);
            }
            if (uHal > 0.0 || uBloom > 0.0) {
                float3 g1 = glowAt(uv, 0.006);
                float3 g2 = glowAt(uv, 0.016);
                float3 g3 = glowAt(uv, 0.032);
                if (uBloom > 0.0) rgb += (g1 * 0.6 + g2 * 0.8 + g3 * 0.6) * uBloom * 1.6;
                if (uHal > 0.0) {
                    float hl = dot(g1 * 0.5 + g2 + g3 * 0.7, float3(0.3, 0.5, 0.2));
                    float3 halo = float3(1.0, 0.32, 0.12) * hl * uHal * 2.6;
                    rgb = 1.0 - (1.0 - rgb) * (1.0 - clamp(halo, 0.0, 1.0));
                }
            }
            if (uVig > 0.0) {
                float2 p = (uv - 0.5) * float2(uAspect, 1.0);
                float r2 = dot(p, p) / (0.25 * (uAspect * uAspect + 1.0));
                rgb *= 1.0 + uVig * r2 * r2 * 0.7 + uVig * r2 * 0.2;
            }
            if (uGrain > 0.0) {
                float2 q = fc / uGrainPx;
                float fr = floor(uTime * 24.0);
                float2 off = float2(h21(float2(fr, 1.3)), h21(float2(fr, 7.9))) * 400.0;
                float n = vn(q + off) - 0.5;
                float nc = vn(q * 1.3 + off.yx) - 0.5;
                float l = dot(rgb, float3(0.2126, 0.7152, 0.0722));
                float amt = uGrain * 0.16 * (0.35 + 1.3 * l * (1.0 - l) * 2.0);
                rgb += float3(n + nc * 0.15, n, n - nc * 0.15) * amt;
            }
            float a = float(orig.a);
            return half4(half3(clamp(rgb, 0.0, 1.0) * a), half(a));
        }
    """
}

object PrivacyPreview {
    private val shaders = WeakHashMap<View, RuntimeShader>()

    fun effect(v: View, c: Clip, srcMs: Long): RenderEffect? {
        if (Build.VERSION.SDK_INT < 33 || v.width <= 0 || v.height <= 0) return null
        val p = c.privacy?.takeIf { it.active } ?: return null
        val sh = shaders.getOrPut(v) { try { RuntimeShader(AGSL) } catch (_: Throwable) { return null } }
        val boxes = p.boxesAt(srcMs)
        sh.setFloatUniform("uSize", v.width.toFloat(), v.height.toFloat())
        sh.setFloatUniform("uCount", boxes.size.toFloat())
        sh.setFloatUniform("uStrength", p.strength.coerceIn(0.05f, 1f))
        sh.setFloatUniform("uPixel", if (p.pixelate) 1f else 0f)
        for (k in 0 until PrivacyBlur.MAX) {
            val b = boxes.getOrNull(k) ?: floatArrayOf(0f, 0f, 0f, 0f)
            sh.setFloatUniform("uB$k", b[0], b[1], b[2], b[3])
        }
        return RenderEffect.createRuntimeShaderEffect(sh, "content")
    }

    private const val AGSL = """
        uniform shader content;
        uniform float2 uSize;
        uniform float uCount;
        uniform float uStrength;
        uniform float uPixel;
        uniform float4 uB0; uniform float4 uB1; uniform float4 uB2; uniform float4 uB3;
        uniform float4 uB4; uniform float4 uB5; uniform float4 uB6; uniform float4 uB7;

        float3 tex(float2 uv) {
            half4 c = content.eval(clamp(uv, 0.0, 1.0) * uSize);
            return c.a > 0.0 ? float3(c.rgb) / float(c.a) : float3(0.0);
        }
        float4 boxAt(int k) {
            if (k == 0) return uB0; if (k == 1) return uB1; if (k == 2) return uB2; if (k == 3) return uB3;
            if (k == 4) return uB4; if (k == 5) return uB5; if (k == 6) return uB6; return uB7;
        }
        half4 main(float2 fc) {
            float2 uv = fc / uSize;
            half4 orig = content.eval(fc);
            float m = 0.0; float4 b = float4(0.0);
            for (int k = 0; k < 8; k++) {
                if (float(k) >= uCount) break;
                float4 bx = boxAt(k);
                float2 d = (uv - bx.xy) / max(bx.zw * 0.5, float2(0.001));
                float w = 1.0 - smoothstep(0.85, 1.0, length(d));
                if (w > m) { m = w; b = bx; }
            }
            if (m <= 0.0) return orig;
            float3 o;
            if (uPixel > 0.5) {
                float cell = max(b.z, b.w) * mix(0.06, 0.22, uStrength);
                o = tex((floor(uv / cell) + 0.5) * cell);
            } else {
                float2 rad = float2(max(b.z, b.w)) * mix(0.05, 0.16, uStrength);
                float3 s = float3(0.0);
                for (int i = -3; i <= 3; i++) {
                    for (int j = -3; j <= 3; j++) {
                        s += tex(uv + float2(float(i), float(j)) / 3.0 * rad);
                    }
                }
                o = s / 49.0;
            }
            float a = float(orig.a);
            float3 base = a > 0.0 ? float3(orig.rgb) / a : float3(0.0);
            return half4(half3(mix(base, o, m) * a), half(a));
        }
    """
}
