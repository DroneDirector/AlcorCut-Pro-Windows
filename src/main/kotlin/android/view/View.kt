package android.view

/** The app's window as the shared code sees it (haptics do nothing on a PC). Own code. */
open class View {
    @Volatile open var width = 0
    @Volatile open var height = 0
    open val display: Display? get() = Display()
    fun performHapticFeedback(constant: Int): Boolean = false
    fun performHapticFeedback(constant: Int, flags: Int): Boolean = false
}

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

abstract class ViewGroup : View() {
    open fun removeView(v: View) {}
}

/** A drawing surface (the live-stream preview on Windows shows the camera's pictures). */
open class SurfaceView(context: android.content.Context? = null) : View() {
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
object Surface { const val ROTATION_0 = 0; const val ROTATION_90 = 1; const val ROTATION_180 = 2; const val ROTATION_270 = 3 }
