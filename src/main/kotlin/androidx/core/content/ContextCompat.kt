package androidx.core.content

import android.content.Context

/** On Windows the app may read your files: every permission is granted. */
object ContextCompat {
    fun checkSelfPermission(c: Context, perm: String): Int = 0
    fun getMainExecutor(c: Context): java.util.concurrent.Executor = java.util.concurrent.Executor { javax.swing.SwingUtilities.invokeLater(it) }
}
