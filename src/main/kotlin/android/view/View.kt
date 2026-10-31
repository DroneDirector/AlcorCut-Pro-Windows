package android.view

import androidx.compose.ui.graphics.toComposeImageBitmap

/**
 * A phone view as the shared code sees it. On Windows views are drawn by the Compose host
 * (AndroidView), which reads these properties; every change bumps [version] so it redraws. Own code.
 */
open class View(val context: android.content.Context? = null) {
    @Volatile open var width = 0
    @Volatile open var height = 0
    open val display: Display? get() = Display()
    fun performHapticFeedback(constant: Int): Boolean = false
    fun performHapticFeedback(constant: Int, flags: Int): Boolean = false

    /** Redraw counter for the Compose host. */
    val version = androidx.compose.runtime.mutableIntStateOf(0)
    protected fun changed() { version.intValue++ }
    var alpha = 1f; set(v) { field = v; changed() }
    var translationX = 0f; set(v) { field = v; changed() }
    var translationY = 0f; set(v) { field = v; changed() }
    var translationZ = 0f; set(v) { field = v; changed() }
    var scaleX = 1f; set(v) { field = v; changed() }
    var scaleY = 1f; set(v) { field = v; changed() }
    var rotation = 0f; set(v) { field = v; changed() }
    var pivotX = Float.NaN; set(v) { field = v; changed() }
    var pivotY = Float.NaN; set(v) { field = v; changed() }
    var visibility = VISIBLE; set(v) { field = v; changed() }
    var keepScreenOn = false
    var tag: Any? = null
    var layoutParams: ViewGroup.LayoutParams? = null
    var parent: ViewGroup? = null; internal set
    /** The shader effect drawn over this view's picture (android.graphics.RenderEffect). */
    var renderEffect: android.graphics.RenderEffect? = null; private set
    fun setRenderEffect(e: android.graphics.RenderEffect?) { renderEffect = e; changed() }
    fun invalidate() = changed()
    fun postInvalidateOnAnimation() = changed()
    fun requestLayout() {}
    fun post(r: Runnable): Boolean { javax.swing.SwingUtilities.invokeLater(r); return true }
    fun postDelayed(r: Runnable, ms: Long): Boolean { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(r, ms); return true }
    fun removeCallbacks(r: Runnable?): Boolean = true
    fun getLocationInWindow(out: IntArray) { out[0] = screenX; out[1] = screenY }
    fun getLocationOnScreen(out: IntArray) = getLocationInWindow(out)
    @Volatile internal var screenX = 0
    @Volatile internal var screenY = 0
    private val layoutListeners = ArrayList<OnLayoutChangeListener>()
    fun interface OnLayoutChangeListener { fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) }
    fun addOnLayoutChangeListener(l: OnLayoutChangeListener) { layoutListeners += l }
    fun removeOnLayoutChangeListener(l: OnLayoutChangeListener) { layoutListeners -= l }
    /** Called by the host when the size changes. */
    fun layoutTo(w: Int, h: Int) {
        if (w == width && h == height) return
        val ow = width; val oh = height
        width = w; height = h
        for (l in layoutListeners.toList()) l.onLayoutChange(this, 0, 0, w, h, 0, 0, ow, oh)
        changed()
    }
    companion object { const val VISIBLE = 0; const val INVISIBLE = 4; const val GONE = 8 }
}

object Gravity { const val CENTER = 17; const val TOP = 48; const val BOTTOM = 80; const val START = 8388611; const val END = 8388613; const val LEFT = 3; const val RIGHT = 5; const val CENTER_HORIZONTAL = 1; const val CENTER_VERTICAL = 16; const val FILL = 119 }

object HapticFeedbackConstants {
    const val LONG_PRESS = 0
    const val VIRTUAL_KEY = 1
    const val KEYBOARD_TAP = 3
    const val CLOCK_TICK = 4
    const val CONTEXT_CLICK = 6
    const val CONFIRM = 16
    const val REJECT = 17
    const val TEXT_HANDLE_MOVE = 9
}

/** Window flags (keeping the screen on is left to Windows' own power settings). */
class Window {
    fun addFlags(flags: Int) {}
    fun clearFlags(flags: Int) {}
}

class WindowManager {
    class LayoutParams {
        companion object {
            const val FLAG_KEEP_SCREEN_ON = 0x80
        }
    }
}

/** Size on screen, set by the Windows view host. */
open class ViewSize {
    @Volatile var width = 0
    @Volatile var height = 0
}

abstract class ViewGroup(context: android.content.Context? = null) : View(context) {
    open class LayoutParams(@JvmField var width: Int, @JvmField var height: Int) { companion object { const val MATCH_PARENT = -1; const val WRAP_CONTENT = -2 } }
    internal val children = java.util.concurrent.CopyOnWriteArrayList<View>()
    val childCount: Int get() = children.size
    fun getChildAt(i: Int): View = children[i]
    open fun addView(v: View) { v.parent = this; children += v; changed() }
    open fun addView(v: View, p: LayoutParams) { v.layoutParams = p; addView(v) }
    open fun removeView(v: View) { children -= v; changed() }
    fun removeAllViews() { children.clear(); changed() }
}

/** A drawing surface (the live-stream preview on Windows shows the camera's pictures). */
open class SurfaceView(context: android.content.Context? = null) : View(context) {
    val preview = androidx.camera.view.PreviewView(context)
    val holder = SurfaceHolder()
}
class SurfaceHolder {
    interface Callback {
        fun surfaceCreated(holder: SurfaceHolder)
        fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int)
        fun surfaceDestroyed(holder: SurfaceHolder)
    }
    fun addCallback(c: Callback) {}
    fun removeCallback(c: Callback) {}
}

/** The screen (always upright on a PC). */
class Display(val displayId: Int = 0) {
    val rotation: Int get() = 0
    companion object { const val DEFAULT_DISPLAY = 0 }
}
/**
 * A picture target. On Windows an encoder's input surface collects what is drawn to it ([frames]), and a
 * view's surface shows it. Own code.
 */
open class Surface(internal val sink: ((IntArray, Int, Int, Long) -> Unit)? = null) {
    @Volatile var valid = true
    fun isValid() = valid
    open fun release() { valid = false }
    fun lockCanvas(dirty: android.graphics.Rect?): android.graphics.Canvas = lockHardwareCanvas()
    private var canvasBmp: android.graphics.Bitmap? = null
    var defaultWidth = 1280; var defaultHeight = 720
    fun lockHardwareCanvas(): android.graphics.Canvas {
        val b = canvasBmp?.takeIf { it.width == defaultWidth && it.height == defaultHeight } ?: android.graphics.Bitmap.createBitmap(defaultWidth, defaultHeight, android.graphics.Bitmap.Config.ARGB_8888).also { canvasBmp = it }
        return android.graphics.Canvas(b)
    }
    fun unlockCanvasAndPost(c: android.graphics.Canvas) {
        val b = canvasBmp ?: return
        val px = IntArray(b.width * b.height); b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        sink?.invoke(px, b.width, b.height, System.nanoTime())
    }
    companion object { const val ROTATION_0 = 0; const val ROTATION_90 = 1; const val ROTATION_180 = 2; const val ROTATION_270 = 3 }
}


/**
 * A view showing a video player's pictures (and what an effect draws over them). The player hands it
 * each picture ([show]); [getBitmap] gives the current one back.
 */
open class TextureView(context: android.content.Context? = null) : View(context) {
    /** The latest picture (ARGB) and its size; read by the Compose host. */
    @Volatile var frame: androidx.compose.ui.graphics.ImageBitmap? = null; private set
    @Volatile var frameW = 0; private set
    @Volatile var frameH = 0; private set
    private var px: IntArray? = null
    private val transformM = android.graphics.Matrix()
    val isAvailable: Boolean get() = true
    var surfaceTextureListener: SurfaceTextureListener? = null
    interface SurfaceTextureListener {
        fun onSurfaceTextureAvailable(s: Any, w: Int, h: Int) {}
        fun onSurfaceTextureSizeChanged(s: Any, w: Int, h: Int) {}
        fun onSurfaceTextureDestroyed(s: Any): Boolean = true
        fun onSurfaceTextureUpdated(s: Any) {}
    }
    var isOpaque = true
    fun setTransform(m: android.graphics.Matrix?) { transformM.set(m ?: android.graphics.Matrix()); changed() }
    fun getTransform(m: android.graphics.Matrix?): android.graphics.Matrix = (m ?: android.graphics.Matrix()).also { it.set(transformM) }
    /** Shows a picture (any thread). */
    fun show(src: IntArray, w: Int, h: Int) {
        val img = org.jetbrains.skia.Image.makeRaster(org.jetbrains.skia.ImageInfo.makeN32(w, h, org.jetbrains.skia.ColorAlphaType.OPAQUE),
            java.nio.ByteBuffer.allocate(w * h * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).also { it.asIntBuffer().put(src, 0, w * h) }.array(), w * 4)
        synchronized(this) { if (px?.size != w * h) px = IntArray(w * h); System.arraycopy(src, 0, px!!, 0, w * h) }
        frame = img.toComposeImageBitmap(); frameW = w; frameH = h
        changed()
    }
    fun clear() { frame = null; changed() }
    fun getBitmap(): android.graphics.Bitmap? = synchronized(this) { px?.let { android.graphics.Bitmap.createBitmap(it, frameW, frameH, android.graphics.Bitmap.Config.ARGB_8888) } }
    fun getBitmap(w: Int, h: Int): android.graphics.Bitmap? = getBitmap()?.let { android.graphics.Bitmap.createScaledBitmap(it, w, h, true) }
    fun getBitmap(b: android.graphics.Bitmap): android.graphics.Bitmap? = getBitmap()
    internal val transformMatrix: android.graphics.Matrix get() = transformM
}