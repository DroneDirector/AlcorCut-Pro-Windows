package android.widget

import androidx.compose.runtime.mutableStateListOf

/** Short notices at the bottom of the window, like on the phone. */
class Toast private constructor(val text: String, val long: Boolean) {
    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1
        /** Notices showing now (the window draws them). */
        val shown = mutableStateListOf<Toast>()
        fun makeText(context: android.content.Context?, text: CharSequence, duration: Int) = Toast(text.toString(), duration == LENGTH_LONG)
        fun makeText(context: android.content.Context?, res: Int, duration: Int) = Toast("", duration == LENGTH_LONG)
    }

    fun show() {
        javax.swing.SwingUtilities.invokeLater {
            shown += this
            val t = javax.swing.Timer(if (long) 3500 else 2000) { shown -= this }
            t.isRepeats = false; t.start()
        }
    }
    fun cancel() { javax.swing.SwingUtilities.invokeLater { shown -= this } }
}
