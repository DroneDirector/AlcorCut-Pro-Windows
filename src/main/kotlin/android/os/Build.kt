package android.os

/** The shared code asks the phone's Android version; on Windows every modern path is taken. Own code. */
object Build {
    @JvmField val MANUFACTURER: String = "PC"
    @JvmField val MODEL: String = System.getProperty("os.name") ?: "Windows"
    @JvmField val BRAND: String = "PC"
    @JvmField val DEVICE: String = "pc"
    @JvmField val HARDWARE: String = System.getProperty("os.arch") ?: ""
    @JvmField val SOC_MODEL: String = System.getProperty("os.arch") ?: ""

    object VERSION {
        @JvmField val SDK_INT: Int = 35
        @JvmField val RELEASE: String = System.getProperty("os.version") ?: ""
    }

    object VERSION_CODES {
        const val N = 24; const val O = 26; const val P = 28; const val Q = 29; const val R = 30
        const val S = 31; const val TIRAMISU = 33; const val UPSIDE_DOWN_CAKE = 34; const val VANILLA_ICE_CREAM = 35
    }
}

/** Runs work on the UI thread or later. */
class Handler(private val looper: Looper? = null) {
    fun post(r: Runnable): Boolean { javax.swing.SwingUtilities.invokeLater(r); return true }
    fun postDelayed(r: Runnable, ms: Long): Boolean {
        val t = javax.swing.Timer(ms.toInt().coerceAtLeast(0)) { r.run() }
        t.isRepeats = false; t.start(); return true
    }
    fun removeCallbacksAndMessages(token: Any?) {}
}

class Looper private constructor() {
    companion object {
        private val main = Looper()
        fun getMainLooper(): Looper = main
        fun myLooper(): Looper? = if (javax.swing.SwingUtilities.isEventDispatchThread()) main else null
    }
}

object SystemClock {
    fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000
    fun uptimeMillis(): Long = System.nanoTime() / 1_000_000
    fun elapsedRealtimeNanos(): Long = System.nanoTime()
}
