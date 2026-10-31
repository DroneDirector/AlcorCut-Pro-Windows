package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Background removal (Color → Detail → BACKGROUND, or a video layer's REMOVE BACKGROUND).
 * People: ML Kit selfie segmentation on every frame. Magic Mask: any object picked by you and
 * tracked through the clip ([MagicSel], MobileSAM). Edges are refined against the full-size picture
 * (joint bilateral upsampling), so hair and outlines follow the real edges.
 * [mode] 0 = transparent (layers below show through), 1 = blurred background, 2 = a colour.
 * [invert]: affect the selected object instead of its surroundings (e.g. blur a number plate).
 */
data class BgRemoval(
    val mode: Int = 1,
    val color: Int = 0xFF00B140.toInt(),
    /** Magic Mask selection (any object); null = people, found automatically. */
    val magic: MagicSel? = null,
    val invert: Boolean? = null,
)

@UnstableApi
class BgRemoveEffect(
    private val mode: Int,
    private val color: Int = 0,
    private val invert: Boolean = false,
    /** Tracked Magic Mask, and how to turn frame time into the clip's source time. */
    private val track: MaskTrack? = null,
    private val srcAt: ((Long) -> Long)? = null,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = BgRemoveProgram(mode, color, invert, track, srcAt, useHdr)
}

/** The background effect for a clip (people, or its tracked Magic Mask starting [offsetMs] into the clip). */
@UnstableApi
fun bgEffectFor(context: Context, c: Clip, offsetMs: Long = 0L): BgRemoveEffect? {
    val bg = c.bg ?: return null
    val m = bg.magic
    if (m == null) return BgRemoveEffect(bg.mode, bg.color, bg.invert == true)
    val t = MaskTrack.load(context, m.id) ?: return null
    return BgRemoveEffect(bg.mode, bg.color, bg.invert == true, t) { relMs -> c.srcAtOffset(offsetMs + relMs) }
}

@UnstableApi
private class BgRemoveProgram(
    private val mode: Int, private val color: Int, private val invert: Boolean,
    private val track: MaskTrack?, private val srcAt: ((Long) -> Long)?,
    useHdr: Boolean,
) : BaseGlShaderProgram(useHdr, 1) {
    private val program = try { GlProgram(VERT, FRAG) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private val copy = if (track == null) try { GlProgram(VERT, COPY) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) } else null
    private var w = 1; private var h = 1
    private var sw = 256; private var sh = 144
    private var fbo = 0; private var smallTex = 0; private var maskTex = 0
    private var read: ByteBuffer? = null
    private var maskBytes: ByteBuffer? = null
    private var maskW = 0; private var maskH = 0
    private var t0 = Long.MIN_VALUE
    private var rect = floatArrayOf(0f, 0f, 1f, 1f)
    private var lastEntry: MaskEntry? = null
    private val segmenter: Segmenter? = if (track == null) Segmentation.getClient(
        SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.STREAM_MODE).enableRawSizeMask().build()
    ) else null

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        copy?.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        w = inputWidth; h = inputHeight
        if (w >= h) { sw = 256; sh = (256f * h / w).toInt().coerceAtLeast(16) } else { sh = 256; sw = (256f * w / h).toInt().coerceAtLeast(16) }
        return Size(inputWidth, inputHeight)
    }

    /** Uploads an 8-bit mask (picture orientation: row 0 = top). */
    private fun uploadMask(bytes: ByteBuffer, mw: Int, mh: Int) {
        if (maskTex == 0 || maskW != mw || maskH != mh) {
            if (maskTex != 0) try { GlUtil.deleteTexture(maskTex) } catch (_: Throwable) {}
            val t = IntArray(1); GLES20.glGenTextures(1, t, 0); maskTex = t[0]
            maskW = mw; maskH = mh
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTex)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, mw, mh, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, bytes)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    /** People: a small copy of the frame through the segmenter. */
    private fun segment(texId: Int) {
        val sp = copy ?: return
        val seg = segmenter ?: return
        val prevFbo = IntArray(1); GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, prevFbo, 0)
        val vp = IntArray(4); GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0)
        if (fbo == 0) { smallTex = GlUtil.createTexture(sw, sh, false); fbo = GlUtil.createFboForTexture(smallTex) }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glViewport(0, 0, sw, sh)
        sp.use()
        sp.setSamplerTexIdUniform("uTex", texId, 0)
        sp.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        val buf = read ?: ByteBuffer.allocateDirect(sw * sh * 4).order(ByteOrder.nativeOrder()).also { read = it }
        buf.clear()
        GLES20.glReadPixels(0, 0, sw, sh, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFbo[0])
        GLES20.glViewport(vp[0], vp[1], vp[2], vp[3])
        buf.rewind()
        val raw = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        raw.copyPixelsFromBuffer(buf)
        // GL rows run bottom-up: flip so people stand upright for the model.
        val bmp = Bitmap.createBitmap(raw, 0, 0, sw, sh, Matrix().apply { preScale(1f, -1f) }, false)
        raw.recycle()
        val m = try { Tasks.await(seg.process(InputImage.fromBitmap(bmp, 0))) } catch (_: Throwable) { null }
        bmp.recycle()
        if (m == null) return
        val fb = m.buffer.order(ByteOrder.nativeOrder()); fb.rewind()
        val mw = m.width; val mh = m.height
        val bytes = maskBytes?.takeIf { it.capacity() == mw * mh } ?: ByteBuffer.allocateDirect(mw * mh).also { maskBytes = it }
        bytes.clear()
        for (i in 0 until mw * mh) bytes.put(((if (fb.remaining() >= 4) fb.float else 0f).coerceIn(0f, 1f) * 255f).toInt().toByte())
        bytes.rewind()
        uploadMask(bytes, mw, mh)
    }

    /** Magic Mask: the tracked outline at this frame's source time. */
    private fun tracked(presentationTimeUs: Long) {
        val t = track ?: return
        if (t0 == Long.MIN_VALUE) t0 = presentationTimeUs
        val src = srcAt?.invoke((presentationTimeUs - t0) / 1000) ?: ((presentationTimeUs - t0) / 1000)
        val e = t.at(src)
        if (e === lastEntry && maskTex != 0) return
        lastEntry = e
        // Not in the picture: an all-clear mask.
        val w = if (e.empty) 1 else e.w; val h = if (e.empty) 1 else e.h
        val bytes = maskBytes?.takeIf { it.capacity() >= w * h } ?: ByteBuffer.allocateDirect(max(w * h, 1)).also { maskBytes = it }
        bytes.clear(); if (e.empty) bytes.put(0.toByte()) else bytes.put(e.bytes); bytes.rewind()
        rect = if (e.empty) floatArrayOf(0f, 0f, 1f, 1f) else e.rect
        uploadMask(bytes, w, h)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            try { if (track != null) tracked(presentationTimeUs) else segment(inputTexId) } catch (_: Throwable) {}
            program.use()
            program.setSamplerTexIdUniform("uTex", inputTexId, 0)
            // No mask yet: keep the whole picture.
            program.setSamplerTexIdUniform("uMask", if (maskTex != 0) maskTex else inputTexId, 1)
            program.setFloatUniform("uHasMask", if (maskTex != 0) 1f else 0f)
            program.setFloatUniform("uMode", mode.toFloat())
            program.setFloatUniform("uInvert", if (invert) 1f else 0f)
            program.setFloatsUniform("uColor", floatArrayOf(((color shr 16) and 255) / 255f, ((color shr 8) and 255) / 255f, (color and 255) / 255f))
            program.setFloatsUniform("uTexel", floatArrayOf(1f / w, 1f / h))
            program.setFloatsUniform("uMaskSize", floatArrayOf(maskW.coerceAtLeast(1).toFloat(), maskH.coerceAtLeast(1).toFloat()))
            program.setFloatsUniform("uRect", rect)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    override fun release() {
        super.release()
        try { segmenter?.close() } catch (_: Throwable) {}
        try {
            program.delete(); copy?.delete()
            if (fbo != 0) GlUtil.deleteFbo(fbo)
            if (smallTex != 0) GlUtil.deleteTexture(smallTex)
            if (maskTex != 0) GlUtil.deleteTexture(maskTex)
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    companion object {
        private const val VERT = """
            attribute vec4 aFramePosition;
            varying vec2 vUv;
            void main() { gl_Position = aFramePosition; vUv = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5); }
        """
        private const val COPY = """
            precision mediump float;
            uniform sampler2D uTex;
            varying vec2 vUv;
            void main() { gl_FragColor = vec4(texture2D(uTex, vUv).rgb, 1.0); }
        """
        private const val FRAG = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform sampler2D uTex;
            uniform sampler2D uMask;
            uniform float uHasMask;
            uniform float uMode;
            uniform float uInvert;
            uniform vec3 uColor;
            uniform vec2 uTexel;
            uniform vec2 uMaskSize;
            uniform vec4 uRect;
            varying vec2 vUv;
            // Joint bilateral upsampling: the mask's own pixels, weighted by how alike their colour is to this pixel.
            // The mask covers uRect of the picture (a close-up round the object); outside it is empty.
            float maskAt(vec2 uv, vec3 c) {
                vec2 iuv = vec2(uv.x, 1.0 - uv.y);
                vec2 rs = uRect.zw - uRect.xy;
                vec2 muv = (iuv - uRect.xy) / rs;
                if (muv.x < 0.0 || muv.y < 0.0 || muv.x > 1.0 || muv.y > 1.0) return 0.0;
                vec2 p = muv * uMaskSize - 0.5;
                vec2 b = floor(p);
                float s = 0.0; float ws = 0.0;
                for (int j = -1; j <= 2; j++) {
                    for (int i = -1; i <= 2; i++) {
                        vec2 q = b + vec2(float(i), float(j));
                        vec2 qu = (clamp(q, vec2(0.0), uMaskSize - 1.0) + 0.5) / uMaskSize;
                        vec2 qi = uRect.xy + qu * rs;
                        vec3 qc = texture2D(uTex, vec2(qi.x, 1.0 - qi.y)).rgb;
                        vec2 d = q - p;
                        vec3 dc = qc - c;
                        float wgt = exp(-dot(d, d) * 0.5 - dot(dc, dc) * 40.0);
                        s += wgt * texture2D(uMask, qu).r; ws += wgt;
                    }
                }
                return s / max(ws, 1e-5);
            }
            void main() {
                vec4 c = texture2D(uTex, vUv);
                float m = uHasMask > 0.5 ? smoothstep(0.25, 0.75, maskAt(vUv, c.rgb)) : 1.0;
                if (uInvert > 0.5) m = 1.0 - m;
                if (uMode < 0.5) { gl_FragColor = vec4(c.rgb, c.a * m); return; }
                vec3 bg;
                if (uMode < 1.5) {
                    vec3 s = vec3(0.0);
                    for (int i = 0; i < 16; i++) {
                        float a = float(i) * 0.3927;
                        float r = (i < 8) ? 14.0 : 28.0;
                        s += texture2D(uTex, vUv + vec2(cos(a), sin(a)) * vec2(uTexel.x / uTexel.y, 1.0) * r / 720.0).rgb;
                    }
                    bg = s / 16.0;
                } else bg = uColor;
                gl_FragColor = vec4(mix(bg, c.rgb, m), c.a);
            }
        """
    }
}
