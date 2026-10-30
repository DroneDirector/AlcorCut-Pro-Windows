package android.view

/** The app's window as the shared code sees it (haptics do nothing on a PC). Own code. */
open class View {
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
