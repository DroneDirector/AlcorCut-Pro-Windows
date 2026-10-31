package com.dronedirector.veyrocutpro.windows

import org.lwjgl.egl.EGL
import org.lwjgl.egl.EGL10
import org.lwjgl.egl.EGL14
import org.lwjgl.opengles.GLES
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * The video editor's graphics engine: OpenGL ES on the PC's graphics card through ANGLE (Google's
 * OpenGL ES on DirectX, BSD licence, shipped with the app), so the phone's effect shaders run as they
 * are. Everything GL runs on one thread with one context ([run]). Own code.
 */
object GlEnv {
    private val thread = Executors.newSingleThreadExecutor { r -> Thread(r, "gl").apply { isDaemon = true; glThread = this } }
    @Volatile private var ready: Boolean? = null
    /** Why the graphics engine couldn't start (shown with the driver note), or null. */
    @Volatile var problem: String? = null; private set
    var display = 0L; private set
    var context = 0L; private set
    private var surface = 0L
    @Volatile private var glThread: Thread? = null

    /** Whether the engine runs on this PC (starts it on first use). */
    val ok: Boolean get() = ready ?: run<Boolean> { ready ?: init().also { ready = it } }

    /** Runs [block] on the GL thread (directly when already there) and returns its result. */
    fun <T> run(block: () -> T): T {
        if (Thread.currentThread() === glThread) return block()
        return try { thread.submit(Callable { block() }).get() } catch (e: java.util.concurrent.ExecutionException) { throw e.cause ?: e }
    }

    private fun libs() {
        val win = System.getProperty("os.name").lowercase().contains("win")
        if (win) {
            val dir = listOfNotNull(System.getProperty("compose.application.resources.dir")?.let { File(it, "angle") }, File("packaging/resources/windows/angle"))
                .firstOrNull { File(it, "libGLESv2.dll").exists() }
            if (dir != null) {
                Configuration.EGL_LIBRARY_NAME.set(File(dir, "libEGL.dll").absolutePath)
                Configuration.OPENGLES_LIBRARY_NAME.set(File(dir, "libGLESv2.dll").absolutePath)
            }
        } else {
            Configuration.EGL_LIBRARY_NAME.set("libEGL.so.1")
            Configuration.OPENGLES_LIBRARY_NAME.set("libGLESv2.so.2")
        }
    }

    private fun init(): Boolean = try {
        libs()
        MemoryStack.stackPush().use { st ->
            display = EGL14.eglGetDisplay(0L)
            if (display == 0L) throw IllegalStateException("No graphics display")
            val maj = st.mallocInt(1); val min = st.mallocInt(1)
            if (!EGL14.eglInitialize(display, maj, min)) throw IllegalStateException("Graphics engine didn't start (EGL 0x%04X)".format(EGL14.eglGetError()))
            EGL.createDisplayCapabilities(display, maj[0], min[0])
            EGL14.eglBindAPI(EGL14.EGL_OPENGL_ES_API)
            val attrs = st.ints(
                EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT, 0x3040, 0x0004 /* ES2 */, EGL10.EGL_NONE)
            val cfgs = st.mallocPointer(1); val n = st.mallocInt(1)
            if (!EGL10.eglChooseConfig(display, attrs, cfgs, n) || n[0] == 0) throw IllegalStateException("No suitable graphics mode")
            val cfg = cfgs[0]
            context = EGL10.eglCreateContext(display, cfg, EGL10.EGL_NO_CONTEXT, st.ints(0x3098 /* CLIENT_VERSION */, 2, EGL10.EGL_NONE))
            if (context == 0L) throw IllegalStateException("Graphics context failed (EGL 0x%04X)".format(EGL10.eglGetError()))
            surface = EGL10.eglCreatePbufferSurface(display, cfg, st.ints(EGL10.EGL_WIDTH, 16, EGL10.EGL_HEIGHT, 16, EGL10.EGL_NONE))
            if (!EGL10.eglMakeCurrent(display, surface, surface, context)) throw IllegalStateException("Graphics context failed (EGL 0x%04X)".format(EGL10.eglGetError()))
            GLES.createCapabilities()
        }
        true
    } catch (t: Throwable) {
        problem = t.message ?: t.toString()
        com.dronedirector.veyrocutpro.ui.AppLog.w("Graphics engine: $problem")
        false
    }

    /** Starts the engine on its thread (call before the first use). */
    fun start(): Boolean = run { ok }
}
