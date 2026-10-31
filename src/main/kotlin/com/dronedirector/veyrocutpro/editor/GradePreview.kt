package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import java.util.WeakHashMap

/**
 * Live preview of the colour grade + detail on the player's view itself (Android 13+), so the
 * video player only has to decode plain video. The export still uses the full Media3 pipeline.
 */
object GradePreview {
    val supported: Boolean get() = Build.VERSION.SDK_INT >= 33

    private const val N = 33
    private class ViewState(val shader: Any, var key: Any? = null, var extra: Boolean = false)
    private val states = WeakHashMap<View, ViewState>()
    private val lutBitmaps = object : LinkedHashMap<ProGrade, Bitmap>(6, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ProGrade, Bitmap>?) = size > 6
    }

    /** True when the grade/detail needs the frame-by-frame time (film grain). */
    fun animated(c: Clip) = c.detail.grain > 0f

    fun lut(g: ProGrade): Bitmap = synchronized(lutBitmaps) {
        lutBitmaps.getOrPut(g) {
            val cube = LutCache.cube(g)
            val bmp = Bitmap.createBitmap(N * N, N, Bitmap.Config.ARGB_8888)
            val px = IntArray(N * N * N)
            // x = b * N + r, y = g
            for (r in 0 until N) for (gg in 0 until N) for (b in 0 until N) px[gg * N * N + b * N + r] = cube[r][gg][b]
            bmp.setPixels(px, 0, N * N, 0, 0, N * N, N)
            bmp
        }
    }

    /** False when this phone couldn't build the shader with masks; the plain one is used then. */
    @Volatile private var masksOk = true

    private fun newShader(): RuntimeShader {
        if (masksOk) {
            try { return RuntimeShader(AGSL_MASKED) } catch (t: Throwable) {
                masksOk = false
                android.util.Log.w("GradePreview", "Mask shader unavailable: ${t.message}")
            }
        }
        return RuntimeShader(AGSL_PLAIN)
    }

    /** Before / after split position (0 = off, else 0..1 across the frame). */
    @Volatile var split: Float = 0f

    /** [extra]: a further effect drawn on top of the grade (the background cut-out). */
    fun apply(view: View, c: Clip?, timeSec: Float = 0f, extra: RenderEffect? = null) {
        if (!supported) return
        val masks0 = c?.activeMasks() ?: emptyList()
        if (c == null || (c.grade.isIdentity && !c.detail.needsVideoPass && masks0.isEmpty())) {
            if (extra != null) {
                view.setRenderEffect(extra)
                states.getOrPut(view) { ViewState(newShader()) }.apply { key = null; this.extra = true }
            } else {
                val st = states[view]
                if (st?.extra == true) { view.setRenderEffect(null); st.extra = false }
                clear(view)
            }
            return
        }
        if (view.width <= 0 || view.height <= 0) return
        val st = states.getOrPut(view) { ViewState(newShader()) }
        val sh = st.shader as RuntimeShader
        val masks = if (masksOk) masks0 else emptyList()
        val key = Triple(c.grade, c.detail, Triple(view.width, view.height, masks to split))
        if (st.key == key && !animated(c) && extra == null && !st.extra) return
        st.key = key
        st.extra = extra != null
        sh.setFloatUniform("uSize", view.width.toFloat(), view.height.toFloat())
        val hasLut = !c.grade.isIdentity
        if (hasLut) {
            sh.setInputShader("lut", BitmapShader(lut(c.grade), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            })
        } else {
            sh.setInputShader("lut", BitmapShader(lut(ProGrade()), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        }
        val d = c.detail
        sh.setFloatUniform("uN", N.toFloat())
        sh.setFloatUniform("uHasLut", if (hasLut) 1f else 0f)
        sh.setFloatUniform("uSharpen", d.sharpen)
        // Texture, clarity and image detail are drawn here (live while dragging, like the grade).
        sh.setFloatUniform("uDetail", d.localDetail)
        sh.setFloatUniform("uClarity", d.clarity)
        sh.setFloatUniform("uTexture", d.texture)
        sh.setFloatUniform("uDenoise", d.denoise)
        sh.setFloatUniform("uVignette", d.vignette)
        sh.setFloatUniform("uGrain", d.grain)
        sh.setFloatUniform("uTime", timeSec % 10f)
        sh.setFloatUniform("uFx", d.fx.ordinal.toFloat())
        sh.setFloatUniform("uFxAmt", d.fxAmount)
        sh.setFloatUniform("uSplit", split)
        // Masks: each with its own grade, blended in where the mask is.
        if (masksOk) sh.setFloatUniform("uMCount", masks.size.toFloat())
        if (masksOk) for (k in 0 until MAX_VIDEO_MASKS) {
            val m = masks.getOrNull(k)
            val g = m?.let { MaskUniforms.geo(it) } ?: FloatArray(4)
            val o = m?.let { MaskUniforms.opt(it) } ?: floatArrayOf(0f, 0.5f, 0f, 0f)
            sh.setFloatUniform("uG$k", g[0], g[1], g[2], g[3])
            sh.setFloatUniform("uO$k", o[0], o[1], o[2], o[3])
            sh.setInputShader("mlut$k", BitmapShader(lut(m?.grade ?: ProGrade()), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            })
        }
        val g = RenderEffect.createRuntimeShaderEffect(sh, "content")
        view.setRenderEffect(if (extra != null) RenderEffect.createChainEffect(extra, g) else g)
    }

    fun clear(view: View) {
        if (!supported) return
        val st = states[view]
        if (st?.key != null || st == null) view.setRenderEffect(null)
        st?.key = null
    }

    private const val MLUT0 = """
        float3 mlutApply0(float3 c) {
            float n = uN;
            float3 s = clamp(c, 0.0, 1.0) * (n - 1.0);
            float b0 = floor(s.b);
            float b1 = min(b0 + 1.0, n - 1.0);
            float f = s.b - b0;
            float2 p = float2(s.r + 0.5, s.g + 0.5);
            float3 c0 = float3(mlut0.eval(float2(b0 * n + p.x, p.y)).rgb);
            float3 c1 = float3(mlut0.eval(float2(b1 * n + p.x, p.y)).rgb);
            return mix(c0, c1, f);
        }
    """
    private const val MLUT1 = """
        float3 mlutApply1(float3 c) {
            float n = uN;
            float3 s = clamp(c, 0.0, 1.0) * (n - 1.0);
            float b0 = floor(s.b);
            float b1 = min(b0 + 1.0, n - 1.0);
            float f = s.b - b0;
            float2 p = float2(s.r + 0.5, s.g + 0.5);
            float3 c0 = float3(mlut1.eval(float2(b0 * n + p.x, p.y)).rgb);
            float3 c1 = float3(mlut1.eval(float2(b1 * n + p.x, p.y)).rgb);
            return mix(c0, c1, f);
        }
    """
    private const val MLUT2 = """
        float3 mlutApply2(float3 c) {
            float n = uN;
            float3 s = clamp(c, 0.0, 1.0) * (n - 1.0);
            float b0 = floor(s.b);
            float b1 = min(b0 + 1.0, n - 1.0);
            float f = s.b - b0;
            float2 p = float2(s.r + 0.5, s.g + 0.5);
            float3 c0 = float3(mlut2.eval(float2(b0 * n + p.x, p.y)).rgb);
            float3 c1 = float3(mlut2.eval(float2(b1 * n + p.x, p.y)).rgb);
            return mix(c0, c1, f);
        }
    """

    private const val HEAD = """
        uniform shader content;
        uniform shader lut;
        uniform float2 uSize;
        uniform float uN;
        uniform float uHasLut;
        uniform float uSharpen;
        uniform float uDetail;
        uniform float uClarity;
        uniform float uTexture;
        uniform float uDenoise;
        uniform float uVignette;
        uniform float uGrain;
        uniform float uTime;
        uniform float uFx;
        uniform float uFxAmt;
        uniform float uSplit;
"""
    private const val MASK_DECL = """
        uniform shader mlut0;
        uniform shader mlut1;
        uniform shader mlut2;
        uniform float uMCount;
        uniform float4 uG0; uniform float4 uG1; uniform float4 uG2;
        uniform float4 uO0; uniform float4 uO1; uniform float4 uO2;
"""
    private const val BODY = """
        bool fxIs(float id) { return abs(uFx - id) < 0.5; }
        float rnd(float2 c) { return fract(sin(dot(c, float2(12.9898, 78.233)) + uTime * 7.0) * 43758.5453); }
        float3 px(float2 p) {
            half4 c = content.eval(clamp(p, float2(0.5), uSize - 0.5));
            return c.a > 0.0 ? float3(c.rgb) / float(c.a) : float3(0.0);
        }
        // Soft blur of radius r (pixels): two rings of 8 samples and the centre.
        float3 blurAt(float2 p, float r) {
            float3 s = px(p);
            for (int k = 0; k < 8; k++) {
                float a = float(k) * 0.7854;
                float2 o = float2(cos(a), sin(a)) * r;
                s += px(p + o) + px(p + o * 0.5);
            }
            return s / 17.0;
        }
        float3 applyLut(float3 c) {
            float n = uN;
            float3 s = clamp(c, 0.0, 1.0) * (n - 1.0);
            float b0 = floor(s.b);
            float b1 = min(b0 + 1.0, n - 1.0);
            float f = s.b - b0;
            float2 p = float2(s.r + 0.5, s.g + 0.5);
            float3 c0 = float3(lut.eval(float2(b0 * n + p.x, p.y)).rgb);
            float3 c1 = float3(lut.eval(float2(b1 * n + p.x, p.y)).rgb);
            return mix(c0, c1, f);
        }
        half4 main(float2 fc) {
            float2 uv = fc / uSize;
            float aspect = uSize.x / uSize.y;
            float2 sc = fc;
            if (fxIs(5.0) && uv.x > 0.5) sc.x = uSize.x - fc.x;
            if (fxIs(4.0)) {
                float cells = mix(160.0, 24.0, uFxAmt);
                float2 cell = float2(cells * aspect, cells);
                sc = (floor(uv * cell) + 0.5) / cell * uSize;
            }
            half4 orig = content.eval(fc);
            float3 rgb = px(sc);
            if (fxIs(6.0)) {
                float o = 6.0 * uFxAmt;
                rgb = float3(px(sc + float2(o, 0.0)).r, rgb.g, px(sc - float2(o, 0.0)).b);
            }
            if (uSharpen > 0.0 || uDenoise > 0.0 || fxIs(2.0)) {
                float r = fxIs(2.0) ? 2.0 + 8.0 * uFxAmt : 1.5;
                float3 blur = (px(sc + float2(r, 0.0)) + px(sc - float2(r, 0.0)) + px(sc + float2(0.0, r)) + px(sc - float2(0.0, r))) * 0.25;
                if (fxIs(2.0)) rgb = mix(rgb, blur, 0.85);
                if (uDenoise > 0.0) rgb = mix(rgb, blur, uDenoise * 0.7);
                if (uSharpen > 0.0) rgb = rgb + (rgb - blur) * uSharpen * 1.5;
            }
            // Image detail, texture and clarity (as in the export), sized to the frame like the photo editor.
            float longPx = max(uSize.x, uSize.y);
            if (uTexture != 0.0) {
                float3 fine = blurAt(sc, max(1.5, longPx * 0.0037));
                rgb += (rgb - fine) * uTexture * 1.2;
            }
            if (uDetail != 0.0 || uClarity != 0.0) {
                float l = dot(rgb, float3(0.299, 0.587, 0.114));
                if (uDetail != 0.0) {
                    float3 wide = blurAt(sc, max(2.0, longPx * 0.0031));
                    rgb += (rgb - wide) * uDetail * 1.2 * (1.0 - abs(l - 0.5) * 2.0);
                }
                if (uClarity != 0.0) {
                    float3 broad = blurAt(sc, longPx * 0.0167);
                    float mid = clamp(1.0 - abs(l - 0.5) * 2.0, 0.0, 1.0);
                    rgb += (rgb - broad) * uClarity * 1.1 * mid;
                }
            }
            if (uHasLut > 0.5) rgb = applyLut(rgb);
/*MASKS*/
            if (fxIs(1.0)) { float l = dot(rgb, float3(0.299, 0.587, 0.114)); rgb = mix(rgb, float3(l), uFxAmt); }
            if (fxIs(8.0)) { float lv = mix(12.0, 3.0, uFxAmt); rgb = floor(rgb * lv + 0.5) / lv; }
            if (fxIs(9.0)) rgb = mix(rgb, 1.0 - rgb, uFxAmt);
            if (uVignette != 0.0) {
                float d = length((uv - 0.5) * float2(aspect, 1.0)) / length(float2(aspect * 0.5, 0.5));
                rgb *= 1.0 + uVignette * smoothstep(0.35, 1.0, d);
            }
            if (uGrain > 0.0) rgb += (rnd(fc) - 0.5) * uGrain * 0.22;
            rgb = clamp(rgb, 0.0, 1.0);
            float a = float(orig.a);
            // Before / after: the original on the left of the divider.
            if (uSplit > 0.0) {
                float sx = uSplit * uSize.x;
                if (fc.x < sx) rgb = px(fc);
                if (abs(fc.x - sx) < 1.5) rgb = float3(1.0, 0.75, 0.2);
            }
            return half4(half3(rgb * a), half(a));
        }
    """
    private const val MASK_APPLY = """
            if (uMCount > 0.5) {
                float3 base = rgb;
                if (uMCount > 0.5) rgb = mix(rgb, mlutApply0(rgb), clamp(mWeight(uG0, uO0, uv, uSize, base), 0.0, 1.0));
                if (uMCount > 1.5) rgb = mix(rgb, mlutApply1(rgb), clamp(mWeight(uG1, uO1, uv, uSize, base), 0.0, 1.0));
                if (uMCount > 2.5) rgb = mix(rgb, mlutApply2(rgb), clamp(mWeight(uG2, uO2, uv, uSize, base), 0.0, 1.0));
            }
"""
    /** AGSL is SkSL: use its float2 / float3 / float4 names for the shared GLSL maths. */
    private fun sksl(s: String) = s.replace("vec4", "float4").replace("vec3", "float3").replace("vec2", "float2")
    private val AGSL_MASKED: String by lazy {
        HEAD + MASK_DECL + sksl(MaskUniforms.WEIGHT_GLSL) + MLUT0 + MLUT1 + MLUT2 + BODY.replace("/*MASKS*/", MASK_APPLY)
    }
    private val AGSL_PLAIN: String by lazy { HEAD + BODY.replace("/*MASKS*/", "") }
}
