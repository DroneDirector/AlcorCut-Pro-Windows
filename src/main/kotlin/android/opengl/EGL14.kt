package android.opengl

import com.dronedirector.veyrocutpro.windows.GlEnv
import org.lwjgl.egl.EGL10
import org.lwjgl.opengles.GLES
import org.lwjgl.system.MemoryStack

class EGLDisplay internal constructor(val nativeHandle: Long)
class EGLConfig internal constructor(val nativeHandle: Long)
class EGLContext internal constructor(val nativeHandle: Long)
/** A drawing surface; for an encoder's input surface it's an off-screen buffer whose pictures go to the encoder on swap. */
class EGLSurface internal constructor(val nativeHandle: Long, internal val target: android.view.Surface?, internal val w: Int, internal val h: Int) {
    @Volatile internal var ptsNs = -1L
}

/**
 * EGL with the phone's call shapes, on the app's graphics engine (ANGLE). A window surface on an
 * encoder's input surface is an off-screen buffer: each swap reads the picture back and hands it to
 * the encoder with its presentation time. Own code.
 */
object EGL14 {
    const val EGL_DEFAULT_DISPLAY = 0
    @JvmField val EGL_NO_DISPLAY = EGLDisplay(0)
    @JvmField val EGL_NO_CONTEXT = EGLContext(0)
    @JvmField val EGL_NO_SURFACE = EGLSurface(0, null, 0, 0)
    const val EGL_NONE = 0x3038; const val EGL_RED_SIZE = 0x3024; const val EGL_GREEN_SIZE = 0x3023; const val EGL_BLUE_SIZE = 0x3022; const val EGL_ALPHA_SIZE = 0x3021
    const val EGL_RENDERABLE_TYPE = 0x3040; const val EGL_OPENGL_ES2_BIT = 4; const val EGL_SURFACE_TYPE = 0x3033; const val EGL_PBUFFER_BIT = 1; const val EGL_WINDOW_BIT = 4
    const val EGL_CONTEXT_CLIENT_VERSION = 0x3098; const val EGL_WIDTH = 0x3057; const val EGL_HEIGHT = 0x3056; const val EGL_SUCCESS = 0x3000
    const val EGL_DRAW = 0x3059; const val EGL_READ = 0x305A

    @JvmStatic fun eglGetDisplay(id: Int): EGLDisplay = if (GlEnv.ok) EGLDisplay(GlEnv.display) else EGL_NO_DISPLAY
    @JvmStatic fun eglInitialize(d: EGLDisplay, major: IntArray?, mo: Int, minor: IntArray?, mi: Int): Boolean {
        if (d.nativeHandle == 0L) return false
        major?.set(mo, 1); minor?.set(mi, 5); return true
    }
    @JvmStatic fun eglGetError(): Int = try { EGL10.eglGetError() } catch (_: Throwable) { EGL_SUCCESS }

    @JvmStatic fun eglChooseConfig(d: EGLDisplay, attrs: IntArray, off: Int, configs: Array<EGLConfig?>, co: Int, size: Int, num: IntArray, no: Int): Boolean = MemoryStack.stackPush().use { st ->
        // Only the attributes desktop EGL knows (the phone's recordable flag is left out), and off-screen buffers.
        val list = ArrayList<Int>()
        var i = off
        while (i < attrs.size && attrs[i] != EGL_NONE) {
            val k = attrs[i]; val v = attrs[i + 1]; i += 2
            if (k == 0x3142 || k == EGL_SURFACE_TYPE) continue
            list += k; list += v
        }
        list += EGL_SURFACE_TYPE; list += EGL_PBUFFER_BIT; list += EGL_NONE
        val out = st.mallocPointer(size.coerceAtLeast(1)); val n = st.mallocInt(1)
        val ok = EGL10.eglChooseConfig(d.nativeHandle, st.ints(*list.toIntArray()), out, n)
        num[no] = n[0]
        for (k in 0 until minOf(n[0], size)) configs[co + k] = EGLConfig(out[k])
        ok && n[0] > 0
    }
    @JvmStatic fun eglCreateContext(d: EGLDisplay, cfg: EGLConfig?, share: EGLContext, attrs: IntArray, off: Int): EGLContext = MemoryStack.stackPush().use { st ->
        EGLContext(EGL10.eglCreateContext(d.nativeHandle, cfg!!.nativeHandle, share.nativeHandle, st.ints(EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE)))
    }
    @JvmStatic fun eglCreateWindowSurface(d: EGLDisplay, cfg: EGLConfig?, win: Any?, attrs: IntArray, off: Int): EGLSurface = MemoryStack.stackPush().use { st ->
        val s = win as? android.view.Surface
        val w = s?.defaultWidth ?: 16; val h = s?.defaultHeight ?: 16
        EGLSurface(EGL10.eglCreatePbufferSurface(d.nativeHandle, cfg!!.nativeHandle, st.ints(EGL_WIDTH, w, EGL_HEIGHT, h, EGL_NONE)), s, w, h)
    }
    @JvmStatic fun eglCreatePbufferSurface(d: EGLDisplay, cfg: EGLConfig?, attrs: IntArray, off: Int): EGLSurface = MemoryStack.stackPush().use { st ->
        var w = 16; var h = 16; var i = off
        while (i < attrs.size && attrs[i] != EGL_NONE) { if (attrs[i] == EGL_WIDTH) w = attrs[i + 1]; if (attrs[i] == EGL_HEIGHT) h = attrs[i + 1]; i += 2 }
        EGLSurface(EGL10.eglCreatePbufferSurface(d.nativeHandle, cfg!!.nativeHandle, st.ints(EGL_WIDTH, w, EGL_HEIGHT, h, EGL_NONE)), null, w, h)
    }
    @JvmStatic fun eglMakeCurrent(d: EGLDisplay, draw: EGLSurface, read: EGLSurface, ctx: EGLContext): Boolean {
        val ok = EGL10.eglMakeCurrent(d.nativeHandle, draw.nativeHandle, read.nativeHandle, ctx.nativeHandle)
        if (ok && ctx.nativeHandle != 0L) GLES.createCapabilities()
        return ok
    }
    @JvmStatic fun eglSwapBuffers(d: EGLDisplay, s: EGLSurface): Boolean {
        val t = s.target ?: return EGL10.eglSwapBuffers(d.nativeHandle, s.nativeHandle)
        // The picture to the encoder: read back (bottom row first in GL), flipped, as ARGB.
        val buf = java.nio.ByteBuffer.allocateDirect(s.w * s.h * 4)
        org.lwjgl.opengles.GLES20.glReadPixels(0, 0, s.w, s.h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        val px = IntArray(s.w * s.h)
        for (y in 0 until s.h) {
            val row = (s.h - 1 - y) * s.w * 4
            for (x in 0 until s.w) {
                val i = row + x * 4
                px[y * s.w + x] = (0xFF shl 24) or ((buf.get(i).toInt() and 0xFF) shl 16) or ((buf.get(i + 1).toInt() and 0xFF) shl 8) or (buf.get(i + 2).toInt() and 0xFF)
            }
        }
        if (s.ptsNs >= 0) android.media.MediaCodec.setSurfaceTime(t, s.ptsNs / 1000)
        t.sink?.invoke(px, s.w, s.h, if (s.ptsNs >= 0) s.ptsNs else System.nanoTime())
        return true
    }
    @JvmStatic fun eglDestroySurface(d: EGLDisplay, s: EGLSurface): Boolean = if (s.nativeHandle != 0L) EGL10.eglDestroySurface(d.nativeHandle, s.nativeHandle) else true
    @JvmStatic fun eglDestroyContext(d: EGLDisplay, c: EGLContext): Boolean = if (c.nativeHandle != 0L) EGL10.eglDestroyContext(d.nativeHandle, c.nativeHandle) else true
    @JvmStatic fun eglReleaseThread(): Boolean = true
    @JvmStatic fun eglTerminate(d: EGLDisplay): Boolean = true
}

object EGLExt {
    const val EGL_RECORDABLE_ANDROID = 0x3142; const val EGL_OPENGL_ES3_BIT_KHR = 0x40
    /** When the next swapped picture is shown (nanoseconds); goes to the encoder with it. */
    @JvmStatic fun eglPresentationTimeANDROID(d: EGLDisplay, s: EGLSurface, ns: Long): Boolean { s.ptsNs = ns; return true }
}
