package android.hardware.display

/** Screens: on a PC the window doesn't rotate, so nothing changes. */
class DisplayManager {
    interface DisplayListener {
        fun onDisplayAdded(displayId: Int)
        fun onDisplayRemoved(displayId: Int)
        fun onDisplayChanged(displayId: Int)
    }
    fun registerDisplayListener(l: DisplayListener, h: android.os.Handler?) {}
    fun unregisterDisplayListener(l: DisplayListener) {}
    fun getDisplay(id: Int): android.view.Display = android.view.Display(id)
    val displays: Array<android.view.Display> get() = arrayOf(android.view.Display(0))
    fun getDisplays(category: String?): Array<android.view.Display> = displays
    companion object { const val DISPLAY_CATEGORY_PRESENTATION = "android.hardware.display.category.PRESENTATION" }
}
