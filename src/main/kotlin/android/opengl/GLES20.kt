package android.opengl

import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer
import org.lwjgl.opengles.GLES20 as G

/**
 * OpenGL ES 2.0 with the phone's call shapes (arrays with offsets, any buffer), on the app's graphics
 * engine ([com.dronedirector.veyrocutpro.windows.GlEnv]). Call on the GL thread. Own code.
 */
object GLES20 {
    const val GL_TEXTURE_2D = 0x0DE1; const val GL_TEXTURE0 = 0x84C0; const val GL_TEXTURE1 = 0x84C1; const val GL_TEXTURE2 = 0x84C2; const val GL_TEXTURE3 = 0x84C3
    const val GL_TEXTURE_MIN_FILTER = 0x2801; const val GL_TEXTURE_MAG_FILTER = 0x2800; const val GL_TEXTURE_WRAP_S = 0x2802; const val GL_TEXTURE_WRAP_T = 0x2803
    const val GL_LINEAR = 0x2601; const val GL_NEAREST = 0x2600; const val GL_CLAMP_TO_EDGE = 0x812F; const val GL_REPEAT = 0x2901; const val GL_LINEAR_MIPMAP_LINEAR = 0x2703
    const val GL_RGBA = 0x1908; const val GL_RGB = 0x1907; const val GL_LUMINANCE = 0x1909; const val GL_ALPHA = 0x1906; const val GL_LUMINANCE_ALPHA = 0x190A
    const val GL_UNSIGNED_BYTE = 0x1401; const val GL_FLOAT = 0x1406; const val GL_UNSIGNED_SHORT = 0x1403; const val GL_INT = 0x1404
    const val GL_FRAMEBUFFER = 0x8D40; const val GL_FRAMEBUFFER_BINDING = 0x8CA6; const val GL_COLOR_ATTACHMENT0 = 0x8CE0; const val GL_FRAMEBUFFER_COMPLETE = 0x8CD5
    const val GL_RENDERBUFFER = 0x8D41; const val GL_TEXTURE_BINDING_2D = 0x8069; const val GL_CURRENT_PROGRAM = 0x8B8D; const val GL_ACTIVE_TEXTURE = 0x84E0
    const val GL_VIEWPORT = 0x0BA2; const val GL_UNPACK_ALIGNMENT = 0x0CF5; const val GL_PACK_ALIGNMENT = 0x0D05
    const val GL_TRIANGLE_STRIP = 5; const val GL_TRIANGLES = 4; const val GL_TRIANGLE_FAN = 6; const val GL_LINES = 1; const val GL_POINTS = 0
    const val GL_COLOR_BUFFER_BIT = 0x4000; const val GL_DEPTH_BUFFER_BIT = 0x100
    const val GL_BLEND = 0x0BE2; const val GL_SRC_ALPHA = 0x0302; const val GL_ONE_MINUS_SRC_ALPHA = 0x0303; const val GL_ONE = 1; const val GL_ZERO = 0; const val GL_DEPTH_TEST = 0x0B71; const val GL_SCISSOR_TEST = 0x0C11
    const val GL_ARRAY_BUFFER = 0x8892; const val GL_STATIC_DRAW = 0x88E4
    const val GL_VERTEX_SHADER = 0x8B31; const val GL_FRAGMENT_SHADER = 0x8B30; const val GL_COMPILE_STATUS = 0x8B81; const val GL_LINK_STATUS = 0x8B82
    const val GL_NO_ERROR = 0; const val GL_MAX_TEXTURE_SIZE = 0x0D33; const val GL_TRUE = 1; const val GL_FALSE = 0

    /** A direct copy of [b] when it's on the Java heap (the graphics library needs direct memory). */
    private fun direct(b: Buffer?): ByteBuffer? {
        if (b == null) return null
        if (b.isDirect) return when (b) {
            is ByteBuffer -> b
            else -> org.lwjgl.system.MemoryUtil.memByteBuffer(org.lwjgl.system.MemoryUtil.memAddress(b), b.remaining() * elemSize(b))
        }
        val n = b.remaining() * elemSize(b)
        val d = ByteBuffer.allocateDirect(n).order(ByteOrder.nativeOrder())
        when (b) {
            is ByteBuffer -> d.put(b.duplicate())
            is IntBuffer -> d.asIntBuffer().put(b.duplicate())
            is FloatBuffer -> d.asFloatBuffer().put(b.duplicate())
            is ShortBuffer -> d.asShortBuffer().put(b.duplicate())
            else -> throw IllegalArgumentException("Unsupported buffer")
        }
        d.position(0); d.limit(n)
        return d
    }
    private fun elemSize(b: Buffer) = when (b) { is IntBuffer, is FloatBuffer -> 4; is ShortBuffer -> 2; else -> 1 }

    @JvmStatic fun glGetError(): Int = G.glGetError()
    @JvmStatic fun glGenTextures(n: Int, t: IntArray, off: Int) { val a = IntArray(n); G.glGenTextures(a); System.arraycopy(a, 0, t, off, n) }
    @JvmStatic fun glDeleteTextures(n: Int, t: IntArray, off: Int) { G.glDeleteTextures(t.copyOfRange(off, off + n)) }
    @JvmStatic fun glGenFramebuffers(n: Int, t: IntArray, off: Int) { val a = IntArray(n); G.glGenFramebuffers(a); System.arraycopy(a, 0, t, off, n) }
    @JvmStatic fun glDeleteFramebuffers(n: Int, t: IntArray, off: Int) { G.glDeleteFramebuffers(t.copyOfRange(off, off + n)) }
    @JvmStatic fun glBindTexture(target: Int, t: Int) = G.glBindTexture(target, t)
    @JvmStatic fun glActiveTexture(u: Int) = G.glActiveTexture(u)
    @JvmStatic fun glTexParameteri(target: Int, p: Int, v: Int) = G.glTexParameteri(target, p, v)
    @JvmStatic fun glTexParameterf(target: Int, p: Int, v: Float) = G.glTexParameterf(target, p, v)
    @JvmStatic fun glPixelStorei(p: Int, v: Int) = G.glPixelStorei(p, v)
    @JvmStatic fun glTexImage2D(target: Int, level: Int, ifmt: Int, w: Int, h: Int, border: Int, fmt: Int, type: Int, data: Buffer?) =
        G.glTexImage2D(target, level, ifmt, w, h, border, fmt, type, direct(data))
    @JvmStatic fun glTexSubImage2D(target: Int, level: Int, x: Int, y: Int, w: Int, h: Int, fmt: Int, type: Int, data: Buffer) =
        G.glTexSubImage2D(target, level, x, y, w, h, fmt, type, direct(data)!!)
    @JvmStatic fun glBindFramebuffer(target: Int, f: Int) = G.glBindFramebuffer(target, f)
    @JvmStatic fun glFramebufferTexture2D(target: Int, att: Int, texTarget: Int, tex: Int, level: Int) = G.glFramebufferTexture2D(target, att, texTarget, tex, level)
    @JvmStatic fun glCheckFramebufferStatus(target: Int): Int = G.glCheckFramebufferStatus(target)
    @JvmStatic fun glViewport(x: Int, y: Int, w: Int, h: Int) = G.glViewport(x, y, w, h)
    @JvmStatic fun glGetIntegerv(p: Int, out: IntArray, off: Int) { val a = IntArray(16); G.glGetIntegerv(p, a); val n = if (p == GL_VIEWPORT) 4 else 1; System.arraycopy(a, 0, out, off, minOf(n, out.size - off)) }
    @JvmStatic fun glReadPixels(x: Int, y: Int, w: Int, h: Int, fmt: Int, type: Int, out: Buffer) {
        if (out.isDirect) { G.glReadPixels(x, y, w, h, fmt, type, direct(out)!!); return }
        val d = ByteBuffer.allocateDirect(out.remaining() * elemSize(out)).order(ByteOrder.nativeOrder())
        G.glReadPixels(x, y, w, h, fmt, type, d)
        val p = out.position()
        when (out) {
            is ByteBuffer -> out.duplicate().put(d)
            is IntBuffer -> out.duplicate().put(d.asIntBuffer())
            is FloatBuffer -> out.duplicate().put(d.asFloatBuffer())
            else -> {}
        }
        out.position(p)
    }
    @JvmStatic fun glDrawArrays(mode: Int, first: Int, count: Int) = G.glDrawArrays(mode, first, count)
    @JvmStatic fun glClearColor(r: Float, g: Float, b: Float, a: Float) = G.glClearColor(r, g, b, a)
    @JvmStatic fun glClear(mask: Int) = G.glClear(mask)
    @JvmStatic fun glEnable(c: Int) = G.glEnable(c)
    @JvmStatic fun glDisable(c: Int) = G.glDisable(c)
    @JvmStatic fun glBlendFunc(s: Int, d: Int) = G.glBlendFunc(s, d)
    @JvmStatic fun glUseProgram(p: Int) = G.glUseProgram(p)
    @JvmStatic fun glGetUniformLocation(p: Int, n: String) = G.glGetUniformLocation(p, n)
    @JvmStatic fun glGetAttribLocation(p: Int, n: String) = G.glGetAttribLocation(p, n)
    @JvmStatic fun glUniform1i(l: Int, v: Int) = G.glUniform1i(l, v)
    @JvmStatic fun glUniform1f(l: Int, v: Float) = G.glUniform1f(l, v)
    @JvmStatic fun glUniform2f(l: Int, a: Float, b: Float) = G.glUniform2f(l, a, b)
    @JvmStatic fun glUniform3f(l: Int, a: Float, b: Float, c: Float) = G.glUniform3f(l, a, b, c)
    @JvmStatic fun glUniform4f(l: Int, a: Float, b: Float, c: Float, d: Float) = G.glUniform4f(l, a, b, c, d)
    @JvmStatic fun glUniform1fv(l: Int, n: Int, v: FloatArray, off: Int) = G.glUniform1fv(l, v.copyOfRange(off, off + n))
    @JvmStatic fun glUniformMatrix4fv(l: Int, n: Int, t: Boolean, v: FloatArray, off: Int) = G.glUniformMatrix4fv(l, t, v.copyOfRange(off, off + 16 * n))
    @JvmStatic fun glFinish() = G.glFinish()
    @JvmStatic fun glFlush() = G.glFlush()
}

object GLES30 {
    const val GL_RGBA16F = 0x881A; const val GL_HALF_FLOAT = 0x140B; const val GL_RGBA8 = 0x8058; const val GL_RED = 0x1903; const val GL_R8 = 0x8229
}

/** Uploading a Bitmap as a texture (RGBA, top row first as the phone does). */
object GLUtils {
    @JvmStatic fun texImage2D(target: Int, level: Int, bitmap: android.graphics.Bitmap, border: Int) {
        val w = bitmap.width; val h = bitmap.height
        val px = IntArray(w * h); bitmap.getPixels(px, 0, w, 0, 0, w, h)
        val b = rgba(px)
        G.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        G.glTexImage2D(target, level, GLES20.GL_RGBA, w, h, border, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, b)
    }
    @JvmStatic fun texImage2D(target: Int, level: Int, internalFormat: Int, bitmap: android.graphics.Bitmap, border: Int) = texImage2D(target, level, bitmap, border)
    @JvmStatic fun texSubImage2D(target: Int, level: Int, x: Int, y: Int, bitmap: android.graphics.Bitmap) {
        val w = bitmap.width; val h = bitmap.height
        val px = IntArray(w * h); bitmap.getPixels(px, 0, w, 0, 0, w, h)
        G.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        G.glTexSubImage2D(target, level, x, y, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, rgba(px))
    }
    /** Premultiplied RGBA, as a phone Bitmap holds it in memory (what the phone uploads). */
    internal fun rgba(px: IntArray): ByteBuffer {
        val b = ByteBuffer.allocateDirect(px.size * 4)
        for (c in px) {
            val a = c ushr 24
            if (a == 255) { b.put((c shr 16).toByte()); b.put((c shr 8).toByte()); b.put(c.toByte()); b.put(-1) }
            else { b.put(((c shr 16 and 0xFF) * a / 255).toByte()); b.put(((c shr 8 and 0xFF) * a / 255).toByte()); b.put(((c and 0xFF) * a / 255).toByte()); b.put(a.toByte()) }
        }
        b.flip(); return b
    }
}
