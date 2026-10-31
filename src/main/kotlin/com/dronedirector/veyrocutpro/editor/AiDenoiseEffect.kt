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

/** Export: AI noise reduction on every frame (read back, denoise with the AI model, upload, blend). */
@UnstableApi
class AiDenoiseEffect(private val context: Context, private val strength: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = AiDenoiseProgram(this.context, strength)
    override fun isNoOp(inputWidth: Int, inputHeight: Int) = strength <= 0f
}

@UnstableApi
private class AiDenoiseProgram(private val context: Context, private val strength: Float) : BaseGlShaderProgram(false, 1) {
    private val program = try {
        GlProgram(
            """
            attribute vec4 aFramePosition;
            varying vec2 vUv;
            void main() { gl_Position = aFramePosition; vUv = aFramePosition.xy * 0.5 + 0.5; }
            """,
            """
            precision mediump float;
            uniform sampler2D uOrig;
            uniform sampler2D uDen;
            uniform float uStrength;
            varying vec2 vUv;
            void main() { gl_FragColor = mix(texture2D(uOrig, vUv), texture2D(uDen, vUv), uStrength); }
            """,
        )
    } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private var w = 0; private var h = 0
    private var denTex = 0
    private var buf: ByteBuffer? = null
    private var ok = true

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        w = inputWidth; h = inputHeight
        buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        if (denTex != 0) GLES20.glDeleteTextures(1, intArrayOf(denTex), 0)
        denTex = GlUtil.createTexture(w, h, false)
        return Size(w, h)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            val b = buf!!
            if (ok) {
                // Read the frame.
                val prev = IntArray(1)
                GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, prev, 0)
                val fbo = GlUtil.createFboForTexture(inputTexId)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
                b.rewind()
                GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, b)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prev[0])
                GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
                // RGBA bytes → ARGB ints → AI → back.
                b.rewind()
                val px = IntArray(w * h)
                for (i in px.indices) {
                    val r = b.get().toInt() and 0xFF; val g = b.get().toInt() and 0xFF; val bl = b.get().toInt() and 0xFF; b.get()
                    px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
                }
                val den = AiDenoiser.denoise(context, px, w, h, showProgress = false)
                if (den == null) ok = false else {
                    b.rewind()
                    for (c in den) { b.put(((c shr 16) and 0xFF).toByte()); b.put(((c shr 8) and 0xFF).toByte()); b.put((c and 0xFF).toByte()); b.put(0xFF.toByte()) }
                    b.rewind()
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, denTex)
                    GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, b)
                }
            }
            program.use()
            program.setSamplerTexIdUniform("uOrig", inputTexId, 0)
            program.setSamplerTexIdUniform("uDen", if (ok) denTex else inputTexId, 1)
            program.setFloatUniform("uStrength", if (ok) strength else 0f)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun release() {
        super.release()
        try {
            program.delete()
            if (denTex != 0) GLES20.glDeleteTextures(1, intArrayOf(denTex), 0)
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }
}
