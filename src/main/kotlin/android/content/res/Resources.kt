package android.content.res

/** Screen facts (the window's screen). */
class DisplayMetrics {
    @JvmField var widthPixels: Int = 1920
    @JvmField var heightPixels: Int = 1080
    @JvmField var density: Float = 1f
    @JvmField var densityDpi: Int = 160
    @JvmField var scaledDensity: Float = 1f
    @JvmField var xdpi: Float = 96f
    @JvmField var ydpi: Float = 96f
}

class Resources private constructor() {
    val displayMetrics: DisplayMetrics get() = DisplayMetrics().apply {
        try {
            val s = java.awt.Toolkit.getDefaultToolkit().screenSize
            val dpi = java.awt.Toolkit.getDefaultToolkit().screenResolution
            widthPixels = s.width; heightPixels = s.height
            density = dpi / 96f; scaledDensity = density; densityDpi = (160 * density).toInt(); xdpi = dpi.toFloat(); ydpi = dpi.toFloat()
        } catch (_: Throwable) {}
    }
    val configuration: androidx.compose.ui.platform.Configuration get() = androidx.compose.ui.platform.Configuration()
    companion object { val system = Resources() }
}
