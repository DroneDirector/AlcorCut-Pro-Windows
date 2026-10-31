package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Shape (pen) masks for clips: a smooth closed curve
 * through the points you place, with a soft edge, its own grade inside (or outside), and optional
 * planar tracking so it stays on a moving object.
 */
object PenMask {
    /** The clip's pen masks that change the picture. */
    fun active(c: Clip): List<PhotoMask> =
        (c.masks ?: emptyList()).filter { it.type == MaskType.PEN && it.visible && !it.grade.isIdentity && it.amount > 0f && (it.pts?.size ?: 0) >= 6 }.take(MAX_VIDEO_MASKS)

    /** The points at source time [srcMs] (moved along the planar track, if there is one). */
    fun pointsAt(m: PhotoMask, srcMs: Long): FloatArray {
        val p = m.pts?.toFloatArray() ?: return FloatArray(0)
        val tr = m.ptrack; val ref = m.pref
        if (tr.isNullOrEmpty() || ref == null || ref.size != 8) return p
        val q = Layer(pin = tr).pinAt(srcMs) ?: return p
        val a = Homography.squareTo(ref.toFloatArray()) ?: return p
        val b = Homography.squareTo(q) ?: return p
        val h = Homography.mul(b, Homography.invert(a) ?: return p)
        val out = FloatArray(p.size)
        for (i in 0 until p.size / 2) { val (u, v) = Homography.apply(h, p[2 * i].toDouble(), p[2 * i + 1].toDouble()); out[2 * i] = u.toFloat(); out[2 * i + 1] = v.toFloat() }
        return out
    }

    /** Smooth closed curve through [p] (Catmull–Rom as cubic Béziers), scaled to w×h. */
    fun path(p: FloatArray, w: Float, h: Float): Path {
        val n = p.size / 2
        val path = Path()
        if (n < 2) return path
        fun x(i: Int) = p[2 * ((i % n + n) % n)] * w
        fun y(i: Int) = p[2 * ((i % n + n) % n) + 1] * h
        path.moveTo(x(0), y(0))
        for (i in 0 until n) {
            val c1x = x(i) + (x(i + 1) - x(i - 1)) / 6f; val c1y = y(i) + (y(i + 1) - y(i - 1)) / 6f
            val c2x = x(i + 1) - (x(i + 2) - x(i)) / 6f; val c2y = y(i + 1) - (y(i + 2) - y(i)) / 6f
            path.cubicTo(c1x, c1y, c2x, c2y, x(i + 1), y(i + 1))
        }
        path.close()
        return path
    }

    /** The mask as a grey picture (white inside), with a soft edge from [PhotoMask.feather]. */
    fun raster(m: PhotoMask, srcMs: Long, w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFF000000.toInt())
        val p = pointsAt(m, srcMs)
        if (p.size >= 6) {
            val r = m.feather.coerceIn(0f, 1f) * 0.06f * minOf(w, h)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt(); style = Paint.Style.FILL
                if (r >= 1f) maskFilter = BlurMaskFilter(r, BlurMaskFilter.Blur.NORMAL)
            }
            c.drawPath(path(p, w.toFloat(), h.toFloat()), paint)
        }
        return b
    }

    /** A first shape: a soft-cornered diamond in the middle of the picture. */
    fun starter(): List<Float> = listOf(0.5f, 0.28f, 0.68f, 0.5f, 0.5f, 0.72f, 0.32f, 0.5f)

    /** The bounding quad of the points (for planar tracking). */
    fun boundsQuad(p: FloatArray): FloatArray {
        var x0 = 1f; var y0 = 1f; var x1 = 0f; var y1 = 0f
        for (i in 0 until p.size / 2) { x0 = minOf(x0, p[2 * i]); x1 = maxOf(x1, p[2 * i]); y0 = minOf(y0, p[2 * i + 1]); y1 = maxOf(y1, p[2 * i + 1]) }
        return floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1)
    }

    // ---------------------------------------------------------------- live preview (Android 13+)

    private const val AGSL = """
        uniform shader content;
        uniform shader m0; uniform shader m1; uniform shader m2;
        uniform shader l0; uniform shader l1; uniform shader l2;
        uniform float2 uSize; uniform float2 uMSize;
        uniform float uN; uniform float uCount;
        uniform float4 uO0; uniform float4 uO1; uniform float4 uO2;
        float3 lutOf(shader lut, float3 c) {
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
        float wOf(float m, float4 o) { float w = m; if (o.z > 0.5) w = 1.0 - w; return clamp(w * o.w, 0.0, 1.0); }
        half4 main(float2 p) {
            half4 src = content.eval(p);
            float3 c = float3(src.rgb);
            float2 mp = p / uSize * uMSize;
            if (uCount > 0.5) c = mix(c, lutOf(l0, c), wOf(float(m0.eval(mp).r), uO0));
            if (uCount > 1.5) c = mix(c, lutOf(l1, c), wOf(float(m1.eval(mp).r), uO1));
            if (uCount > 2.5) c = mix(c, lutOf(l2, c), wOf(float(m2.eval(mp).r), uO2));
            return half4(half3(c), src.a);
        }
    """

    private val cache = java.util.WeakHashMap<View, Pair<Any, RenderEffect>>()

    /** The pen masks of [c] at source time [srcMs] for the player's view, or null. Call on the main thread. */
    fun preview(v: View, c: Clip, srcMs: Long): RenderEffect? {
        if (Build.VERSION.SDK_INT < 33 || v.width <= 0 || v.height <= 0) return null
        val ms = active(c)
        if (ms.isEmpty()) return null
        val tracked = ms.any { !it.ptrack.isNullOrEmpty() }
        val key = listOf(ms, v.width, v.height, if (tracked) srcMs / 33 else 0L)
        cache[v]?.let { if (it.first == key) return it.second }
        val mw = 320; val mh = (320f * v.height / v.width).toInt().coerceIn(16, 1024)
        val sh = try { RuntimeShader(AGSL) } catch (_: Throwable) { return null }
        val blank = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val lutBlank = GradePreview.lut(ProGrade())
        for (k in 0 until 3) {
            val m = ms.getOrNull(k)
            val mb = m?.let { raster(it, srcMs, mw, mh) } ?: blank
            sh.setInputShader("m$k", BitmapShader(mb, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR })
            sh.setInputShader("l$k", BitmapShader(m?.let { GradePreview.lut(it.grade) } ?: lutBlank, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR })
            sh.setFloatUniform("uO$k", 4f, 0f, if (m?.invert == true) 1f else 0f, m?.amount?.coerceIn(0f, 1f) ?: 0f)
        }
        sh.setFloatUniform("uSize", v.width.toFloat(), v.height.toFloat())
        sh.setFloatUniform("uMSize", mw.toFloat(), mh.toFloat())
        sh.setFloatUniform("uN", lutBlank.height.toFloat())
        sh.setFloatUniform("uCount", ms.size.toFloat())
        val fx = RenderEffect.createRuntimeShaderEffect(sh, "content")
        cache[v] = key to fx
        return fx
    }
}

/** Everything drawn on a clip's preview after its grade: background cut-out, then pen masks. */
object ClipPreviewFx {
    fun extra(context: Context, v: View, c: Clip, srcMs: Long): RenderEffect? {
        // Inner first: depth, then pen masks, then the background cut-out.
        val fx = listOfNotNull(
            DepthPreview.effect(context, v, c, srcMs), PenMask.preview(v, c, srcMs), CutoutPreview.forClip(context, v, c, srcMs),
            // Privacy blur, then the film look over everything (as in the export).
            PrivacyPreview.effect(v, c, srcMs), FilmPreview.effect(v, c, srcMs),
        )
        if (fx.isEmpty()) return null
        return fx.drop(1).fold(fx[0]) { inner, outer -> RenderEffect.createChainEffect(outer, inner) }
    }
}

// ---------------------------------------------------------------- export

/** Export: each pen mask's own grade blended in where the mask is. [offsetMs] = where this part of the clip starts (timeline, from the clip start). */
@OptIn(UnstableApi::class)
class PenMaskEffect(private val c: Clip, private val offsetMs: Long) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = PenMaskProgram(c, offsetMs, useHdr)
    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = PenMask.active(c).isEmpty()
}

@OptIn(UnstableApi::class)
private class PenMaskProgram(private val c: Clip, private val offsetMs: Long, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val masks = PenMask.active(c)
    private val program = try { GlProgram(VERT, FRAG) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private val lutTex = IntArray(3)
    private val maskTex = IntArray(3)
    private val tracked = masks.any { !it.ptrack.isNullOrEmpty() }
    private var drawnStatic = false
    private var t0 = Long.MIN_VALUE
    private var w = 1; private var h = 1

    init { program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE) }
    override fun configure(inputWidth: Int, inputHeight: Int): Size { w = inputWidth; h = inputHeight; return Size(inputWidth, inputHeight) }

    private fun params() {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun setup() {
        if (lutTex[0] != 0) return
        GLES20.glGenTextures(3, lutTex, 0); GLES20.glGenTextures(3, maskTex, 0)
        val n = MaskUniforms.LUT_N
        for (k in 0 until 3) {
            val strip = masks.getOrNull(k)?.let { MaskUniforms.strip(it.grade) } ?: IntArray(n * n * n)
            val buf = ByteBuffer.allocateDirect(strip.size * 4).order(ByteOrder.nativeOrder())
            for (p in strip) { buf.put(((p shr 16) and 0xFF).toByte()); buf.put(((p shr 8) and 0xFF).toByte()); buf.put((p and 0xFF).toByte()); buf.put(0xFF.toByte()) }
            buf.position(0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTex[k]); params()
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, n * n, n, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        }
    }

    private fun drawMasks(srcMs: Long) {
        val mw = minOf(w, 960); val mh = (mw.toFloat() * h / w).toInt().coerceAtLeast(16)
        for (k in 0 until 3) {
            val m = masks.getOrNull(k) ?: continue
            val b = PenMask.raster(m, srcMs, mw, mh)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTex[k]); params()
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
            b.recycle()
        }
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        if (t0 == Long.MIN_VALUE) t0 = presentationTimeUs
        val src = c.srcAtOffset(offsetMs + (presentationTimeUs - t0) / 1000)
        try {
            setup()
            if (tracked || !drawnStatic) { drawMasks(src); drawnStatic = true }
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatUniform("uN", MaskUniforms.LUT_N.toFloat())
            program.setFloatUniform("uCount", masks.size.toFloat())
            for (k in 0 until 3) {
                val m = masks.getOrNull(k)
                program.setSamplerTexIdUniform("uLut$k", lutTex[k], 1 + k)
                program.setSamplerTexIdUniform("uMask$k", maskTex[k], 4 + k)
                program.setFloatsUniform("uO$k", floatArrayOf(4f, 0f, if (m?.invert == true) 1f else 0f, m?.amount?.coerceIn(0f, 1f) ?: 0f))
            }
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    override fun release() {
        super.release()
        if (lutTex[0] != 0) { GLES20.glDeleteTextures(3, lutTex, 0); GLES20.glDeleteTextures(3, maskTex, 0) }
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
            uniform sampler2D uLut0; uniform sampler2D uLut1; uniform sampler2D uLut2;
            uniform sampler2D uMask0; uniform sampler2D uMask1; uniform sampler2D uMask2;
            uniform float uN;
            uniform float uCount;
            uniform vec4 uO0; uniform vec4 uO1; uniform vec4 uO2;
            varying vec2 vTexSamplingCoord;
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
            float wOf(float m, vec4 o) { float w = m; if (o.z > 0.5) w = 1.0 - w; return clamp(w * o.w, 0.0, 1.0); }
            void main() {
                vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
                vec3 c = src.rgb;
                // The mask pictures are top row first.
                vec2 uv = vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y);
                if (uCount > 0.5) c = mix(c, lutOf(uLut0, c), wOf(texture2D(uMask0, uv).r, uO0));
                if (uCount > 1.5) c = mix(c, lutOf(uLut1, c), wOf(texture2D(uMask1, uv).r, uO1));
                if (uCount > 2.5) c = mix(c, lutOf(uLut2, c), wOf(texture2D(uMask2, uv).r, uO2));
                gl_FragColor = vec4(c, src.a);
            }
        """
    }
}
